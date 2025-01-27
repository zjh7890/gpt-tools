package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.llm.ChatMessage
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.treePanel.ClassDependencyInfo
import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiClass
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
最新的上下文信息：
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


