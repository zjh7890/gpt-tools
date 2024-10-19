package com.github.zjh7890.gpttools

import com.azure.ai.openai.OpenAIClientBuilder
import com.azure.ai.openai.OpenAIServiceVersion
import com.azure.ai.openai.models.ChatCompletionsOptions
import com.azure.ai.openai.models.ChatRequestAssistantMessage
import com.azure.ai.openai.models.ChatRequestSystemMessage
import com.azure.ai.openai.models.ChatRequestUserMessage
import com.azure.core.credential.AzureKeyCredential


fun main() {
    /**
     * @Date: 2024/9/27 12:41
     */
    // 创建 AzureOpenAIClient 客户端
    val client = OpenAIClientBuilder()
        .endpoint("https://openai-fly-jp.openai.azure.com")
        .credential(AzureKeyCredential(""))
        .serviceVersion(OpenAIServiceVersion.V2024_07_01_PREVIEW)
        .buildClient()

    // 创建聊天消息
    val messages = listOf(
        ChatRequestSystemMessage("You are a helpful assistant."),
        ChatRequestUserMessage("Does Azure OpenAI support customer managed keys?"),
        ChatRequestAssistantMessage("Yes, customer managed keys are supported by Azure OpenAI."),
        ChatRequestUserMessage("Do other Azure AI services support this too?")
    )

    val completionsOptions = ChatCompletionsOptions(messages)

    // 发送请求并接收响应
    client.getChatCompletionsStream("GPT-4o", completionsOptions)
        .forEach { chatCompletions ->
            if (chatCompletions.choices.isNullOrEmpty()) {
                return@forEach
            }
            val delta = chatCompletions.choices[0].delta
            delta.role?.let {
                println("Role = $it")
            }
            delta.content?.let { content ->
                print(content)
            }
        }

}



