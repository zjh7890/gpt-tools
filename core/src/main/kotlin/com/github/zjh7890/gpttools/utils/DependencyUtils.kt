package com.github.zjh7890.gpttools.utils

import com.github.zjh7890.gpttools.services.*
import com.github.zjh7890.gpttools.toolWindow.treePanel.ClassDependencyInfo
import com.github.zjh7890.gpttools.toolWindow.treePanel.MavenDependencyId
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil

/**
 * @Date: 2025/1/24 18:18
 */
object DependencyUtils {
    /**
     * 将 classDependencyGraph 转换为 AppFileTree
     */
    fun buildAppFileTreeFromClassGraph(
        classDependencyGraph: Map<PsiClass, ClassDependencyInfo>
    ): AppFileTree {
        // 以 Project 为粒度分组
        val projectFileTreeMap = mutableMapOf<Project, ProjectFileTree>()

        for ((psiClass, dependencyInfo) in classDependencyGraph) {
            val vFile = psiClass.containingFile?.virtualFile ?: continue
            val project = psiClass.project

            // 先拿到或创建对应的 ProjectFileTree
            val projectFileTree = projectFileTreeMap.getOrPut(project) {
                ProjectFileTree(
                    project = project,
                    // 注意，这里不再直接把所有文件都塞进 fileTree，
                    // 而是使用 modules 和 mavenDependencies 两个列表
                    modules = mutableListOf(),
                    mavenDependencies = mutableListOf()
                )
            }

            // 判断是否是外部依赖
            val projectPath = project.basePath ?: ""
            val isExternal = !vFile.path.startsWith(projectPath)

            if (!isExternal) {
                // 本地依赖 → 找到或创建对应的 module
                val moduleName = getModuleName(project, psiClass)
                val moduleDependency = findOrCreateModule(projectFileTree.modules, moduleName)

                // 找到或创建对应的 packageDependency
                val packageName = psiClass.qualifiedName
                    ?.substringBeforeLast('.')
                    ?: "(default package)"
                val packageDependency = findOrCreatePackage(moduleDependency.packages, packageName)

                // 找到或创建 ProjectFile
                val projectFile = findOrCreateProjectFile(packageDependency.files, vFile, project)

                // 最后将当前 psiClass + usedMethods 放进 projectFile.classes
                fillProjectClassAndMethods(projectFile, psiClass, dependencyInfo)
            } else {
                // 外部依赖 → 解析 maven info
                val mavenInfo = extractMavenInfo(vFile.path)
                if (mavenInfo != null) {
                    val mavenDependency = findOrCreateMavenDependency(
                        projectFileTree.mavenDependencies,
                        mavenInfo.groupId,
                        mavenInfo.artifactId,
                        mavenInfo.version
                    )
                    // 包名
                    val packageName = psiClass.qualifiedName
                        ?.substringBeforeLast('.')
                        ?: "(default package)"
                    val packageDependency = findOrCreatePackage(mavenDependency.packages, packageName)
                    val projectFile = findOrCreateProjectFile(packageDependency.files, vFile, project, isMaven = true)

                    fillProjectClassAndMethods(projectFile, psiClass, dependencyInfo)
                }
            }
        }

        // 若某个文件里所有类均被整类使用，则设置该文件为 whole
        projectFileTreeMap.values.forEach { projectFileTree ->
            projectFileTree.modules.forEach { module ->
                module.packages.forEach { pkg ->
                    pkg.files.forEach { file ->
                        if (file.classes.isNotEmpty() && file.classes.all { it.whole }) {
                            file.whole = true
                            file.classes.clear()
                        }
                    }
                }
            }
            projectFileTree.mavenDependencies.forEach { mdep ->
                mdep.packages.forEach { pkg ->
                    pkg.files.forEach { file ->
                        if (file.classes.isNotEmpty() && file.classes.all { it.whole }) {
                            file.whole = true
                            file.classes.clear()
                        }
                    }
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