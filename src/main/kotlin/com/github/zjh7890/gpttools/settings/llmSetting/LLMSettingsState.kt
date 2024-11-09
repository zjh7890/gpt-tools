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
    name = "com.github.zjh7890.gpttools.settings.llmSetting.ShireSettingsState",
    storages = [Storage("ShireSettings.xml")]
)
class LLMSettingsState : PersistentStateComponent<LLMSettingsState> {
    var settings: MutableList<LLMSetting> = mutableListOf(
        LLMSetting()
    )

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

    @Synchronized
    override fun loadState(state: LLMSettingsState) = XmlSerializerUtil.copyBean(state, this)

    /**
     * 获取默认的配置项
     * @return 默认的 [LLMSetting]，如果没有设置默认项则返回第一项，列表为空时返回 null
     */
    fun getDefaultSetting(): LLMSetting? {
        return settings.find { it.isDefault } ?: settings.firstOrNull()
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
            return LlmConfig(
                title = defaultSetting.modelName,
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

    enum class ResponseType {
        SSE,
        JSON
    }

    // 定义监听器接口
    interface SettingsChangeListener {
        fun onSettingsChanged()
    }
}
