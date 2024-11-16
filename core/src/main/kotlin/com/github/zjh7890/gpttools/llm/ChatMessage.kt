package com.github.zjh7890.gpttools.llm

import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: ChatRole,
    var content: String,
)

@Serializable
data class CustomRequest(val messages: List<ChatMessage>)
