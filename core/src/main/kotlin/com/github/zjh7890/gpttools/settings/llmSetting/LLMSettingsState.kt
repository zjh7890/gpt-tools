package com.github.zjh7890.gpttools.settings.llmSetting

import com.github.zjh7890.gpttools.llm.LlmConfig
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "com.github.zjh7890.gpttools.settings.llmSetting.LLMSettingsState",
    storages = [Storage("LLMSettingsState.xml")]
)
class LLMSettingsState : PersistentStateComponent<LLMSettingsState> {
    var settings: MutableList<LLMSetting> = mutableListOf()
    
    // 新增字段
    var defaultModelName: String? = null
    var formatCopyModelName: String? = null

    // 添加监听器列表
    private val listeners = mutableListOf<SettingsChangeListener>()

    // 添加监听器的方法
    fun addSettingsChangeListener(listener: SettingsChangeListener) {
        listeners.add(listener)
    }

    // 移除监听器的方法
    fun removeSettingsChangeListener(listener: SettingsChangeListener) {
        listeners.remove(listener)
    }

    // 通知监听器的方法
    fun notifySettingsChanged() {
        listeners.forEach { it.onSettingsChanged() }
    }


    @Synchronized
    override fun getState(): LLMSettingsState = this

    fun getFillSettings(): List<LLMSetting> {
        // 如果配置列表为空，添加默认配置
        if (settings.isEmpty()) {
            settings.add(
                LLMSetting(
                    name = "claude-3.5-sonnet - Trial",
                    temperature = 0.0,
                    apiHost = "https://api.zyai.online/v1/chat/completions",
                    apiToken = "sk-tOedSsr00qxzCyUUF672C2E0850f4483Bd82A1625bC53379",
                    modelName = "claude-3-5-sonnet-20241022",
                    azureEndpoint = "https://{deploymentName}.openai.azure.com",
                    azureApiKey = "",
                    azureModel = "",
                    stream = true,
                    provider = Provider.OpenAILike
                )
            )
        }
        return settings
    }

    @Synchronized
    override fun loadState(state: LLMSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
        
        // 如果配置列表为空，添加默认配置
        if (settings.isEmpty()) {
            settings.add(
                LLMSetting(
                    name = "claude-3.5-sonnet - Trial",
                    temperature = 0.0,
                    apiHost = "https://api.zyai.online/v1/chat/completions",
                    apiToken = "sk-tOedSsr00qxzCyUUF672C2E0850f4483Bd82A1625bC53379",
                    modelName = "claude-3-5-sonnet-20241022",
                    azureEndpoint = "https://{deploymentName}.openai.azure.com",
                    azureApiKey = "",
                    azureModel = "",
                    stream = true,
                    provider = Provider.OpenAILike
                )
            )
        }
        
        // 兼容历史配置
        settings.forEach { setting ->
            if (setting.name.isNullOrEmpty()) {
                setting.name = setting.modelName
            }
        }
    }

    /**
     * 获取默认的配置项
     * @return 默认的 [LLMSetting],如果没有设置默认项则返回第一项,列表为空时返回 null
     */
    fun getDefaultSetting(): LLMSetting? {
        return settings.find { it.name == defaultModelName } 
            ?: settings.firstOrNull()
    }

    /**
     * 获取格式化复制功能使用的配置项
     * @return 用于格式化复制的 [LLMSetting],如果没有设置则返回默认项,列表为空时返回 null
     */
    fun getFormatCopySetting(): LLMSetting? {
        return settings.find { it.name == formatCopyModelName }
            ?: getDefaultSetting()
    }
    
    companion object {
        fun getInstance(): LLMSettingsState {
            return ApplicationManager.getApplication().getService(LLMSettingsState::class.java)
        }

        fun getLlmConfig(): LlmConfig {
            val settings = getInstance()
            val defaultSetting = settings.getDefaultSetting()
            if (defaultSetting == null) {
                throw IllegalStateException("No default setting found")
            }
            return toLlmConfig(defaultSetting)
        }

        fun toLlmConfig(setting: LLMSetting?): LlmConfig {
            val defaultSetting = setting ?: getInstance().getDefaultSetting()
            ?: throw IllegalStateException("No default setting found")
            val title = if (defaultSetting.name.isNullOrEmpty()) defaultSetting.modelName else defaultSetting.name
            return LlmConfig(
                title = title,
                provider = defaultSetting.provider,
                apiKey = if (defaultSetting.provider == Provider.OpenAILike) defaultSetting.apiToken else defaultSetting.azureApiKey,
                model = if (defaultSetting.provider == Provider.OpenAILike) defaultSetting.modelName else defaultSetting.azureModel,
                temperature = defaultSetting.temperature,
                apiBase = if (defaultSetting.provider == Provider.OpenAILike) defaultSetting.apiHost else defaultSetting.azureEndpoint,
                stream = defaultSetting.stream,
                azureEndpoint = defaultSetting.azureEndpoint,
                azureApiKey = defaultSetting.azureApiKey,
                azureModel = defaultSetting.azureModel
            )
        }
    }

    // 定义监听器接口
    interface SettingsChangeListener {
        fun onSettingsChanged()
    }
}
