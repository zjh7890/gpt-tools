package com.github.zjh7890.gpttools.llms

import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface LLMProvider {
    val defaultTimeout: Long get() = 600

    @Deprecated("Use stream instead")
    fun prompt(promptText: String): String

    @OptIn(ExperimentalCoroutinesApi::class)
    fun stream(promptText: String, systemPrompt: String, keepHistory: Boolean = true): Flow<String> {
        return callbackFlow {
            val prompt = prompt(promptText)
            trySend(prompt)

            awaitClose()
        }
    }

    fun clearMessage() {

    }

    fun appendLocalMessage(msg: String, role: ChatRole) {}
}
