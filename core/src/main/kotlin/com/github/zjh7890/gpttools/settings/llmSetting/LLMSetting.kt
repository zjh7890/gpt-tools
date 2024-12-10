package com.github.zjh7890.gpttools.settings.llmSetting
data class LLMSetting(
    var name: String = "claude-3.5-sonnet - Trial",
    var temperature: Double = 0.0,
    // OpenAI 相关字段
    var apiHost: String = "https://api.zyai.online/v1/chat/completions",
    var apiToken: String = "sk-tOedSsr00qxzCyUUF672C2E0850f4483Bd82A1625bC53379",
    var modelName: String = "claude-3-5-sonnet-20241022",
    // Azure 相关字段
    var azureEndpoint: String = "https://{deploymentName}.openai.azure.com",
    var azureApiKey: String = "",
    var azureModel: String = "",
    var stream: Boolean = true,
    var isDefault: Boolean = false,
    // 新增 provider 字段
    var provider: Provider = Provider.OpenAILike
)