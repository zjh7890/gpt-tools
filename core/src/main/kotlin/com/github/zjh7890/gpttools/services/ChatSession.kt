package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.llm.ChatMessage
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.treePanel.ClassDependencyInfo
import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
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

    /**
     * 示例：根据反序列化好的 AppFileTree，遍历所有类，生成依赖图
     */
    fun generateClassGraph(project: Project) {
        classGraph.clear()
        // 遍历所有文件树
        appFileTree.projectFileTrees.forEach { projectTree ->
            // 这里简单处理：无论 projectTree 里的 projectName 是否与当前一致，都用同一个 project
            projectTree.files.forEach { projectFile ->
                // 根据 filePath 找到 VirtualFile
                val virtualFile = project.baseDir.findFileByRelativePath(projectFile.filePath)
                if (virtualFile != null) {
                    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                    if (psiFile != null) {
                        if (projectFile.whole) {
                            // 如果整个文件被标记为 whole，处理文件中的所有类
                            PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java).forEach { psiClass ->
                                val dependencyInfo = ClassDependencyInfo()
                                psiClass.methods.forEach { method ->
                                    dependencyInfo.markMethodUsed(method)
                                }
                                classGraph[psiClass] = dependencyInfo
                            }
                        } else {
                            // 如果不是整个文件，只处理指定的类
                            projectFile.classes.forEach { projectClass ->
                                val foundPsiClass = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
                                    .find { it.name == projectClass.className }
                                    ?: return@forEach // 找不到就跳过

                                val dependencyInfo = ClassDependencyInfo()
                                if (projectClass.whole) {
                                    // 如果整个类被标记为 whole，处理所有方法
                                    foundPsiClass.methods.forEach { method ->
                                        dependencyInfo.markMethodUsed(method)
                                    }
                                } else {
                                    // 只处理指定的方法
                                    foundPsiClass.methods.forEach { method ->
                                        val match = projectClass.methods.any {
                                            it.methodName == method.name &&
                                                    it.parameterTypes == method.parameterList.parameters.map { p -> p.type.canonicalText }
                                        }
                                        if (match) {
                                            dependencyInfo.markMethodUsed(method)
                                        }
                                    }
                                }
                                classGraph[foundPsiClass] = dependencyInfo
                            }
                        }
                    }
                }
            }
        }
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
    val projectName: String = "",    // 原来的 project: Project 改为 projectName
    val files: MutableList<ProjectFile> = mutableListOf()
) {
    fun toSerializable(): SerializableProjectFileTree {
        return SerializableProjectFileTree(
            projectName = projectName,
            files = files.map { it.toSerializable() }
        )
    }
}

@Serializable
data class SerializableProjectFileTree(
    val projectName: String = "",
    val files: List<SerializableProjectFile> = emptyList()
) {
    fun toProjectFileTree(): ProjectFileTree {
        // 获取对应的 Project 实例
        val project = ProjectManager.getInstance().openProjects
            .find { it.name == projectName }
            ?: throw IllegalStateException("Cannot find project with name: $projectName")
        val fileList = files.map { it.toProjectFile(project) }.toMutableList()
        return ProjectFileTree(
            projectName = projectName,
            files = fileList
        )
    }
}

// ----------------------------
// “项目文件”
// ----------------------------
data class ProjectFile(
    val filePath: String = "",            // 相对项目根目录路径
    val virtualFile: VirtualFile,
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
    val classes: List<SerializableProjectClass>? = null,
    val whole: Boolean = false
) {
    fun toProjectFile(project: Project): ProjectFile {
        val vFile = project.baseDir.findFileByRelativePath(filePath)
            ?: throw IllegalStateException("Cannot find filePath=$filePath in project=${project.name}")
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
    val methodName: String,
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
