package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.llm.ChatMessage
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.treePanel.ClassDependencyInfo
import com.github.zjh7890.gpttools.utils.DependencyUtils
import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

// ----------------------------
// 运行期使用的 ChatSession
// ----------------------------
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val messages: MutableList<ChatContextMessage> = mutableListOf(),
    val startTime: Long = System.currentTimeMillis(),
    val type: String = "chat",
    var appFileTree: AppFileTree = AppFileTree(),
    var withFiles: Boolean = true,
    val project: Project,                // 对话所属的 Project，就是开启会话的 Project
    val relevantProjects: MutableList<Project> = mutableListOf()    // 对话中涉及的 Project，可能有跨项目的文件
) {
    var classGraph: MutableMap<PsiClass, ClassDependencyInfo> = mutableMapOf()

    // 添加序列化方法
    fun toSerializable(): SerializableChatSession {
        return SerializableChatSession(
            id = id,
            messages = messages,
            startTime = startTime,
            type = type,
            withFiles = withFiles,
            appFileTree = appFileTree.toSerializable(),
            projectName = project.name,
            relevantProjectNames = relevantProjects.map { it.name }.toMutableList() // 添加这一行
        )
    }

    fun add(message: ChatContextMessage) {
        messages.add(message)
    }

    // 清空会话
    fun clear() {
        messages.clear()
    }

    // 导出聊天历史记录
    fun exportChatHistory(invalidContext: Boolean = false): String {
        val chatHistory = transformMessages(invalidContext).joinToString("\n\n") {
            val border = FileUtil.determineBorder(it.content)
            """
${it.role}:
${border}
${it.content}
${border}
            """.trimIndent()
        }
        saveChatHistoryToFile(chatHistory)
        return chatHistory
    }

    // 保存聊天记录到文件
    private fun saveChatHistoryToFile(chatHistory: String) {
        try {
            // 使用 formatFileName 方法生成文件名
            val fileName = formatFileName(type)
            val filePath = getPluginDataDirectory() + "/chat_history/$fileName"
            // 写入文件
            FileUtil.writeToFile(filePath, chatHistory)
        } catch (e: Exception) {
            logger.warn("Error saving chat history: ${e.message}")
        }
    }

    // 根据时间和类型格式化文件名
    private fun formatFileName(type: String): String {
        val date = Date(startTime)
        val dateFormat = SimpleDateFormat("yy-MM-dd HH-mm-ss")
        val formattedDate = dateFormat.format(date)
        return "$formattedDate-$type.txt"
    }

    // 获取插件数据存储目录
    private fun getPluginDataDirectory(): String {
        val userHome = System.getProperty("user.home")
        return "$userHome/.gpttools"
    }

    fun transformMessages(invalidContext: Boolean = false): MutableList<ChatMessage> {
        // 找到最后一个用户消息的索引
        val lastUserMessageIndex = messages.indexOfLast { it.role == ChatRole.user }

        return messages.mapIndexed { index, it ->
            when {
                // 1. 没有 context
                it.context.isBlank() -> ChatMessage(it.role, it.content)

                // 2. 有 context 但不是最后一个用户消息，或上下文失效
                index != lastUserMessageIndex || invalidContext -> {
                    ChatMessage(
                        it.role,
                        """
${it.content}
---
上下文信息：
${FileUtil.wrapBorder("上下文已失效")}
                        """.trimIndent()
                    )
                }
                // 3. 有 context 且是最后一个用户消息
                else -> {
                    ChatMessage(
                        it.role,
                        """
${it.content}
---
上下文信息：
${FileUtil.wrapBorder(it.context)}
                    """.trimIndent()
                    )
                }
            }
        }.toMutableList()
    }

    companion object {
        private val logger = logger<ChatCodingService>()
    }
}

// ----------------------------
// 可序列化的 ChatSession
// ----------------------------
@Serializable
data class SerializableChatSession(
    val id: String = "",
    val messages: MutableList<ChatContextMessage> = mutableListOf(),
    val startTime: Long = 0L,
    val type: String = "",
    val withFiles: Boolean = true,
    val appFileTree: SerializableAppFileTree = SerializableAppFileTree(),
    val projectName: String = "",
    val relevantProjectNames: MutableList<String> = mutableListOf()    // 改为 String 类型的项目名列表
) {
    fun toChatSession(): ChatSession {
        // 获取所有打开的项目
        val openProjects = ProjectManager.getInstance().openProjects
        // 找到主项目
        val mainProject = openProjects.find { it.name == projectName }
            ?: throw IllegalStateException("Cannot find project with name: $projectName")
        // 找到相关项目
        val relevantProjects = relevantProjectNames.mapNotNull { name ->
            openProjects.find { it.name == name }
        }.toMutableList()
        // 反序列化出 AppFileTree
        val realAppFileTree = appFileTree.toAppFileTree()
        return ChatSession(
            id = id,
            messages = messages,
            startTime = startTime,
            type = type,
            withFiles = withFiles,
            appFileTree = realAppFileTree,
            project = mainProject,
            relevantProjects = relevantProjects
        )
    }
}

// ----------------------------
// 聊天消息
// ----------------------------
@Serializable
data class ChatContextMessage(
    val role: ChatRole = ChatRole.user,
    var content: String = "",
    var context: String = "",
) {
    fun exportChatHistory(invalidContext: Boolean = false): String {
        return if (invalidContext) {
            content
        } else {
            "$role: $content\nContext: $context\n"
        }
    }

    fun add(session: ChatSession) {
        session.messages.add(this)
    }
}

