package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.toolWindow.treePanel.ClassDependencyInfo
import com.github.zjh7890.gpttools.toolWindow.treePanel.MavenDependencyId
import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.PsiUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.serialization.Serializable

/**
 *  ----------------------------
 *  文件理解（结合 whole/partial 与 selected 的关系）:
 *  ----------------------------
 *
 *  1. whole/partial 的含义（新版，无隐式节点）：
 *     - 当某个文件（ProjectFile）或类（ProjectClass）设置为 whole=true，表示“我们需要使用该文件/类的全部内容”。
 *       但**与原设计不同**的是：我们依旧会在数据结构中“显式”地列出它的全部子节点（例如文件下的所有类、类中的所有方法），
 *       不再将其视为“隐式存在”而不存储。这样可以避免后续操作时出现“先降级为 partial 再重新扫描子节点”的繁琐流程。
 *     - 当标记为 whole=false，则意味着“只需要其中的一部分”，这时我们会在 children 列表里（比如 ProjectFile.classes、
 *       ProjectClass.methods）列举出所需的具体子节点。若需要更多子节点，可以随时调用相关方法添加；若要移除部分子节点，也可以直接删除。
 *
 *     - 总之，“whole=true” 现在只是一个标签，表示“当前对象及其所有子节点都被完整使用”。**但不再依赖“清空子节点”来表示整对象引用**，
 *       数据结构中依旧能看到它包含的所有类或方法。
 *
 *  2. 移除逻辑：
 *     - **删除整个对象（文件 / 类）**：若我们不再需要这个文件或类，可直接从上级列表中移除它。例如在 PackageDependency.files 里 `remove` 掉
 *       对应的 ProjectFile，或在 ProjectFile.classes 中 `remove` 掉对应的 ProjectClass。
 *     - **删除部分子节点（类中的部分方法等）**：不论父级是 whole=true 还是 whole=false，都可以直接操作其子节点列表进行删除；无需再做“降级”。
 *       因为即使是 whole=true，我们也已显式存了所有子节点，对它们的增删改查都可以直接进行。
 *
 *  3. selected 与 whole/partial 的关系：
 *     - selected 表示在后续某些操作（例如 UI 勾选、批量处理）时是否被选中，与 “是否纳入(whole/partial)” 无直接影响。
 *       两者是不同维度：数据结构中是否存在节点，取决于是否 addFile/addClass/addMethod；而节点是否选中，取决于 UI 或逻辑对 selected 的设置。
 *     - 即使文件或类是 whole=true，因为我们采用“无隐式节点”的方式，子节点也都在数据结构中显式存在，也可独立设置 selected = true / false。
 *       具体是否这样使用，视你业务需求而定。
 *
 *  4. 关于“整对象 / 部分对象”的转换：
 *     - 在旧设计中，如果某个文件/类是 whole=true 而要部分移除某个方法，需要先“降级为 partial”，再重新扫描子节点。现在则无需如此。
 *     - 如果业务上还需要“切换 whole ↔ partial”的标记，也可以做，但这只是一种标签状态切换；子节点依旧存储在数据结构中，并不受影响。
 */
