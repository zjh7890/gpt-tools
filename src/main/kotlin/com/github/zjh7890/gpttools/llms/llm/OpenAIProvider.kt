package com.github.zjh7890.gpttools.llms.llm

import com.azure.ai.openai.OpenAIClient
import com.azure.ai.openai.OpenAIClientBuilder
import com.azure.ai.openai.models.*
import com.azure.core.credential.KeyCredential
import com.azure.core.util.CoreUtils
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.zjh7890.gpttools.llms.LLMProvider
import com.github.zjh7890.gpttools.settings.llmSettings.GptToolSettings
import com.github.zjh7890.gpttools.settings.llmSettings.SELECT_CUSTOM_MODEL
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Duration


@Service(Service.Level.PROJECT)
class OpenAIProvider(val project: Project) : LLMProvider {
    private val timeout = Duration.ofSeconds(defaultTimeout)
    private val openAiVersion: String
        get() {
            val model = GptToolSettings.getInstance().openAiModel
            if (model == SELECT_CUSTOM_MODEL) {
                return GptToolSettings.getInstance().customModel
            }
            return model
        }
    private val openAiKey: String
        get() = GptToolSettings.getInstance().openAiKey

    private val maxTokenLength: Int
        get() = GptToolSettings.getInstance().fetchMaxTokenLength()

    private val messages: MutableList<ChatRequestMessage> = ArrayList()
    private var historyMessageLength: Int = 0

    private val client: OpenAIClient
        get() {
            if (openAiKey.isEmpty()) {
                throw IllegalStateException("You LLM server Key is empty")
            }

            val keyCredential = KeyCredential(openAiKey)
            return OpenAIClientBuilder()
                .credential(keyCredential)
                .buildClient()
        }

    override fun clearMessage() {
        messages.clear()
        historyMessageLength = 0
    }

    override fun appendLocalMessage(msg: String, role: ChatRole) {
        when (role) {
            ChatRole.User -> messages.add(ChatRequestUserMessage(msg))
            ChatRole.System -> messages.add(ChatRequestSystemMessage(msg))
            ChatRole.Assistant -> messages.add(ChatRequestAssistantMessage(msg))
        }
    }

    override fun prompt(promptText: String): String {
        val completionRequest = prepareRequest(promptText, "", true)
        val chatCompletions = client.getChatCompletions(openAiVersion, completionRequest)
        val output = chatCompletions.choices.first().message.content
        return output
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun stream(promptText: String, systemPrompt: String, keepHistory: Boolean): Flow<String> {
        if ((!keepHistory)) {
            clearMessage()
        }

        var output = ""
        val completionRequest = prepareRequest(promptText, systemPrompt, keepHistory)

        return callbackFlow {
            try {
                val flow = client.getChatCompletionsStream(openAiVersion, completionRequest)
                var status = 0
                var content = ""
                var toolCallId = ""
                var toolCallAccumulator = ""
                var functionName = ""
                val availableFunctions: Map<String, (Map<String, Any>) -> Any> = mapOf()
                flow.forEach { chatCompletion ->
                    var consumed = false
                    if (!CoreUtils.isNullOrEmpty(chatCompletion.choices)) {
                        val chatChoice = chatCompletion.choices[0]
                        val delta: ChatResponseMessage = chatChoice.delta

                        if (delta.content != null) {
                            // 对应 Python 代码中的 print 和 content 累加
                            output += delta.content
                            trySend(delta.content)
                            content += delta.content
                            status = 1
                            consumed = true
                        }

                        // toolCalls 有内容的处理
                        if (!CoreUtils.isNullOrEmpty(delta.toolCalls)) {
                            if (status == 1) {
//                                messages.add(
//                                    mapOf(
//                                        "role" to "assistant",
//                                        "content" to content
//                                    )
//                                )
                                content = ""
                            }
                            status = 2
                            consumed = true

                            delta.toolCalls.forEach { toolCall ->

                            }
                        }
                        if (!consumed) {
                            println("------- unknown chunk -------   role: ${delta.role}    reason: ${chatChoice.finishReason}")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
//                close(e) // 关闭流并传递异常
            }
            close()
        }
    }

    private fun prepareRequest(promptText: String, systemPrompt: String, keepHistory: Boolean): ChatCompletionsOptions {
        if (systemPrompt.isNotEmpty()) {
            val systemMessage = ChatRequestSystemMessage(systemPrompt)
            messages.add(systemMessage)
        }

        val mapper = ObjectMapper()

        val systemMessage = ChatRequestUserMessage(promptText)

        historyMessageLength += promptText.length
        if (historyMessageLength > maxTokenLength) {
            messages.clear()
        }

        if (promptText != "") {
            messages.add(systemMessage)
        }
        logger.info("messages length: ${messages.size}")

        val options = ChatCompletionsOptions(messages) // 假设构造器可以直接接受消息列表和模型版本
        options.setModel(openAiVersion);
        options.setTemperature(0.0);
        return options
    }

    companion object {
        private val logger: Logger = logger<OpenAIProvider>()
    }
}
