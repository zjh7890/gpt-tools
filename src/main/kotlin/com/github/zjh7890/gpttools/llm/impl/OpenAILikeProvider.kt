package com.github.zjh7890.gpttools.llm.impl

import com.github.zjh7890.gpttools.llm.ChatMessage
import com.github.zjh7890.gpttools.llm.CustomRequest
import com.github.zjh7890.gpttools.llm.LlmConfig
import com.github.zjh7890.gpttools.llm.LlmProvider
import com.github.zjh7890.gpttools.settings.llmSetting.ShireSettingsState
import com.intellij.openapi.diagnostic.logger
import com.github.zjh7890.gpttools.llm.custom.CustomSSEHandler
import com.github.zjh7890.gpttools.llm.custom.appendCustomHeaders
import com.github.zjh7890.gpttools.llm.custom.updateCustomFormat
import com.nfeld.jsonpathkt.JsonPath
import com.nfeld.jsonpathkt.extension.read
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

data class CustomFields(
    val model: String,
    val temperature: Double,
    val maxTokens: Int?,
    val stream: Boolean
)

class OpenAILikeProvider : CustomSSEHandler(), LlmProvider {
    /**
     * Default timeout for the provider.
     * This is used to set the default timeout for the provider.
     * For example, If you want to wait in 10min, you can use:
     * ```Kotlin
     * Duration.ofSeconds(defaultTimeout)
     * ```
     */
    val defaultTimeout: Long get() = 600

    private val timeout = Duration.ofSeconds(defaultTimeout)

    private var client = OkHttpClient()

    override fun call(
        messages: MutableList<ChatMessage>,
        llmConfig: LlmConfig
    ): Flow<String> {
        // 根据 responseType 决定 stream 的值
        val isStream = llmConfig.responseType == ShireSettingsState.ResponseType.SSE

        val requestFormat: String = if (llmConfig.maxTokens != null) {
            """{ "customFields": {"model": "${llmConfig.model}", "temperature": ${llmConfig.temperature}, "max_tokens": ${llmConfig.maxTokens}, "stream": $isStream} }"""
        } else {
            """{ "customFields": {"model": "${llmConfig.model}", "temperature": ${llmConfig.temperature}, "stream": $isStream} }"""
        }

        val customRequest = CustomRequest(messages)
        val requestContent = customRequest.updateCustomFormat(requestFormat)

        val body = requestContent.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val builder = Request.Builder()
        if (llmConfig.apiKey.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer ${llmConfig.apiKey}")
            builder.addHeader("Content-Type", "application/json")
        }
        builder.appendCustomHeaders(requestFormat)

        logger<OpenAILikeProvider>().warn("Requesting form: $requestContent")

        client = client.newBuilder().readTimeout(timeout).build()
        val call = client.newCall(builder.url(llmConfig.apiBase).post(body).build())

        return if (llmConfig.responseType == ShireSettingsState.ResponseType.SSE) {
            streamSSE(call, messages, llmConfig.responseFormat)
        } else {
            val response = call.execute()
            val responseBody: String? = response.body?.string()
            logger<OpenAILikeProvider>().warn("LLM response body non stream: $responseBody")
            val responseContent: String = JsonPath.parse(responseBody)?.read(llmConfig.responseFormat) ?: ""
            kotlinx.coroutines.flow.flow {
                emit(responseContent)
            }
        }
    }
}
