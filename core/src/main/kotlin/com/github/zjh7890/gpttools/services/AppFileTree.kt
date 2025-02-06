package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.toolWindow.treePanel.CheckState
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
 *  文件理解(结合 whole/partial 与 selected 的关系):
 *  ----------------------------
 *
 *  1. whole/partial 的含义(新版,无隐式节点):
 *     - 当某个文件(ProjectFile)或类(ProjectClass)设置为 whole=true,表示"我们需要使用该文件/类的全部内容"。
 *       但**与原设计不同**的是:我们依旧会在数据结构中"显式"地列出它的全部子节点(例如文件下的所有类、类中的所有方法),
 *       不再将其视为"隐式存在"而不存储。这样可以避免后续操作时出现"先降级为 partial 再重新扫描子节点"的繁琐流程。
 *     - 当标记为 whole=false,则意味着"只需要其中的一部分",这时我们会在 children 列表里(比如 ProjectFile.classes、
 *       ProjectClass.methods)列举出所需的具体子节点。若需要更多子节点,可以随时调用相关方法添加;若要移除部分子节点,也可以直接删除。
 *
 *     - 总之,"whole=true" 现在只是一个标签,表示"当前对象及其所有子节点都被完整使用"。**但不再依赖"清空子节点"来表示整对象引用**,
 *       数据结构中依旧能看到它包含的所有类或方法。
 *
 *  2. 移除逻辑:
 *     - **删除整个对象(文件 / 类)**:若我们不再需要这个文件或类,可直接从上级列表中移除它。例如在 PackageDependency.files 里 `remove` 掉
 *       对应的 ProjectFile,或在 ProjectFile.classes 中 `remove` 掉对应的 ProjectClass。
 *     - **删除部分子节点(类中的部分方法等)**:不论父级是 whole=true 还是 whole=false,都可以直接操作其子节点列表进行删除;无需再做"降级"。
 *       因为即使是 whole=true,我们也已显式存了所有子节点,对它们的增删改查都可以直接进行。
 *
 *  3. selected 与 whole/partial 的关系:
 *     - selected 表示在后续某些操作(例如 UI 勾选、批量处理)时是否被选中,与 "是否纳入(whole/partial)" 无直接影响。
 *       两者是不同维度:数据结构中是否存在节点,取决于是否 addFile/addClass/addMethod;而节点是否选中,取决于 UI 或逻辑对 selected 的设置。
 *     - 即使文件或类是 whole=true,因为我们采用"无隐式节点"的方式,子节点也都在数据结构中显式存在,也可独立设置 selected = true / false。
 *       具体是否这样使用,视你业务需求而定。
 *
 *  4. 关于"整对象 / 部分对象"的转换:
 *     - 在旧设计中,如果某个文件/类是 whole=true 而要部分移除某个方法,需要先"降级为 partial",再重新扫描子节点。现在则无需如此。
 *     - 如果业务上还需要"切换 whole ↔ partial"的标记,也可以做,但这只是一种标签状态切换;子节点依旧存储在数据结构中,并不受影响。
 */
