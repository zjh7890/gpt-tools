package com.github.zjh7890.gpttools.settings.llmSetting

import com.intellij.openapi.options.ConfigurableBase

class LlmSettingsConfigurable @JvmOverloads constructor(
    private val settings: ShireSettingsState = ShireSettingsState.getInstance()
) : ConfigurableBase<ShireSettingUi, ShireSettingsState>(
    "com.gpttools.shire.settings",
    "gpt-tools Settings",
    "com.gpttools.shire.settings"
) {
    override fun getSettings(): ShireSettingsState {
        return ShireSettingsState.getInstance()
    }

    override fun createUi(): ShireSettingUi {
        return ShireSettingUi()
    }
}