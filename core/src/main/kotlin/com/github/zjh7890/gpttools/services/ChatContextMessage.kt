package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import kotlinx.serialization.Serializable

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
