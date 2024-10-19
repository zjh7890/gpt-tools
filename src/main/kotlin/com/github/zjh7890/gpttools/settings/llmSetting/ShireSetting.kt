package com.github.zjh7890.gpttools.settings.llmSetting

data class ShireSetting(
    var temperature: Double = 0.0,

    // OpenAI 相关字段
    var apiHost: String = "https://api.openai.com/v1/chat/completions",
    var apiToken: String = "",
    var modelName: String = "",

    // Azure 相关字段
    var azureEndpoint: String = "https://{deploymentName}.openai.azure.com",
    var azureApiKey: String = "",
    var azureModel: String = "",

    var responseType: ShireSettingsState.ResponseType = ShireSettingsState.ResponseType.SSE,
    var responseFormat: String = "\$.choices[0].delta.content",
    var isDefault: Boolean = false, // 新增字段，标记为默认配置

    // 新增 provider 字段
    var provider: Provider = Provider.OpenAI
)