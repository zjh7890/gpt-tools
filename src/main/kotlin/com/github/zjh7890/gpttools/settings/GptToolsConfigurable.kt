package com.github.zjh7890.gpttools.settings

import com.github.zjh7890.gpttools.settings.template.CodeTemplateApplicationSettingsService
import com.github.zjh7890.gpttools.settings.llmSetting.LLMSettingsState
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class GptToolsConfigurable(private val project: Project) : Configurable {
    private var gptToolConfigUi: GptToolConfigUi? = null
    private val templateSettings: CodeTemplateApplicationSettingsService =
        CodeTemplateApplicationSettingsService.instance
    private val llmSettings: LLMSettingsState = LLMSettingsState.getInstance()

    override fun createComponent(): JComponent? {
        gptToolConfigUi = GptToolConfigUi(project, templateSettings, this)
        return gptToolConfigUi?.panel
    }

    override fun isModified(): Boolean = gptToolConfigUi?.isModified(templateSettings.state, llmSettings) ?: false
    
    override fun apply() {
        gptToolConfigUi?.applyTo(templateSettings.state, llmSettings)
    }
    
    override fun reset() {
        gptToolConfigUi?.resetFrom(templateSettings.state, llmSettings)
    }
    
    override fun getDisplayName(): String = "gpt-tools"
    
    override fun disposeUIResources() {
        gptToolConfigUi = null
    }
}
