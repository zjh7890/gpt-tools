package com.github.zjh7890.gpttools.llm

import com.github.zjh7890.gpttools.settings.llmSetting.Provider

class LlmConfig(
    val title: String = "",
    val provider: Provider = Provider.OpenAILike, // 更新 provider 类型
    val apiBase: String = "https://api.openai.com/v1/chat/completions",
    val stream: Boolean,
    val apiKey: String = "",
    val model: String = "",
    val temperature: Double = 0.0,
    val maxTokens: Int? = null,
    val requestFormat: Map<String, String> = mapOf(),
//    val responseFormat: String = "\$.choices[0].delta.content", // 默认值
    val messageKeys: Map<String, String> = mapOf(),
//    val responseType: LLMSettingsState.ResponseType, // 新增的 responseType 参数

    // 新增 Azure 相关字段
    var azureEndpoint: String = "",
    var azureApiKey: String = "",
    var azureModel: String = ""
)