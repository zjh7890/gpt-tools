package com.github.zjh7890.gpttools.llm

import com.github.zjh7890.gpttools.llm.impl.OpenAILikeProvider
import com.github.zjh7890.gpttools.llm.impl.AzureAIProvider
import com.github.zjh7890.gpttools.services.ChatSession
import com.github.zjh7890.gpttools.settings.llmSetting.Provider
import com.github.zjh7890.gpttools.utils.FileUtil
import kotlinx.coroutines.flow.Flow

/**
 * Interface for providing LLM (Language Model) services.
 */
interface LlmProvider {
    /**
     * Streams chat completion responses from the service.
     *
     * @param messages The list of chat messages.
     * @param llmConfig The configuration for the LLM.
     * @return A Flow of String values representing the chat completion responses.
     */
    fun stream(messages: MutableList<ChatMessage>, llmConfig: LlmConfig): Flow<String>

    companion object {
        fun getProvider(llmConfig: LlmConfig): LlmProvider {
            return when (llmConfig.provider) {
                Provider.OpenAI -> OpenAILikeProvider()
                Provider.Azure -> AzureAIProvider()
            }
        }

        fun stream(session: ChatSession, llmConfig: LlmConfig): Flow<String> {
            val provider = getProvider(llmConfig)
            val messages = session.messages.map { ChatMessage(it.role, it.content) }.toMutableList()
            return provider.stream(messages, llmConfig)
        }

        fun stream(messages: MutableList<ChatMessage>, llmConfig: LlmConfig): Flow<String> {
            val provider = getProvider(llmConfig)
            return provider.stream(messages, llmConfig)
        }
    }
}