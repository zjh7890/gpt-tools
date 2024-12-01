package com.github.zjh7890.gpttools.settings.llmSetting
data class LLMSetting(
    var name: String = "",
    var temperature: Double = 0.0,
    // OpenAI 相关字段
    var apiHost: String = "https://api.openai.com/v1/chat/completions",
    var apiToken: String = "",
    var modelName: String = "",
    // Azure 相关字段
    var azureEndpoint: String = "https://{deploymentName}.openai.azure.com",
    var azureApiKey: String = "",
    var azureModel: String = "",
    var stream: Boolean = true,
    var isDefault: Boolean = false,
    // 新增 provider 字段
    var provider: Provider = Provider.OpenAILike
)