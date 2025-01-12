package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.llm.ChatMessage
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val messages: MutableList<ChatContextMessage> = mutableListOf(),
    val startTime: Long = System.currentTimeMillis(),
    val type: String = "chat",
    var projectFileTrees: MutableList<ProjectFileTree> = mutableListOf(),
    var withFiles: Boolean = true,
    val project: String,
    val projects : MutableList<Project> = mutableListOf()
) {
    // 添加序列化方法
    fun toSerializable(): SerializableChatSession {
        return SerializableChatSession(
            id = id,
            messages = messages,
            startTime = startTime,
            type = type,
            withFiles = withFiles,
            projectFileTrees = projectFileTrees.map { it.toSerializable() }.toMutableList(),
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
```
上下文已失效
```
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
}

data class ProjectFileTree(
    val projectName: String,
    val files: MutableList<VirtualFile> = mutableListOf()
) {
    /**
     * 转换为可序列化的文件树对象
     */
    fun toSerializable(): SerializableProjectFileTree {
        return SerializableProjectFileTree(
            projectName = projectName,
            filePaths = files.map { it.path }
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
            projectFileTrees = projectFileTrees,
            project = projectName,
            projects =  mutableListOf(project)
        )
    }
}

@Serializable
data class SerializableProjectFileTree(
    val projectName: String = "",
    val filePaths: List<String> = emptyList()
) {
    /**
     * 转换为 ProjectFileTree 实例
     */
    fun toProjectFileTree(): ProjectFileTree {
        val files = filePaths.mapNotNull { path ->
            LocalFileSystem.getInstance().findFileByPath(path)
        }.toMutableList()
        return ProjectFileTree(
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
