package com.github.zjh7890.gpttools

import com.github.zjh7890.gpttools.llm.ChatMessage
import com.github.zjh7890.gpttools.llm.LlmConfig
import com.github.zjh7890.gpttools.llm.impl.OpenAILikeProvider
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole

/**
 * @Date: 2024/9/22 11:01
 */
suspend fun main() {
    // 调用 OpenAILikeProvider
    val llm = OpenAILikeProvider()
    println("hhh")
    // 调用 stream
    val messages = listOf("Hello, world!")
    val llmConfig = LlmConfig(
        apiBase = "https://api.gptsapi.net/v1/chat/completions",
        apiKey = "",
        model = "o1-mini",
        temperature = 1.0,
        stream = true,
        maxTokens = null
    )

    val result = llm.call(mutableListOf(ChatMessage(
        ChatRole.user,
        """

        """.trimIndent())), llmConfig)
    result.collect {
        println(it)
    }
}