data class AppFileTree(
    val projectFileTrees: MutableList<ProjectFileTree> = mutableListOf()
) {
    fun toSerializable(): SerializableAppFileTree {
        return SerializableAppFileTree(
            projectTrees = projectFileTrees.map { it.toSerializable() }
        )
    }


    /**
     * 将指定文件加入到 AppFileTree
     * - 若是本地文件，则按照 module -> package -> file 进行挂载
     * - 若是外部依赖 (.m2/repository/... )，则解析 groupId:artifactId:version 并挂载到 mavenDependencies
     * - 仅完成结构挂载，不做额外解析（如类、方法）
     */
    fun addFile(file: VirtualFile, project: Project, whole: Boolean = false) {
        // 1. 找到或创建当前 Project 对应的 projectFileTree
        val pft = findOrCreateProjectFileTree(this, project)

        // 2. 判断本地 or 外部依赖
        val projectPath = project.basePath ?: ""
        val isExternal = !file.path.startsWith(projectPath)

        // 3. 找到对应的 packageDependency 和创建 ProjectFile
        val (packageDep, projectFile) = if (!isExternal) {
            // ------- 本地文件 -------
            // (1) 获取 moduleDependency
            val moduleName = getModuleName(file, project)
            val moduleDep = findOrCreateModule(pft.modules, moduleName)

            // (2) 获取 packageDependency
            val psiFile = PsiManager.getInstance(project).findFile(file)
            val packageName = getPackageName(psiFile)
            val packageDep = findOrCreatePackage(moduleDep.packages, packageName)

            // (3) 找 / 创建 ProjectFile
            val projFile = findOrCreateProjectFile(packageDep.files, file, project, isMaven = false, whole = whole)
            packageDep to projFile
        } else {
            // ------- 外部依赖文件 -------
            // (1) 解析 maven groupId/artifactId/version
            val mavenInfo = extractMavenInfo(file.path) ?: return
            val mavenDep = findOrCreateMavenDependency(
                pft.mavenDependencies,
                mavenInfo.groupId,
                mavenInfo.artifactId,
                mavenInfo.version
            )

            // (2) 获取 packageDependency
            val psiFile = PsiManager.getInstance(project).findFile(file)
            val packageName = getPackageName(psiFile)
            val packageDep = findOrCreatePackage(mavenDep.packages, packageName)

            // (3) 找 / 创建 ProjectFile
            val projFile = findOrCreateProjectFile(packageDep.files, file, project, isMaven = true, whole = whole)
            packageDep to projFile
        }

        // 4. 如果是 whole=true，显式添加文件中的所有类
        if (whole) {
            val psiFile = PsiManager.getInstance(project).findFile(file)
            if (psiFile != null) {
                val allPsiClasses = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
                for (cls in allPsiClasses) {
                    // 递归添加类及其方法，同样标记为 whole=true
                    addClass(cls, project, whole = true)
                }
            }
        }
    }

    fun addClass(psiClass: PsiClass, project: Project, whole: Boolean = true) {
        // 1. 先保证所在文件已加入
        val containingFile = psiClass.containingFile?.virtualFile ?: return
        // 这里若直接传入 whole=true 可能会形成再次递归，你可根据需要来决定
        // 如果文件层面也想显式列出所有类，则可将 addFile 第二个参数也设为 true
        addFile(containingFile, project, whole = false)

        // 2. 找到或创建 projectFileTree
        val pft = findOrCreateProjectFileTree(this, project)

        val projectPath = project.basePath ?: ""
        val isExternal = !containingFile.path.startsWith(projectPath)

        // 3. 找到对应的 ProjectFile
        val (packageDep, projectFile) = if (!isExternal) {
            // 本地文件
            val moduleName = getModuleName(containingFile, project)
            val moduleDep = findOrCreateModule(pft.modules, moduleName)
            val psiFile = PsiManager.getInstance(project).findFile(containingFile)
            val packageName = getPackageName(psiFile)
            val pkgDep = findOrCreatePackage(moduleDep.packages, packageName)
            val projFile = findOrCreateProjectFile(pkgDep.files, containingFile, project, isMaven = false)
            pkgDep to projFile
        } else {
            // 外部依赖
            val mavenInfo = extractMavenInfo(containingFile.path) ?: return
            val mavenDep = findOrCreateMavenDependency(
                pft.mavenDependencies,
                mavenInfo.groupId,
                mavenInfo.artifactId,
                mavenInfo.version
            )
            val psiFile = PsiManager.getInstance(project).findFile(containingFile)
            val packageName = getPackageName(psiFile)
            val pkgDep = findOrCreatePackage(mavenDep.packages, packageName)
            val projFile = findOrCreateProjectFile(pkgDep.files, containingFile, project, isMaven = true)
            pkgDep to projFile
        }

        // 4. 找/建 对应的 ProjectClass，并设置 whole = 传入值
        val className = psiClass.name ?: return
        val existingClass = projectFile.classes.find { it.className == className }
        val projectClass = if (existingClass == null) {
            // 新建
            val newCls = ProjectClass(
                className = className,
                psiClass = psiClass,
                methods = mutableListOf(),
                whole = whole
            )
            projectFile.classes.add(newCls)
            newCls
        } else {
            // 如果已存在，可根据需求更新它的 whole
            existingClass.whole = whole
            existingClass
        }

        // 如果我们希望“整类”也显式地列出它所有方法
        if (whole) {
            // 此时就递归把所有方法都加进来
            for (m in psiClass.methods) {
                addMethod(m, project)  // addMethod 原本没有 whole 概念，方法本身也无 whole
            }
        }
    }

    /**
     * 将指定 PsiMethod 加入到 AppFileTree。
     * - 首先调用 addFile 把所在文件挂载到结构里
     * - 然后在对应的 ProjectFile 下找 / 建对应的 ProjectClass
     * - 将该 psiMethod 转为 ProjectMethod 并加入
     */
    fun addMethod(psiMethod: PsiMethod, project: Project) {
        // 1. 先保证所在文件已加入
        val containingFile = psiMethod.containingFile?.virtualFile ?: return
        addFile(containingFile, project, false)

        // 2. 找到或创建 projectFileTree
        val pft = findOrCreateProjectFileTree(this, project)

        // 3. 判断本地 or 外部
        val projectPath = project.basePath ?: ""
        val isExternal = !containingFile.path.startsWith(projectPath)

        // 4. 找到对应的 [PackageDependency] -> [ProjectFile]
        val (packageDep, projectFile) = if (!isExternal) {
            val moduleName = getModuleName(containingFile, project)
            val moduleDep = findOrCreateModule(pft.modules, moduleName)

            val psiFile = PsiManager.getInstance(project).findFile(containingFile)
            val packageName = getPackageName(psiFile)
            val pkgDep = findOrCreatePackage(moduleDep.packages, packageName)
            val projFile = findOrCreateProjectFile(pkgDep.files, containingFile, project, false)
            pkgDep to projFile
        } else {
            val mavenInfo = extractMavenInfo(containingFile.path) ?: return
            val mavenDep = findOrCreateMavenDependency(
                pft.mavenDependencies,
                mavenInfo.groupId,
                mavenInfo.artifactId,
                mavenInfo.version
            )
            val psiFile = PsiManager.getInstance(project).findFile(containingFile)
            val packageName = getPackageName(psiFile)
            val pkgDep = findOrCreatePackage(mavenDep.packages, packageName)
            val projFile = findOrCreateProjectFile(pkgDep.files, containingFile, project, true)
            pkgDep to projFile
        }

        // 5. 找 / 建 ProjectClass
        val psiClass = psiMethod.containingClass ?: return
        val className = psiClass.name ?: return

        // 看当前 file 里是否已有这个类
        val existingClass = projectFile.classes.find { it.className == className }
        val projectClass = if (existingClass != null) {
            existingClass
        } else {
            // 新建
            val newCls = ProjectClass(
                className = className,
                psiClass = psiClass,
                methods = mutableListOf(),
                whole = false
            )
            projectFile.classes.add(newCls)
            newCls
        }

        // 6. 将该 psiMethod 加到 projectClass.methods
        val paramTypes = psiMethod.parameterList.parameters.map { it.type.canonicalText }
        val existMethod = projectClass.methods.find {
            it.methodName == psiMethod.name && it.parameterTypes == paramTypes
        }
        if (existMethod == null) {
            projectClass.methods.add(
                ProjectMethod(
                    methodName = psiMethod.name,
                    parameterTypes = paramTypes,
                    psiMethod = psiMethod
                )
            )
        }
    }

    fun removeFile(file: VirtualFile, project: Project) {
        // 找到所在的 ProjectFileTree
        val pft = findOrCreateProjectFileTree(this, project)

        // 1. 本地 or 外部
        val projectPath = project.basePath ?: ""
        val isExternal = !file.path.startsWith(projectPath)

        if (!isExternal) {
            // 去本地 modules 里寻找
            val moduleName = getModuleName(file, project)
            val moduleDep = pft.modules.find { it.moduleName == moduleName } ?: return

            // 尝试在所有 packageDependency 里移除对应的 ProjectFile
            moduleDep.packages.forEach { pkg ->
                pkg.files.removeAll { it.virtualFile == file }
            }
            // 也可以根据需要，如果 pkg.files 为空时，pkg 也一并移除
            moduleDep.packages.removeAll { it.files.isEmpty() }
            // 如果 moduleDep.packages 为空，则也可以选择把整个 moduleDep 移除
            pft.modules.removeAll { it.packages.isEmpty() }
        } else {
            // 去 mavenDependencies 里找
            val mavenInfo = extractMavenInfo(file.path) ?: return
            val mavenDep = pft.mavenDependencies.find {
                it.groupId == mavenInfo.groupId &&
                        it.artifactId == mavenInfo.artifactId &&
                        it.version == mavenInfo.version
            } ?: return

            mavenDep.packages.forEach { pkg ->
                pkg.files.removeAll { it.virtualFile == file }
            }
            mavenDep.packages.removeAll { it.files.isEmpty() }
            pft.mavenDependencies.removeAll { it.packages.isEmpty() }
        }
    }

    fun removeClass(projectClass: ProjectClass, project: Project) {
        val containingFile = projectClass.psiClass.containingFile?.virtualFile ?: return
        val pft = findOrCreateProjectFileTree(this, project)

        val projectPath = project.basePath ?: ""
        val isExternal = !containingFile.path.startsWith(projectPath)

        // 找到对应的 ProjectFile
        val targetFile = if (!isExternal) {
            // 本地文件
            val moduleName = getModuleName(containingFile, project)
            val moduleDep = pft.modules.find { it.moduleName == moduleName } ?: return
            moduleDep.packages
                .flatMap { it.files }
                .find { it.virtualFile == containingFile }
        } else {
            // Maven 外部依赖
            val mavenInfo = extractMavenInfo(containingFile.path) ?: return
            val mavenDep = pft.mavenDependencies.find {
                it.groupId == mavenInfo.groupId &&
                        it.artifactId == mavenInfo.artifactId &&
                        it.version == mavenInfo.version
            } ?: return
            mavenDep.packages
                .flatMap { it.files }
                .find { it.virtualFile == containingFile }
        } ?: return

        // 如果文件是 whole，需要先降级为 partial，这样才能在其 classes 列表里找到并移除目标类
        if (targetFile.whole) {
            targetFile.whole = false
            targetFile.classes.clear()

            // 把当前文件里的所有类都加载进来，但排除要移除的这个类
            val psiFile = targetFile.psiFile ?: return
            val allPsiClasses = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile, com.intellij.psi.PsiClass::class.java)

            // 用 qualifiedName 来排除目标类，避免重名冲突
            val removeQName = projectClass.psiClass.qualifiedName
            allPsiClasses.forEach { c ->
                val cQName = c.qualifiedName
                if (cQName == null || cQName != removeQName) {
                    // 保留的类默认标记为 whole=true（整个类使用）
                    targetFile.classes.add(
                        ProjectClass(
                            className = c.name ?: "",
                            psiClass = c,
                            methods = mutableListOf(),
                            whole = true
                        )
                    )
                }
            }
        } else {
            // 如果文件本来就是 partial，则直接调用 removeClasses 即可
            targetFile.removeClasses(listOf(projectClass))
        }
    }


    fun removeMethod(projectMethod: ProjectMethod, project: Project) {
        val containingFile = projectMethod.psiMethod.containingFile?.virtualFile ?: return
        val pft = AppFileTree.findOrCreateProjectFileTree(this, project)

        // 判断本地 / 外部
        val projectPath = project.basePath ?: ""
        val isExternal = !containingFile.path.startsWith(projectPath)

        // 找到对应的 ProjectFile
        val targetFile: ProjectFile? = if (!isExternal) {
            val moduleName = getModuleName(containingFile, project)
            val moduleDep = pft.modules.find { it.moduleName == moduleName } ?: return
            moduleDep.packages
                .flatMap { it.files }
                .find { it.virtualFile == containingFile }
        } else {
            val mavenInfo = extractMavenInfo(containingFile.path) ?: return
            val mavenDep = pft.mavenDependencies.find {
                it.groupId == mavenInfo.groupId &&
                        it.artifactId == mavenInfo.artifactId &&
                        it.version == mavenInfo.version
            } ?: return
            mavenDep.packages
                .flatMap { it.files }
                .find { it.virtualFile == containingFile }
        }

        if (targetFile == null) return

        // **如果 targetFile 是 whole，需要先降级为 partial**，
        // 这样才能在 targetFile.classes 中找到对应的类。
        if (targetFile.whole) {
            targetFile.whole = false
            targetFile.classes.clear()

            // 把这个文件里的所有 PsiClass 加载进来
            val psiFile = targetFile.psiFile
            if (psiFile != null) {
                val allPsiClasses = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile, com.intellij.psi.PsiClass::class.java)
                allPsiClasses.forEach { psiClass ->
                    // 这里可以选择让每个类默认 whole=true（表示整类引用）
                    // 或直接收集全部方法 whole=false（看你的需求）
                    targetFile.classes.add(
                        ProjectClass(
                            className = psiClass.name ?: "",
                            psiClass = psiClass,
                            methods = mutableListOf(),
                            whole = true
                        )
                    )
                }
            }
        }

        // 现在 targetFile.classes 里有东西了，可以找到目标 class
        val realClass = targetFile.classes.find {
            it.psiClass == projectMethod.psiMethod.containingClass
        }
        // 如果找不到，就可能是因为 class 也是 whole=true，需要再去做降级
        // 或者我们也可以直接找 qualifiedName 去匹配
        if (realClass == null) {
            // 可以考虑查 qualifiedName 再找一下
            // 或者就直接 return
            return
        }

        // **如果 realClass 也是 whole=true，需要再降级**，
        // 然后移除目标方法
        if (realClass.whole) {
            realClass.whole = false
            realClass.methods.clear()

            val allPsiMethods = realClass.psiClass.methods
            val methodSignToRemove = projectMethod.methodName to projectMethod.parameterTypes

            allPsiMethods.forEach { m ->
                val signature = m.name to m.parameterList.parameters.map { it.type.canonicalText }
                // 如果不是要移除的方法，就保留
                if (signature != methodSignToRemove) {
                    realClass.methods.add(
                        ProjectMethod(
                            methodName = m.name,
                            parameterTypes = m.parameterList.parameters.map { it.type.canonicalText },
                            psiMethod = m
                        )
                    )
                }
            }
        } else {
            // 如果 realClass 不是 whole，直接 remove 就行
            realClass.removeMethods(listOf(projectMethod))
        }
    }


    /**
     * 轻量获取 moduleName：从 file 路径里截取项目路径后第一级目录名。
     * 如果需要更精细的逻辑，可自定义或复用 DependencyUtils 的类似实现。
     */
    private fun getModuleName(file: VirtualFile, project: Project): String {
        val basePath = project.basePath ?: return "UnknownModule"
        val relPath = file.path.removePrefix(basePath).removePrefix("/")
        return relPath.split("/").firstOrNull() ?: "UnknownModule"
    }

    /**
     * 猜测包名。若是 Java 文件则可直接用其 packageName，否则返回 (default package)。
     * 可视需要抽用 DependencyUtils 的其他逻辑。
     */
    private fun getPackageName(psiFile: PsiFile?): String {
        return if (psiFile is PsiJavaFile) {
            psiFile.packageName.ifBlank { "(default package)" }
        } else {
            "(default package)"
        }
    }

    companion object {

        /**
         * 将 classDependencyGraph 转换为 AppFileTree
         */
        fun buildAppFileTreeFromClassGraph(
            classDependencyGraph: Map<PsiClass, ClassDependencyInfo>
        ): AppFileTree {
            val projectFileTreeMap = mutableMapOf<Project, ProjectFileTree>()

            for ((psiClass, dependencyInfo) in classDependencyGraph) {
                val vFile = psiClass.containingFile?.virtualFile ?: continue
                val project = psiClass.project

                val projectFileTree = projectFileTreeMap.getOrPut(project) {
                    ProjectFileTree(
                        project = project,
                        modules = mutableListOf(),
                        mavenDependencies = mutableListOf()
                    )
                }

                // 判断是否是外部依赖
                val projectPath = project.basePath ?: ""
                val isExternal = !vFile.path.startsWith(projectPath)

                if (!isExternal) {
                    val moduleName = getModuleName(project, psiClass)
                    val moduleDependency = findOrCreateModule(projectFileTree.modules, moduleName)

                    val packageName = psiClass.qualifiedName
                        ?.substringBeforeLast('.')
                        ?: "(default package)"
                    val packageDependency = findOrCreatePackage(moduleDependency.packages, packageName)

                    val projectFile = findOrCreateProjectFile(packageDependency.files, vFile, project)

                    // 将当前 psiClass + usedMethods 放进 projectFile.classes
                    fillProjectClassAndMethods(projectFile, psiClass, dependencyInfo)

                } else {
                    val mavenInfo = extractMavenInfo(vFile.path)
                    if (mavenInfo != null) {
                        val mavenDependency = findOrCreateMavenDependency(
                            projectFileTree.mavenDependencies,
                            mavenInfo.groupId,
                            mavenInfo.artifactId,
                            mavenInfo.version
                        )
                        val packageName = psiClass.qualifiedName
                            ?.substringBeforeLast('.')
                            ?: "(default package)"
                        val packageDependency = findOrCreatePackage(mavenDependency.packages, packageName)
                        val projectFile = findOrCreateProjectFile(packageDependency.files, vFile, project, isMaven = true)

                        fillProjectClassAndMethods(projectFile, psiClass, dependencyInfo)
                    }
                }
            }

            return AppFileTree(
                projectFileTrees = projectFileTreeMap.values.toMutableList()
            )
        }

        /**
         * 根据 DependenciesTreePanel 原先的逻辑，把外部依赖 jar 路径解析出 groupId, artifactId, version
         */
        fun extractMavenInfo(path: String): MavenDependencyId? {
            val regex = ".*/repository/(.+)/([^/]+)/([^/]+)/([^/]+)\\.jar!/.*".toRegex()
            val matchResult = regex.find(path) ?: return null
            return try {
                val (groupIdPath, artifactId, version, _) = matchResult.destructured
                MavenDependencyId(
                    groupId = groupIdPath.replace('/', '.'),
                    artifactId = artifactId,
                    version = version
                )
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 根据文件相对路径(形如 /moduleA/src/...) 前缀来判断 module
         */
        fun getModuleName(project: Project, cls: PsiClass): String {
            val vFile = cls.containingFile?.virtualFile ?: return "UnknownModule"
            val basePath = project.basePath ?: return "UnknownModule"
            val relativePath = vFile.path.removePrefix(basePath).removePrefix("/")
            return relativePath.split("/").firstOrNull() ?: "UnknownModule"
        }

        /**
         * 在 projectFileTrees 中找出对应的 ProjectFileTree；若无则新建。
         */
        fun findOrCreateProjectFileTree(appFileTree: AppFileTree, project: Project): ProjectFileTree {
            val existing = appFileTree.projectFileTrees.find { it.project == project }
            if (existing != null) return existing

            val newPft = ProjectFileTree(
                project = project,
                modules = mutableListOf(),
                mavenDependencies = mutableListOf()
            )
            appFileTree.projectFileTrees.add(newPft)
            return newPft
        }

        fun findOrCreateModule(modules: MutableList<ModuleDependency>, moduleName: String): ModuleDependency {
            return modules.find { it.moduleName == moduleName } ?: ModuleDependency(moduleName).also {
                modules.add(it)
            }
        }

        fun findOrCreateMavenDependency(
            mavenDeps: MutableList<MavenDependency>,
            groupId: String,
            artifactId: String,
            version: String
        ): MavenDependency {
            return mavenDeps.find {
                it.groupId == groupId && it.artifactId == artifactId && it.version == version
            } ?: MavenDependency(
                groupId = groupId,
                artifactId = artifactId,
                version = version
            ).also {
                mavenDeps.add(it)
            }
        }

        fun findOrCreatePackage(
            packages: MutableList<PackageDependency>,
            packageName: String
        ): PackageDependency {
            return packages.find { it.packageName == packageName } ?: PackageDependency(packageName).also {
                packages.add(it)
            }
        }

        fun findOrCreateProjectFile(
            files: MutableList<ProjectFile>,
            vFile: VirtualFile,
            project: Project,
            isMaven: Boolean = false,
            whole: Boolean = false
        ): ProjectFile {
            // 计算 filePath
            val basePath = project.basePath?.removeSuffix("/") ?: ""
            val absolutePath = vFile.path
            val relativePath = if (!isMaven) {
                // 本地项目文件
                absolutePath.removePrefix(basePath).removePrefix("/")
            } else {
                // 如果是 Maven 文件，可能要直接存绝对路径
                // 或者你也可以存 jarPath + className
                absolutePath
            }

            return files.find { it.filePath == relativePath } ?: run {
                val psiFile = PsiManager.getInstance(project).findFile(vFile)
                val newFile = ProjectFile(
                    filePath = relativePath,
                    virtualFile = vFile,
                    psiFile = psiFile,
                    classes = mutableListOf(),
                    whole = whole
                )
                files.add(newFile)
                newFile
            }
        }

        /**
         * 根据 dependencyInfo 里的 usedMethods，往一个 ProjectFile 里添加对应的 ProjectClass/ProjectMethod
         * 并判断是否为 whole
         */
        fun fillProjectClassAndMethods(
            projectFile: ProjectFile,
            psiClass: PsiClass,
            dependencyInfo: ClassDependencyInfo
        ) {
            val allMethods = psiClass.methods.filterNot { it.isConstructor }
            val usedMethods = dependencyInfo.usedMethods.filterNot { it.isConstructor }

            val projectMethods = usedMethods.map { usedMethod ->
                ProjectMethod(
                    methodName = usedMethod.name,
                    parameterTypes = usedMethod.parameterList.parameters.map { p -> p.type.canonicalText },
                    psiMethod = usedMethod
                )
            }.toMutableList()

            val isWholeClass = allMethods.isNotEmpty() && allMethods.size == usedMethods.size

            val projectClass = ProjectClass(
                className = psiClass.name ?: "Unnamed",
                psiClass = psiClass,
                methods = projectMethods,
                whole = isWholeClass
            )
            projectFile.classes.add(projectClass)
        }

        /**
         * 将原先 generateDependenciesText 和 collectFileContents 的逻辑合并到一个函数中
         */
        fun generateDependenciesTextCombined(
            appFileTree: AppFileTree
        ): String {
            // 针对每个 ProjectFileTree 生成文本，再用 joinToString 拼装
            return appFileTree.projectFileTrees.joinToString("\n\n") { projectFileTree ->
                // 1) 整理 “本地 module” 的文件
                val moduleFilesContents = projectFileTree.modules
                    .flatMap { it.packages }
                    .flatMap { it.files }
                    .mapNotNull { projectFile ->
                        handleSingleProjectFile(projectFile, projectFileTree.project)
                    }

                // 2) 整理 “mavenDependencies” 中的文件
                val mavenFilesContents = projectFileTree.mavenDependencies
                    .flatMap { it.packages }
                    .flatMap { it.files }
                    .mapNotNull { projectFile ->
                        handleSingleProjectFile(projectFile, projectFileTree.project)
                    }

                // 合并两个来源的内容
                val joinedFileContents = (moduleFilesContents + mavenFilesContents).joinToString("\n\n")
                joinedFileContents
            }
        }

        /**
         * 单独提取一个函数，处理单个 ProjectFile 的逻辑
         * 返回要拼接的字符串，若不需要则返回 null
         */
        private fun handleSingleProjectFile(
            projectFile: ProjectFile,
            project: Project
        ): String? {
            val virtualFile = projectFile.virtualFile
            val absolutePath = virtualFile.path

            val relativePath = calculateRelativePath(absolutePath, project, projectFile.ifMavenFile)
            // 如果用户配置了 whole=true 则读取整文件内容
            return if (projectFile.whole) {
                return "${relativePath}\n" + FileUtil.readFileInfoForLLM(virtualFile)
            } else {
                // 收集需要处理的 PsiElement
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
                val elementsToProcess = mutableListOf<PsiElement>()

                // 遍历用户指定的 Class/Method 信息
                projectFile.classes.forEach { projectClass ->
                    val psiClass = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java).find {
                        if (projectClass.whole) {
                            // 如果需要整类，则用 qualifiedName 精确匹配
                            it.qualifiedName == projectClass.className
                        } else {
                            // 否则只匹配类名
                            it.name == projectClass.className
                        }
                    } ?: return@forEach

                    if (projectClass.whole) {
                        // 整类内容
                        elementsToProcess.add(psiClass)
                    } else {
                        // 若只需要其中某些方法
                        projectClass.methods.forEach { projectMethod ->
                            val matchedMethod = psiClass
                                .findMethodsByName(projectMethod.methodName, false)
                                .find { method ->
                                    val paramTypes = method.parameterList.parameters.map { it.type.canonicalText }
                                    paramTypes == projectMethod.parameterTypes
                                } ?: return@forEach

                            elementsToProcess.add(matchedMethod)
                        }
                    }
                }

                // 将收集到的 PsiElement 调用 depsInSingleFile 生成内容
                if (elementsToProcess.isNotEmpty()) {
                    val singleFileContent = ElementsDepsInSingleFileAction.depsInSingleFile(elementsToProcess, project)
                    if (!singleFileContent.isNullOrBlank()) {
                        "${relativePath}\n" + FileUtil.wrapBorder(singleFileContent.trim())
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }

        /**
         * 计算文件的相对路径
         * @param absolutePath 文件的绝对路径
         * @param project 项目对象
         * @param isMaven 是否是 Maven 依赖文件
         * @return 相对路径（本地文件包含项目名称，jar包不包含）
         */
        private fun calculateRelativePath(
            absolutePath: String,
            project: Project,
            isMaven: Boolean
        ): String {
            val basePath = project.basePath?.removeSuffix("/")
            return if (isMaven) {
                // Maven 依赖文件: 提取 jar 包名称及其后续路径
                val regex = ".*/([^/]+\\.jar!/.*)".toRegex()
                val matchResult = regex.find(absolutePath)
                if (matchResult != null) {
                    "/${matchResult.groupValues[1]}"
                } else {
                    absolutePath
                }
            } else {
                // 本地项目文件: 添加项目名称前缀
                val path = absolutePath.removePrefix(basePath ?: "").removePrefix("/")
                "${project.name}/$path"
            }
        }
    }
}

@Serializable
data class SerializableAppFileTree(
    val projectTrees: List<SerializableProjectFileTree> = emptyList()
) {
    /**
     * 反序列化 -> 立即遍历所有文件/类/方法，找出对应 PSI 实例
     * 使得后续使用时保证 psiClass / psiMethod 都不为空
     */
    fun toAppFileTree(): AppFileTree {
        val realProjectFileTrees = projectTrees.map { it.toProjectFileTree() }.toMutableList()
        return AppFileTree(projectFileTrees = realProjectFileTrees)
    }
}

// ----------------------------
// “项目文件树”
// ----------------------------
data class ProjectFileTree(
    val project: Project,
    val modules: MutableList<ModuleDependency> = mutableListOf(),
    val mavenDependencies: MutableList<MavenDependency> = mutableListOf()
) {
    fun toSerializable(): SerializableProjectFileTree {
        return SerializableProjectFileTree(
            projectName = project.name,
            modules = modules.map { it.toSerializable() },
            mavenDependencies = mavenDependencies.map { it.toSerializable() }
        )
    }
}

@Serializable
data class SerializableProjectFileTree(
    val projectName: String = "",
    val modules: List<SerializableModuleDependency> = emptyList(),
    val mavenDependencies: List<SerializableMavenDependency> = emptyList()
) {
    fun toProjectFileTree(): ProjectFileTree {
        // 获取对应的 Project 实例
        val project = ProjectManager.getInstance().openProjects
            .find { it.name == projectName }
            ?: throw IllegalStateException("Cannot find project with name: $projectName")

        return ProjectFileTree(
            project = project,
            modules = modules.map {
                SerializableModuleDependency(
                    moduleName = it.moduleName,
                    packages = it.packages
                ).toModuleDependency(project)
            }.toMutableList(),
            mavenDependencies = mavenDependencies.map {
                SerializableMavenDependency(
                    groupId = it.groupId,
                    artifactId = it.artifactId,
                    version = it.version,
                    packages = it.packages
                ).toMavenDependency(project)
            }.toMutableList()
        )
    }
}

// [Module Name]
data class ModuleDependency(
    val moduleName: String,
    val packages: MutableList<PackageDependency> = mutableListOf()
) {
    fun toSerializable(): SerializableModuleDependency {
        return SerializableModuleDependency(
            moduleName = moduleName,
            packages = packages.map { it.toSerializable() }
        )
    }
}

@Serializable
data class SerializableModuleDependency(
    val moduleName: String,
    val packages: List<SerializablePackageDependency> = emptyList()
) {
    fun toModuleDependency(project: Project): ModuleDependency {
        return ModuleDependency(
            moduleName = moduleName,
            packages = packages.map { it.toPackageDependency(project) }.toMutableList()
        )
    }
}

// Maven Dependencies
data class MavenDependency(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val packages: MutableList<PackageDependency> = mutableListOf()
) {
    fun toSerializable(): SerializableMavenDependency {
        return SerializableMavenDependency(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            packages = packages.map { it.toSerializable() }
        )
    }
}

@Serializable
data class SerializableMavenDependency(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val packages: List<SerializablePackageDependency> = emptyList()
){
    fun toMavenDependency(project: Project): MavenDependency {
        return MavenDependency(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            packages = packages.map { it.toPackageDependency(project) }.toMutableList()
        )
    }
}

// [Package Name]
data class PackageDependency(
    val packageName: String,
    val files: MutableList<ProjectFile> = mutableListOf()
) {
    fun toSerializable(): SerializablePackageDependency {
        return SerializablePackageDependency(
            packageName = packageName,
            files = files.map { it.toSerializable() }
        )
    }
}

@Serializable
data class SerializablePackageDependency(
    val packageName: String,
    val files: List<SerializableProjectFile> = emptyList()
) {
    fun toPackageDependency(project: Project): PackageDependency {
        return PackageDependency(
            packageName = packageName,
            files = files.map { it.toProjectFile(project) }.toMutableList()
        )
    }
}

// ----------------------------
// “项目文件”
// ----------------------------
data class ProjectFile(
    val filePath: String = "",            // 1. 相对项目根目录路径
    val virtualFile: VirtualFile,
    val ifMavenFile: Boolean = false,
    val psiFile: PsiFile?,
    val classes: MutableList<ProjectClass> = mutableListOf(),
    var whole: Boolean = false
) {
    fun toSerializable(): SerializableProjectFile {
        return SerializableProjectFile(
            filePath = filePath,
            classes = classes.map { it.toSerializable() },
            whole = whole
        )
    }

    // 获取当前有效的 Classes
    fun getCurrentClasses(): List<ProjectClass> {
        return if (whole) {
            // 如果是 whole，返回文件中所有类
            psiFile.let {
                PsiTreeUtil.findChildrenOfType(it, PsiClass::class.java).map { psiClass ->
                    ProjectClass(
                        className = psiClass.name ?: "",
                        psiClass = psiClass,
                        methods = mutableListOf(),
                        whole = true
                    )
                }
            }
        } else {
            // 否则返回指定的类列表
            classes
        }
    }

    fun removeClasses(classesToRemove: List<ProjectClass>) {
        if (whole) {
            // 如果当前 file 被标记为 whole，需要先降级为 partial
            whole = false
            classes.clear()

            // 1. 获取当前文件里的所有 PsiClass
            psiFile?.let { psiF ->
                val allPsiClasses = PsiTreeUtil.findChildrenOfType(psiF, PsiClass::class.java)

                // 2. 为了避免单纯用 name() 引发重复名冲突，使用 qualifiedName 做对比
                val qualifiedNamesToRemove = classesToRemove
                    .mapNotNull { it.psiClass.qualifiedName }
                    .toSet()

                allPsiClasses.forEach { psiClass ->
                    val psiQName = psiClass.qualifiedName
                    val shouldKeep = psiQName == null || !qualifiedNamesToRemove.contains(psiQName)
                    if (shouldKeep) {
                        // 对于保留的类，这里依然默认它是 whole=true
                        // 表示"整个类"都用，不关心方法粒度
                        classes.add(
                            ProjectClass(
                                className = psiClass.name ?: "",
                                psiClass = psiClass,
                                methods = mutableListOf(),
                                whole = true
                            )
                        )
                    }
                }
            }
        } else {
            // 如果不是 whole，意味着我们已经在 classes 里记录了需要的类
            // 这里直接根据 qualifiedName 来匹配移除
            val qualifiedNamesToRemove = classesToRemove
                .mapNotNull { it.psiClass.qualifiedName }
                .toSet()

            classes.removeAll { projectClass ->
                val qName = projectClass.psiClass.qualifiedName
                qName != null && qualifiedNamesToRemove.contains(qName)
            }
        }
    }
}

@Serializable
data class SerializableProjectFile(
    val filePath: String = "",
    val ifMavenFile: Boolean = false,
    val classes: List<SerializableProjectClass>? = null,
    val whole: Boolean = false
) {
    fun toProjectFile(project: Project): ProjectFile {
        val vFile: VirtualFile
        if (!ifMavenFile) {
            vFile = project.baseDir.findFileByRelativePath(filePath)
                ?: throw IllegalStateException("Cannot find filePath=$filePath in project=${project.name}")
        } else {
            vFile = LocalFileSystem.getInstance().findFileByPath(filePath)!!
        }

        val psiFile = PsiManager.getInstance(project).findFile(vFile)

        // 如果是 whole，就不需要转换具体的类
        val realClasses = if (whole) {
            mutableListOf()
        } else {
            // 只有当文件是 Java/Kotlin/Scala 等支持类的文件时才处理类
            when {
                psiFile is PsiJavaFile ||
                        psiFile?.fileType?.name?.contains("KOTLIN") == true ||
                        psiFile?.fileType?.name?.contains("SCALA") == true -> {
                    classes?.map { it.toProjectClass(psiFile) }?.toMutableList() ?: mutableListOf()
                }
                else -> mutableListOf()
            }
        }

        return ProjectFile(
            filePath = filePath,
            virtualFile = vFile,
            psiFile = psiFile,  // 这里可以为 null
            classes = realClasses,
            whole = whole
        )
    }
}

// ----------------------------
// “项目类”
// ----------------------------
data class ProjectClass(
    val className: String,
    val psiClass: PsiClass,               // 现在保证不能为空
    val isAtomicClass: Boolean = PsiUtils.isAtomicClass(psiClass),
    val methods: MutableList<ProjectMethod>,
    var whole: Boolean
) {
    fun toSerializable(): SerializableProjectClass {
        return SerializableProjectClass(
            className = className,
            methods = methods.map { it.toSerializable() },
            whole = whole
        )
    }

    fun getCurrentMethods(): List<ProjectMethod> {
        if (isAtomicClass) {
            return emptyList()
        }
        return if (whole) {
            // 如果是 whole，返回类中所有方法
            psiClass.methods.map { method ->
                ProjectMethod(
                    methodName = method.name,
                    parameterTypes = method.parameterList.parameters.map { it.type.canonicalText },
                    psiMethod = method
                )
            }
        } else {
            // 否则返回指定的方法列表
            methods
        }
    }

    fun removeMethods(methodsToRemove: List<ProjectMethod>) {
        if (whole) {
            // 若当前 class 标记为 whole，需要先降级为 partial
            whole = false
            methods.clear()

            // 把类中的所有方法枚举出来
            val allPsiMethods = psiClass.methods
            // 映射出要移除的方法签名
            val toRemoveSignatures = methodsToRemove.map { removed ->
                removed.methodName to removed.parameterTypes
            }.toSet()

            allPsiMethods.forEach { m ->
                val mParamTypes = m.parameterList.parameters.map { it.type.canonicalText }
                val signature = m.name to mParamTypes
                val shouldKeep = !toRemoveSignatures.contains(signature)
                if (shouldKeep) {
                    methods.add(
                        ProjectMethod(
                            methodName = m.name,
                            parameterTypes = mParamTypes,
                            psiMethod = m
                        )
                    )
                }
            }
        } else {
            // 如果不是 whole，直接从 methods 中移除匹配的
            val toRemoveSignatures = methodsToRemove.map { it.methodName to it.parameterTypes }.toSet()

            methods.removeAll { pm ->
                val signature = pm.methodName to pm.parameterTypes
                toRemoveSignatures.contains(signature)
            }
        }
    }
}

@Serializable
data class SerializableProjectClass(
    val className: String = "",
    val methods: List<SerializableProjectMethod> = emptyList(),
    val whole: Boolean = false
) {
    fun toProjectClass(psiFile: PsiFile): ProjectClass {
        // 在 psiFile 里查找同名的 PsiClass
        val foundPsiClass = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
            .find { it.name == className }
            ?: throw IllegalStateException("Cannot find class=$className in file=${psiFile.name}")

        // 如果标记了 whole，就返回空的方法列表
        val realMethods = if (whole) {
            mutableListOf()
        } else {
            // 否则只转换指定的方法
            methods.map { it.toProjectMethod(foundPsiClass) }.toMutableList()
        }

        return ProjectClass(
            className = className,
            psiClass = foundPsiClass,
            methods = realMethods,
            whole = whole
        )
    }
}

// ----------------------------
// “项目方法”
// ----------------------------
data class ProjectMethod(
    val methodName: String,
    val parameterTypes: List<String> = emptyList(),
    val psiMethod: PsiMethod             // 现在保证不能为空
) {
    fun toSerializable(): SerializableProjectMethod {
        return SerializableProjectMethod(
            methodName = methodName,
            parameterTypes = parameterTypes
        )
    }
}

@Serializable
data class SerializableProjectMethod(
    val methodName: String = "",
    val parameterTypes: List<String> = emptyList()
) {
    /**
     * 反序列化：在给定的 PsiClass 中找对应的方法
     * 如果找不到就抛异常，保证 psiMethod 不为空
     */
    fun toProjectMethod(psiClass: PsiClass): ProjectMethod {
        val foundPsiMethod = psiClass.methods.find { method ->
            method.name == methodName &&
                    method.parameterList.parameters.map { p -> p.type.canonicalText } == parameterTypes
        } ?: throw IllegalStateException(
            "Cannot find method=$methodName(param=$parameterTypes) in class=${psiClass.name}"
        )

        return ProjectMethod(
            methodName = methodName,
            parameterTypes = parameterTypes,
            psiMethod = foundPsiMethod
        )
    }
}