data class AppFileTree(
    val projectFileTrees: MutableList<ProjectFileTree> = mutableListOf(),
    var state: CheckState = CheckState.SELECTED
) {
    fun toSerializable(): SerializableAppFileTree {
        return SerializableAppFileTree(
            projectTrees = projectFileTrees.map { it.toSerializable() },
            state = state
        )
    }

    fun addFile(file: VirtualFile, project: Project, whole: Boolean = false) {
        // 1. 找到或创建当前 Project 对应的 projectFileTree
        val pft = findOrCreateProjectFileTree(this, project)

        // 2. 本地 or 外部依赖
        val projectPath = project.basePath ?: ""
        val isExternal = !file.path.startsWith(projectPath)

        if (!isExternal) {
            // 本地文件:直接按包组织
            val flattenedPath = getFlattenedLocalDirPath(file, project)
            val packageDep = findOrCreatePackage(pft.localPackages, flattenedPath)
            val projFile = findOrCreateProjectFile(packageDep.files, file, project, isMaven = false, whole = whole)

            if (whole) {
                // 如果 whole=true,就显式添加文件中的所有类
                val psiFile = PsiManager.getInstance(project).findFile(file)
                if (psiFile != null) {
                    val allPsiClasses = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
                    for (cls in allPsiClasses) {
                        addClass(cls, project, whole = true)
                    }
                }
            }
        } else {
            // 外部依赖文件:
            val mavenInfo = extractMavenInfo(file.path) ?: return
            val mavenDep = findOrCreateMavenDependency(
                pft.mavenDependencies, mavenInfo.groupId, mavenInfo.artifactId, mavenInfo.version
            )

            // 用新方法获取 jar 内部扁平路径
            val flattenedPath = getFlattenedMavenDirPath(file)
            val packageDep = findOrCreatePackage(mavenDep.packages, flattenedPath)

            val projFile = findOrCreateProjectFile(packageDep.files, file, project, isMaven = true, whole = whole)

            if (whole) {
                val psiFile = PsiManager.getInstance(project).findFile(file)
                if (psiFile != null) {
                    val allPsiClasses = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
                    for (cls in allPsiClasses) {
                        addClass(cls, project, whole = true)
                    }
                }
            }
        }
    }

    fun addClass(psiClass: PsiClass, project: Project, whole: Boolean = true) {
        // 1. 先保证所在文件已加入
        val containingFile = psiClass.containingFile?.virtualFile ?: return
        // 这里若直接传入 whole=true 可能会形成再次递归,你可根据需要来决定
        // 如果文件层面也想显式列出所有类,则可将 addFile 第二个参数也设为 true
        addFile(containingFile, project, whole = false)

        // 2. 找到或创建 projectFileTree
        val pft = findOrCreateProjectFileTree(this, project)

        val projectPath = project.basePath ?: ""
        val isExternal = !containingFile.path.startsWith(projectPath)

        // 3. 找到对应的 ProjectFile
        val (packageDep, projectFile) = if (!isExternal) {
            // 本地文件:直接按包组织
            val flattenedPath = getFlattenedLocalDirPath(containingFile, project)
            val pkgDep = findOrCreatePackage(pft.localPackages, flattenedPath)
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
            val flattenedPath = getFlattenedMavenDirPath(containingFile)
            val pkgDep = findOrCreatePackage(mavenDep.packages, flattenedPath)
            val projFile = findOrCreateProjectFile(pkgDep.files, containingFile, project, isMaven = true)
            pkgDep to projFile
        }

        // 4. 找/建 对应的 ProjectClass,并设置 whole = 传入值
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
            // 如果已存在,可根据需求更新它的 whole
            existingClass.whole = whole
            existingClass
        }

        // 如果我们希望"整类"也显式地列出它所有方法
        if (whole) {
            // 此时就递归把所有方法都加进来
            for (m in psiClass.methods) {
                addMethod(m, project)  // addMethod 原本没有 whole 概念,方法本身也无 whole
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
            // 本地文件:直接按包组织
            val flattenedPath = getFlattenedLocalDirPath(containingFile, project)
            val pkgDep = findOrCreatePackage(pft.localPackages, flattenedPath)
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
            val flattenedPath = getFlattenedMavenDirPath(containingFile)
            val pkgDep = findOrCreatePackage(mavenDep.packages, flattenedPath)
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
            // 本地文件:直接按包组织
            val flattenedPath = getFlattenedLocalDirPath(file, project)
            val packageDep = findOrCreatePackage(pft.localPackages, flattenedPath)
            packageDep.files.removeAll { it.virtualFile == file }
            // 如果 packageDep.files 为空,也可以选择把整个 packageDep 移除
            pft.localPackages.removeAll { it.files.isEmpty() }
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
            // 本地文件:直接按包组织
            val flattenedPath = getFlattenedLocalDirPath(containingFile, project)
            val packageDep = findOrCreatePackage(pft.localPackages, flattenedPath)
            packageDep.files.find { it.virtualFile == containingFile }
        } else {
            // Maven 外部依赖
            val mavenInfo = extractMavenInfo(containingFile.path) ?: return
            val mavenDep = pft.mavenDependencies.find {
                it.groupId == mavenInfo.groupId &&
                        it.artifactId == mavenInfo.artifactId &&
                        it.version == mavenInfo.version
            } ?: return
            val flattenedPath = getFlattenedMavenDirPath(containingFile)
            val packageDep = findOrCreatePackage(mavenDep.packages, flattenedPath)
            packageDep.files.find { it.virtualFile == containingFile }
        } ?: return

        // 如果文件是 whole,需要先降级为 partial,这样才能在其 classes 列表里找到并移除目标类
        if (targetFile.whole) {
            targetFile.whole = false
            targetFile.classes.clear()

            // 把当前文件里的所有类都加载进来,但排除要移除的这个类
            val psiFile = targetFile.psiFile ?: return
            val allPsiClasses = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile, com.intellij.psi.PsiClass::class.java)

            // 用 qualifiedName 来排除目标类,避免重名冲突
            val removeQName = projectClass.psiClass.qualifiedName
            allPsiClasses.forEach { c ->
                val cQName = c.qualifiedName
                if (cQName == null || cQName != removeQName) {
                    // 保留的类默认标记为 whole=true(整个类使用)
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
            // 如果文件本来就是 partial,则直接调用 removeClasses 即可
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
            // 本地文件:直接按包组织
            val flattenedPath = getFlattenedLocalDirPath(containingFile, project)
            val packageDep = findOrCreatePackage(pft.localPackages, flattenedPath)
            packageDep.files.find { it.virtualFile == containingFile }
        } else {
            val mavenInfo = extractMavenInfo(containingFile.path) ?: return
            val mavenDep = pft.mavenDependencies.find {
                it.groupId == mavenInfo.groupId &&
                        it.artifactId == mavenInfo.artifactId &&
                        it.version == mavenInfo.version
            } ?: return
            val flattenedPath = getFlattenedMavenDirPath(containingFile)
            val packageDep = findOrCreatePackage(mavenDep.packages, flattenedPath)
            packageDep.files.find { it.virtualFile == containingFile }
        }

        if (targetFile == null) return

        // **如果 targetFile 是 whole,需要先降级为 partial**,
        // 这样才能在 targetFile.classes 中找到对应的类。
        if (targetFile.whole) {
            targetFile.whole = false
            targetFile.classes.clear()

            // 把这个文件里的所有 PsiClass 加载进来
            val psiFile = targetFile.psiFile
            if (psiFile != null) {
                val allPsiClasses = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(psiFile, com.intellij.psi.PsiClass::class.java)
                allPsiClasses.forEach { psiClass ->
                    // 这里可以选择让每个类默认 whole=true(表示整类引用)
                    // 或直接收集全部方法 whole=false(看你的需求)
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

        // 现在 targetFile.classes 里有东西了,可以找到目标 class
        val realClass = targetFile.classes.find {
            it.psiClass == projectMethod.psiMethod.containingClass
        }
        // 如果找不到,就可能是因为 class 也是 whole=true,需要再去做降级
        // 或者我们也可以直接找 qualifiedName 去匹配
        if (realClass == null) {
            // 可以考虑查 qualifiedName 再找一下
            // 或者就直接 return
            return
        }

        // **如果 realClass 也是 whole=true,需要再降级**,
        // 然后移除目标方法
        if (realClass.whole) {
            realClass.whole = false
            realClass.methods.clear()

            val allPsiMethods = realClass.psiClass.methods
            val methodSignToRemove = projectMethod.methodName to projectMethod.parameterTypes

            allPsiMethods.forEach { m ->
                val signature = m.name to m.parameterList.parameters.map { it.type.canonicalText }
                // 如果不是要移除的方法,就保留
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
            // 如果 realClass 不是 whole,直接 remove 就行
            realClass.removeMethods(listOf(projectMethod))
        }
    }


    /**
     * 轻量获取 moduleName:从 file 路径里截取项目路径后第一级目录名。
     * 如果需要更精细的逻辑,可自定义或复用 DependencyUtils 的类似实现。
     */
    private fun getModuleName(file: VirtualFile, project: Project): String {
        val basePath = project.basePath ?: return "UnknownModule"
        val relPath = file.path.removePrefix(basePath).removePrefix("/")
        // 如果相对路径中没有 "/" ,说明文件就在根目录下
        if (!relPath.contains("/")) {
            return "."
        }
        return relPath.split("/").firstOrNull() ?: "UnknownModule"
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
                        localPackages = mutableListOf(),
                        mavenDependencies = mutableListOf()
                    )
                }

                // 判断是否是外部依赖
                val projectPath = project.basePath ?: ""
                val isExternal = !vFile.path.startsWith(projectPath)

                if (!isExternal) {
                    // 本地文件:直接按包组织
                    val flattenedPath = getFlattenedLocalDirPath(vFile, project)
                    val packageDep = findOrCreatePackage(projectFileTree.localPackages, flattenedPath)
                    val projectFile = findOrCreateProjectFile(packageDep.files, vFile, project)

                    // 将当前 psiClass + usedMethods 放进 projectFile.classes
                    fillProjectClassAndMethods(projectFile, psiClass, dependencyInfo)
                } else {
                    val mavenInfo = extractMavenInfo(vFile.path)
                    if (mavenInfo != null) {
                        val mavenDep = findOrCreateMavenDependency(
                            projectFileTree.mavenDependencies,
                            mavenInfo.groupId,
                            mavenInfo.artifactId,
                            mavenInfo.version
                        )
                        val flattenedPath = getFlattenedMavenDirPath(vFile)
                        val packageDep = findOrCreatePackage(mavenDep.packages, flattenedPath)
                        val projectFile = findOrCreateProjectFile(packageDep.files, vFile, project, isMaven = true)

                        fillProjectClassAndMethods(projectFile, psiClass, dependencyInfo)
                    }
                }
            }

            return AppFileTree(
                projectFileTrees = projectFileTreeMap.values.toMutableList()
            )
        }

        fun getFlattenedLocalDirPath(
            file: VirtualFile,
            project: Project
        ): String {
            val basePath = project.basePath?.removeSuffix("/") ?: ""
            val absPath = file.path

            // 移除项目基路径
            val relative = absPath.removePrefix(basePath).removePrefix("/")

            // 去掉文件名，只保留目录路径
            val lastSlash = relative.lastIndexOf("/")
            return if (lastSlash != -1) {
                relative.substring(0, lastSlash)
            } else {
                "." // 如果没有目录分隔符，说明文件在项目根目录，返回 "."
            }
        }

        fun getFlattenedMavenDirPath(
            file: VirtualFile
        ): String {
            val fullPath = file.path
            val jarSeparator = "!/"
            val idx = fullPath.indexOf(jarSeparator)
            if (idx != -1 && idx + jarSeparator.length < fullPath.length) {
                val insideJar = fullPath.substring(idx + jarSeparator.length)

                // 去掉文件名，只保留目录路径
                val lastSlash = insideJar.lastIndexOf("/")
                return if (lastSlash != -1) {
                    insideJar.substring(0, lastSlash)
                } else {
                    "." // 如果 insideJar 中没有 "/"，说明在 jar 内部根目录，返回 "."
                }
            }
            return "(unknown-internal-path)"
        }

        /**
         * 根据 DependenciesTreePanel 原先的逻辑,把外部依赖 jar 路径解析出 groupId, artifactId, version
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
         * 在 projectFileTrees 中找出对应的 ProjectFileTree;若无则新建。
         */
        fun findOrCreateProjectFileTree(appFileTree: AppFileTree, project: Project): ProjectFileTree {
            val existing = appFileTree.projectFileTrees.find { it.project == project }
            if (existing != null) return existing

            val newPft = ProjectFileTree(
                project = project,
                localPackages = mutableListOf(),
                mavenDependencies = mutableListOf()
            )
            appFileTree.projectFileTrees.add(newPft)
            return newPft
        }

        fun findOrCreateMavenDependency(
            mavenDeps: MutableList<MavenDependency>,
            groupId: String,
            artifactId: String,
            version: String
        ): MavenDependency {
            mavenDeps.find {
                it.groupId.equals(groupId, ignoreCase = true) &&
                        it.artifactId.equals(artifactId, ignoreCase = true) &&
                        it.version.equals(version, ignoreCase = true)
            }?.let { return it }

            val newDep = MavenDependency(groupId = groupId, artifactId = artifactId, version = version)
            // 构造比较字符串，例如 "com.example:my-lib:1.0.0"
            val newKey = "$groupId:$artifactId:$version"
            val index = mavenDeps.binarySearch {
                val key = "${it.groupId}:${it.artifactId}:${it.version}"
                key.compareTo(newKey, ignoreCase = true)
            }
            val insertionIndex = if (index < 0) -index - 1 else index
            mavenDeps.add(insertionIndex, newDep)
            return newDep
        }

        fun findOrCreatePackage(
            packages: MutableList<PackageDependency>,
            packageName: String
        ): PackageDependency {
            // 先尝试根据忽略大小写比较查找是否已存在该 package
            packages.find { it.packageName.equals(packageName, ignoreCase = true) }?.let {
                return it
            }
            val newPackage = PackageDependency(packageName)
            // 使用二分查找确定插入位置，按 packageName 忽略大小写排序
            val index = packages.binarySearch { it.packageName.compareTo(packageName, ignoreCase = true) }
            val insertionIndex = if (index < 0) -index - 1 else index
            packages.add(insertionIndex, newPackage)
            return newPackage
        }

        fun findOrCreateProjectFile(
            files: MutableList<ProjectFile>,
            vFile: VirtualFile,
            project: Project,
            isMaven: Boolean = false,
            whole: Boolean = false
        ): ProjectFile {
            val basePath = project.basePath?.removeSuffix("/") ?: ""
            val absolutePath = vFile.path
            val relativePath = if (!isMaven) {
                absolutePath.removePrefix(basePath).removePrefix("/")
            } else {
                absolutePath
            }
            files.find { it.filePath == relativePath }?.let { return it }

            val psiFile = PsiManager.getInstance(project).findFile(vFile)
            val newFile = ProjectFile(
                filePath = relativePath,
                virtualFile = vFile,
                psiFile = psiFile,
                classes = mutableListOf(),
                whole = whole
            )
            // 按文件名（vFile.name）忽略大小写排序，使用二分查找确定插入位置
            val fileName = vFile.name
            val index = files.binarySearch { it.virtualFile.name.compareTo(fileName, ignoreCase = true) }
            val insertionIndex = if (index < 0) -index - 1 else index
            files.add(insertionIndex, newFile)
            return newFile
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
         * 生成依赖文本，只生成 state 为 SELECTED 的节点
         */
        fun generateDependenciesTextCombined(appFileTree: AppFileTree): String {
            // 只处理状态为 SELECTED 的 ProjectFileTree 节点
            val filteredProjects = appFileTree.projectFileTrees.filter { it.state == CheckState.SELECTED }

            return filteredProjects.joinToString("\n\n") { projectFileTree ->
                // ---- 1) 处理本地文件 ----
                val localFilesContents = projectFileTree.localPackages
                    .filter { it.state == CheckState.SELECTED }  // 只处理选中的 package
                    .flatMap { it.files }
                    .filter { it.state == CheckState.SELECTED }  // 只处理选中的 file
                    .mapNotNull { projectFile ->
                        handleSingleProjectFileFiltered(projectFile, projectFileTree.project)
                    }

                // ---- 2) 处理 Maven Dependencies ----
                val mavenFilesContents = projectFileTree.mavenDependencies
                    .filter { it.state == CheckState.SELECTED }  // 只处理选中的 MavenDependency
                    .flatMap { it.packages }
                    .filter { it.state == CheckState.SELECTED }
                    .flatMap { it.files }
                    .filter { it.state == CheckState.SELECTED }
                    .mapNotNull { projectFile ->
                        handleSingleProjectFileFiltered(projectFile, projectFileTree.project)
                    }

                // 合并后用 "\n\n" 拼接成最终字符串
                (localFilesContents + mavenFilesContents).joinToString("\n\n")
            }
        }

        /**
         * 处理单个 ProjectFile，仅生成其内部 state 为 SELECTED 的类/方法内容
         */
        private fun handleSingleProjectFileFiltered(
            projectFile: ProjectFile,
            project: Project
        ): String? {
            val virtualFile = projectFile.virtualFile
            val absolutePath = virtualFile.path

            val relativePath = calculateRelativePath(absolutePath, project, projectFile.ifMavenFile)

            // 如果文件是 whole，则无论内部如何，都返回整个文件内容
            if (projectFile.whole) {
                return "$relativePath\n" + FileUtil.readFileInfoForLLM(virtualFile)
            }

            // 否则仅处理部分文件，提取 state 为 SELECTED 的类/方法
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
            val elementsToProcess = mutableListOf<PsiElement>()

            // 遍历文件中记录的所有 ProjectClass，仅处理状态为 SELECTED 的类
            projectFile.classes
                .filter { it.state == CheckState.SELECTED }
                .forEach { projectClass ->
                    // 查找文件中的对应 PsiClass（如果是 whole，则匹配 qualifiedName，否则匹配类名）
                    val foundPsiClass = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java).find {
                        if (projectClass.whole) {
                            it.qualifiedName == projectClass.className
                        } else {
                            it.name == projectClass.className
                        }
                    } ?: return@forEach

                    if (projectClass.whole) {
                        // whole 模式下，直接将整个 PsiClass 加入处理列表
                        elementsToProcess.add(foundPsiClass)
                    } else {
                        // 否则只处理该类中状态为 SELECTED 的方法
                        projectClass.methods
                            .filter { it.state == CheckState.SELECTED }
                            .forEach { projectMethod ->
                                val matchedMethod = foundPsiClass
                                    .findMethodsByName(projectMethod.methodName, false)
                                    .find { m ->
                                        val paramTypes = m.parameterList.parameters.map { p -> p.type.canonicalText }
                                        paramTypes == projectMethod.parameterTypes
                                    }
                                if (matchedMethod != null) {
                                    elementsToProcess.add(matchedMethod)
                                }
                            }
                    }
                }

            if (elementsToProcess.isNotEmpty()) {
                val singleFileContent = ElementsDepsInSingleFileAction.depsInSingleFile(elementsToProcess, project)
                if (!singleFileContent.isNullOrBlank()) {
                    return "$relativePath\n" + FileUtil.wrapBorder(singleFileContent.trim())
                }
            }

            // 如果没有任何可处理的元素，则返回 null
            return null
        }

        /**
         * 保持原先的逻辑，根据本地项目文件或 Maven 路径计算"相对路径"。
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
    val projectTrees: List<SerializableProjectFileTree> = emptyList(),
    var state: CheckState = CheckState.SELECTED
) {
    /**
     * 反序列化 -> 立即遍历所有文件/类/方法，找出对应 PSI 实例
     * 使得后续使用时保证 psiClass / psiMethod 都不为空
     */
    fun toAppFileTree(): AppFileTree {
        val realProjectFileTrees = projectTrees.map { it.toProjectFileTree() }.toMutableList()
        return AppFileTree(projectFileTrees = realProjectFileTrees, state = state)
    }
}

data class ProjectFileTree(
    val project: Project,
    val localPackages: MutableList<PackageDependency> = mutableListOf(), // 替换原来的 modules
    val mavenDependencies: MutableList<MavenDependency> = mutableListOf(),
    var state: CheckState = CheckState.SELECTED
) {
    fun toSerializable(): SerializableProjectFileTree {
        return SerializableProjectFileTree(
            projectName = project.name,
            localPackages = localPackages.map { it.toSerializable() },  // 修改这里
            mavenDependencies = mavenDependencies.map { it.toSerializable() },
            state = state
        )
    }
}

@Serializable
data class SerializableProjectFileTree(
    val projectName: String = "",
    val localPackages: List<SerializablePackageDependency> = emptyList(), // 替换原来的 modules
    val mavenDependencies: List<SerializableMavenDependency> = emptyList(),
    var state: CheckState = CheckState.SELECTED
) {
    fun toProjectFileTree(): ProjectFileTree {
        val project = ProjectManager.getInstance().openProjects
            .find { it.name == projectName }
            ?: throw IllegalStateException("Cannot find project with name: $projectName")

        return ProjectFileTree(
            project = project,
            localPackages = localPackages.map { it.toPackageDependency(project) }.toMutableList(), // 修改这里
            mavenDependencies = mavenDependencies.map { it.toMavenDependency(project) }.toMutableList(),
            state = state
        )
    }
}

// [Module Name]
data class ModuleDependency(
    val moduleName: String,
    val packages: MutableList<PackageDependency> = mutableListOf(),
    var state: CheckState = CheckState.SELECTED         // 添加 selected 字段
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
    val moduleName: String = "",
    val packages: List<SerializablePackageDependency> = emptyList(),
    var state: CheckState = CheckState.SELECTED
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
    val packages: MutableList<PackageDependency> = mutableListOf(),
    var state: CheckState = CheckState.SELECTED         // 添加 selected 字段
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
    val groupId: String = "",
    val artifactId: String = "",
    val version: String = "",
    val packages: List<SerializablePackageDependency> = emptyList(),
    var state: CheckState = CheckState.SELECTED
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
    val files: MutableList<ProjectFile> = mutableListOf(),
    var state: CheckState = CheckState.SELECTED         // 添加 selected 字段
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
    val packageName: String = "",
    val files: List<SerializableProjectFile> = emptyList(),
    var state: CheckState = CheckState.SELECTED
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
    var whole: Boolean = false,
    var state: CheckState = CheckState.SELECTED         // 添加 selected 字段
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
    val whole: Boolean = false,
    var state: CheckState = CheckState.SELECTED
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
    var whole: Boolean,
    var state: CheckState = CheckState.SELECTED         // 添加 selected 字段
) {
    fun toSerializable(): SerializableProjectClass {
        return SerializableProjectClass(
            className = className,
            methods = methods.map { it.toSerializable() },
            whole = whole,
            state = state           // 传递 selected 值
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
    val whole: Boolean = false,
    var state: CheckState = CheckState.SELECTED
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
            whole = whole,
            state = state           // 传递 selected 值
        )
    }
}

// ----------------------------
// “项目方法”
// ----------------------------
data class ProjectMethod(
    val methodName: String,
    val parameterTypes: List<String> = emptyList(),
    val psiMethod: PsiMethod,            // 现在保证不能为空
    var state: CheckState = CheckState.SELECTED        // 添加 selected 字段
) {
    fun toSerializable(): SerializableProjectMethod {
        return SerializableProjectMethod(
            methodName = methodName,
            parameterTypes = parameterTypes,
            state = state          // 传递 selected 值
        )
    }
}

@Serializable
data class SerializableProjectMethod(
    val methodName: String = "",
    val parameterTypes: List<String> = emptyList(),
    var state: CheckState = CheckState.SELECTED
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
            psiMethod = foundPsiMethod,
            state = state           // 传递 selected 值
        )
    }
}