// ----------------------------
// 非可序列化的 & 可序列化的 文件树结构
// ----------------------------
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
    fun addFile(file: VirtualFile, project: Project) {
        // 1. 找到或创建当前 Project 对应的 projectFileTree
        val pft = DependencyUtils.findOrCreateProjectFileTree(this, project)

        // 2. 判断本地 or 外部依赖
        val projectPath = project.basePath ?: ""
        val isExternal = !file.path.startsWith(projectPath)

        if (!isExternal) {
            // ------- 本地文件 -------
            // (1) 获取 moduleDependency
            val moduleName = getModuleName(file, project)
            val moduleDep = DependencyUtils.findOrCreateModule(pft.modules, moduleName)

            // (2) 获取 packageDependency
            val psiFile = PsiManager.getInstance(project).findFile(file)
            val packageName = getPackageName(psiFile)
            val packageDep = DependencyUtils.findOrCreatePackage(moduleDep.packages, packageName)

            // (3) 找 / 创建 ProjectFile
            DependencyUtils.findOrCreateProjectFile(packageDep.files, file, project, isMaven = false, whole = true)

        } else {
            // ------- 外部依赖文件 -------
            // (1) 解析 maven groupId/artifactId/version
            val mavenInfo = DependencyUtils.extractMavenInfo(file.path) ?: return
            val mavenDep = DependencyUtils.findOrCreateMavenDependency(
                pft.mavenDependencies,
                mavenInfo.groupId,
                mavenInfo.artifactId,
                mavenInfo.version
            )

            // (2) 获取 packageDependency
            val psiFile = PsiManager.getInstance(project).findFile(file)
            val packageName = getPackageName(psiFile)
            val packageDep = DependencyUtils.findOrCreatePackage(mavenDep.packages, packageName)

            // (3) 找 / 创建 ProjectFile
            DependencyUtils.findOrCreateProjectFile(packageDep.files, file, project, isMaven = true, whole = true)
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
        addFile(containingFile, project)

        // 2. 找到或创建 projectFileTree
        val pft = DependencyUtils.findOrCreateProjectFileTree(this, project)

        // 3. 判断本地 or 外部
        val projectPath = project.basePath ?: ""
        val isExternal = !containingFile.path.startsWith(projectPath)

        // 4. 找到对应的 [PackageDependency] -> [ProjectFile]
        val (packageDep, projectFile) = if (!isExternal) {
            val moduleName = getModuleName(containingFile, project)
            val moduleDep = DependencyUtils.findOrCreateModule(pft.modules, moduleName)

            val psiFile = PsiManager.getInstance(project).findFile(containingFile)
            val packageName = getPackageName(psiFile)
            val pkgDep = DependencyUtils.findOrCreatePackage(moduleDep.packages, packageName)
            val projFile = DependencyUtils.findOrCreateProjectFile(pkgDep.files, containingFile, project, false)
            pkgDep to projFile
        } else {
            val mavenInfo = DependencyUtils.extractMavenInfo(containingFile.path) ?: return
            val mavenDep = DependencyUtils.findOrCreateMavenDependency(
                pft.mavenDependencies,
                mavenInfo.groupId,
                mavenInfo.artifactId,
                mavenInfo.version
            )
            val psiFile = PsiManager.getInstance(project).findFile(containingFile)
            val packageName = getPackageName(psiFile)
            val pkgDep = DependencyUtils.findOrCreatePackage(mavenDep.packages, packageName)
            val projFile = DependencyUtils.findOrCreateProjectFile(pkgDep.files, containingFile, project, true)
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
            // 如果是 whole，需要把 whole 改为 false，并添加除了要移除的类之外的所有类
            whole = false
            classes.clear()
            
            psiFile.let {
                PsiTreeUtil.findChildrenOfType(it, PsiClass::class.java).forEach { psiClass ->
                    // 检查当前类是否在要移除的列表中
                    val shouldKeep = !classesToRemove.any { it.className == psiClass.name }
                    
                    if (shouldKeep) {
                        classes.add(ProjectClass(
                            className = psiClass.name ?: "",
                            psiClass = psiClass,
                            methods = mutableListOf(),
                            whole = true
                        ))
                    }
                }
            }
        } else {
            // 如果不是 whole，直接从 classes 列表中移除指定的类
            classes.removeAll { projectClass ->
                classesToRemove.any { it.className == projectClass.className }
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
            // 如果是 whole，需要把 whole 改为 false，并添加除了要移除的方法之外的所有方法
            whole = false
            methods.clear()
            psiClass.methods.forEach { method ->
                // 检查当前方法是否在要移除的列表中
                val shouldKeep = !methodsToRemove.any {
                    it.methodName == method.name &&
                            it.parameterTypes == method.parameterList.parameters.map { p -> p.type.canonicalText }
                }

                if (shouldKeep) {
                    methods.add(ProjectMethod(
                        methodName = method.name,
                        parameterTypes = method.parameterList.parameters.map { it.type.canonicalText },
                        psiMethod = method
                    ))
                }
            }
        } else {
            // 如果不是 whole，直接从 methods 列表中移除指定的方法
            methods.removeAll { method ->
                methodsToRemove.any {
                    it.methodName == method.methodName &&
                            it.parameterTypes == method.parameterTypes
                }
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
