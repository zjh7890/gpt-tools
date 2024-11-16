package com.github.zjh7890.gpttools.llm.impl

import com.azure.ai.openai.OpenAIClient
import com.azure.ai.openai.OpenAIClientBuilder
import com.azure.ai.openai.models.ChatCompletionsOptions
import com.azure.ai.openai.models.ChatRequestAssistantMessage
import com.azure.ai.openai.models.ChatRequestSystemMessage
import com.azure.ai.openai.models.ChatRequestUserMessage
import com.azure.core.credential.AzureKeyCredential
import com.github.zjh7890.gpttools.llm.ChatMessage
import com.github.zjh7890.gpttools.llm.LlmConfig
import com.github.zjh7890.gpttools.llm.LlmProvider
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AzureAIProvider : LlmProvider {

    private val logger = org.slf4j.LoggerFactory.getLogger(AzureAIProvider::class.java)

    override fun call(messages: MutableList<ChatMessage>, llmConfig: LlmConfig): Flow<String> = flow {
        try {
            // 构建 Azure OpenAI 客户端
            val client: OpenAIClient = OpenAIClientBuilder()
                .endpoint(llmConfig.azureEndpoint)
                .credential(AzureKeyCredential(llmConfig.azureApiKey))
                .buildClient()

            // 转换 ShireSetting.ChatMessage 到 Azure 的 ChatRequestMessage
            val azureMessages = messages.map { chatMessage ->
                when (chatMessage.role) {
                    ChatRole.system -> ChatRequestSystemMessage(chatMessage.content)
                    ChatRole.user -> ChatRequestUserMessage(chatMessage.content)
                    ChatRole.assistant -> ChatRequestAssistantMessage(chatMessage.content)
                }
            }

            // 构建 ChatCompletionsOptions
            val chatCompletionsOptions = ChatCompletionsOptions(azureMessages)
                .setTemperature(llmConfig.temperature)
                .setStream(true) // 启用流式响应

            // 发送请求并处理响应
            client.getChatCompletionsStream(llmConfig.model, chatCompletionsOptions).forEach { chatCompletions ->
                if (chatCompletions.choices.isNullOrEmpty()) {
                    return@forEach
                }
                val delta = chatCompletions.choices[0].delta
                delta.content?.let { content ->
                    emit(content)
                }
            }
        } catch (e: Exception) {
            logger.error("Error in AzureAIProvider stream: ${e.message}", e)
            emit("Error: ${e.message}")
        }
    }
}