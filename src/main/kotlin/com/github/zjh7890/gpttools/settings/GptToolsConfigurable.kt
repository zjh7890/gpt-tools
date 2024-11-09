package com.github.zjh7890.gpttools.settings

import com.github.zjh7890.gpttools.settings.template.CodeTemplateApplicationSettingsService
import com.github.zjh7890.gpttools.settings.llmSetting.LLMSettingsState
import com.github.zjh7890.gpttools.settings.other.OtherSettingsState
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class GptToolsConfigurable() : Configurable {
    private var gptToolConfigUi: GptToolConfigUi? = null
    private val templateSettings: CodeTemplateApplicationSettingsService =
        CodeTemplateApplicationSettingsService.instance
    private val llmSettings: LLMSettingsState = LLMSettingsState.getInstance()
    private val otherSettings: OtherSettingsState = OtherSettingsState.getInstance()

    override fun createComponent(): JComponent? {
        gptToolConfigUi = GptToolConfigUi(templateSettings, this)
        return gptToolConfigUi?.panel
    }

    override fun isModified(): Boolean = 
        gptToolConfigUi?.isModified(templateSettings.state, llmSettings, otherSettings) ?: false
    
    override fun apply() {
        gptToolConfigUi?.applyTo(templateSettings.state, llmSettings, otherSettings)
    }
    
    override fun reset() {
        gptToolConfigUi?.resetFrom(templateSettings.state, llmSettings, otherSettings)
    }
    
    override fun getDisplayName(): String = "gpt-tools"
    
    override fun disposeUIResources() {
        gptToolConfigUi = null
    }
}
