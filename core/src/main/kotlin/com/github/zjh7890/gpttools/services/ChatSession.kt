package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.llm.ChatMessage
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.treePanel.ClassDependencyInfo
import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val messages: MutableList<ChatContextMessage> = mutableListOf(),
    val startTime: Long = System.currentTimeMillis(),
    val type: String = "chat",
    var projectTrees: MutableList<ProjectTree> = mutableListOf(),
    var withFiles: Boolean = true,
    val project: String,
    val projects: MutableList<Project> = mutableListOf()
) {
    var classGraph: MutableMap<PsiClass, ClassDependencyInfo> = mutableMapOf()

    // 新增方法用于添加类和方法，并指定文件
    fun addClass(projectName: String, fileName: String, className: String, methodName: String, parameterTypes: List<String> = emptyList()) {
        val projectTree = projectTrees.find { it.projectName == projectName }
            ?: ProjectTree(projectName).also { projectTrees.add(it) }

        val projectFile = projectTree.files.find { it.filePath == fileName }
            ?: ProjectFile(fileName).also { projectTree.files.add(it) }

        val projectClass = projectFile.classes.find { it.className == className }
            ?: ProjectClass(className).also { projectFile.classes.add(it) }

        if (!projectClass.methods.any { it.methodName == methodName && it.parameterTypes == parameterTypes }) {
            projectClass.methods.add(ProjectMethod(methodName, parameterTypes))
        }
    }

    // 添加序列化方法
    fun toSerializable(): SerializableChatSession {
        return SerializableChatSession(
            id = id,
            messages = messages,
            startTime = startTime,
            type = type,
            withFiles = withFiles,
            projectFileTrees = projectTrees.map { it.toSerializable() },
            projectName = project
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
            // 处理异常，例如日志记录
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
        val chatMessages: MutableList<ChatMessage>
        // 找到最后一个用户消息的索引
        val lastUserMessageIndex = messages.indexOfLast { it.role == ChatRole.user }

        // 映射每条消息，根据是否有 context 以及是否是最后一个用户消息进行处理
        chatMessages = messages.mapIndexed { index, it ->
            when {
                // 1. 没有 context
                it.context.isBlank() -> ChatMessage(it.role, it.content)

                // 2. 有 context 但不是最后一个用户消息
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
        return chatMessages
    }

    companion object {
        private val logger = logger<ChatCodingService>()
    }

    fun generateClassGraph(project: Project) {
        classGraph.clear()
        projectTrees.forEach { projectTree ->
            projectTree.files.forEach { projectFile ->
                // 获取虚拟文件
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
                                // 在文件中查找指定的类
                                PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
                                    .find { it.name == projectClass.className }
                                    ?.let { psiClass ->
                                        val dependencyInfo = ClassDependencyInfo()
                                        if (projectClass.whole) {
                                            // 如果整个类被标记为 whole，处理所有方法
                                            psiClass.methods.forEach { method ->
                                                dependencyInfo.markMethodUsed(method)
                                            }
                                        } else {
                                            // 只处理指定的方法
                                            psiClass.methods.forEach { method ->
                                                if (projectClass.methods.any { 
                                                    it.methodName == method.name && 
                                                    it.parameterTypes == method.parameterList.parameters.map { param -> 
                                                        param.type.canonicalText 
                                                    }
                                                }) {
                                                    dependencyInfo.markMethodUsed(method)
                                                }
                                            }
                                        }
                                        classGraph[psiClass] = dependencyInfo
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class ProjectTree(
    val projectName: String,
    val files: MutableList<ProjectFile> = mutableListOf()
) {
    /**
     * 转换为可序列化的文件树对象
     */
    fun toSerializable(): SerializableProjectFileTree {
        return SerializableProjectFileTree(
            projectName = projectName,
            files = files.map { it.toSerializable() }
        )
    }
}

@Serializable
data class SerializableChatSession @JvmOverloads constructor(
    val id: String = "",
    val messages: MutableList<ChatContextMessage> = mutableListOf(),
    val startTime: Long = 0L,
    val type: String = "",
    val withFiles: Boolean = true,
    val projectFileTrees: List<SerializableProjectFileTree> = emptyList(),
    val projectName: String = ""
) {
    /**
     * 转换为 ChatSession 实例
     */
    fun toChatSession(project: Project): ChatSession {
        val projectFileTrees = projectFileTrees.map { it.toProjectFileTree() }.toMutableList()
        return ChatSession(
            id = id,
            messages = messages,
            startTime = startTime,
            type = type,
            withFiles = withFiles,
            projectTrees = projectFileTrees,
            project = projectName,
            projects = mutableListOf(project)
        )
    }
}

@Serializable
data class SerializableProjectFileTree(
    val projectName: String = "",
    val files: List<SerializableProjectFile> = emptyList()
) {
    /**
     * 转换为 ProjectFileTree 实例
     */
    fun toProjectFileTree(): ProjectTree {
        val files = files.map { it.toProjectFile() }.toMutableList()
        return ProjectTree(
            projectName = projectName,
            files = files
        )
    }
}

@Serializable
data class ChatContextMessage @JvmOverloads constructor(
    val role: ChatRole = ChatRole.user,
    var content: String = "",
    var context: String = "",
) {
    /**
     * 导出聊天历史为字符串
     */
    fun exportChatHistory(invalidContext: Boolean = false): String {
        // 实现导出逻辑，根据 invalidContext 的值决定是否包含上下文
        return if (invalidContext) {
            content
        } else {
            "$role: $content\nContext: $context\n"
        }
    }

    /**
     * 添加消息到会话中
     */
    fun add(session: ChatSession) {
        session.messages.add(this)
    }
}

@Serializable
data class ProjectClass(
    val className: String = "",
    val methods: MutableList<ProjectMethod> = mutableListOf(),
    val whole: Boolean = false
) {
    fun toSerializable(): SerializableProjectClass {
        return SerializableProjectClass(
            className = className,
            methods = methods.map { it.toSerializable() }
        )
    }
}

@Serializable
data class ProjectMethod(
    val methodName: String = "",
    val parameterTypes: List<String> = emptyList() // 添加参数类型列表
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
    fun toProjectMethod(): ProjectMethod {
        return ProjectMethod(
            methodName = methodName,
            parameterTypes = parameterTypes
        )
    }
}

@Serializable
data class SerializableProjectClass(
    val className: String = "",
    val methods: List<SerializableProjectMethod> = emptyList()
) {
    fun toProjectClass(): ProjectClass {
        val methods = methods.map { it.toProjectMethod() }.toMutableList()
        return ProjectClass(
            className = className,
            methods = methods
        )
    }
}

@Serializable
data class ProjectFile(
    val filePath: String = "",
    val classes: MutableList<ProjectClass> = mutableListOf(),
    val whole: Boolean = false
) {
    fun toSerializable(): SerializableProjectFile {
        return SerializableProjectFile(
            fileName = filePath,
            classes = classes.map { it.toSerializable() }
        )
    }
}

@Serializable
data class SerializableProjectFile(
    val fileName: String = "",
    val classes: List<SerializableProjectClass> = emptyList()
) {
    fun toProjectFile(): ProjectFile {
        val classes = classes.map { it.toProjectClass() }.toMutableList()
        return ProjectFile(
            filePath = fileName,
            classes = classes
        )
    }
}
