package com.github.zjh7890.gpttools.settings

import com.github.zjh7890.gpttools.settings.template.CodeTemplateApplicationSettings
import com.github.zjh7890.gpttools.settings.template.CodeTemplateApplicationSettingsService
import com.github.zjh7890.gpttools.settings.template.TemplateSettingUi
import com.github.zjh7890.gpttools.settings.llmSetting.LLMSettingUi
import com.github.zjh7890.gpttools.settings.llmSetting.LLMSettingsState
import com.intellij.openapi.project.Project
import javax.swing.JTabbedPane

class GptToolConfigUi(
    val project: Project,
    val templateSettings: CodeTemplateApplicationSettingsService,
    val gptToolsConfigurable: GptToolsConfigurable
) {
    val panel = JTabbedPane()
    private val llmSettingUi = LLMSettingUi()
    private val templateSettingUi = TemplateSettingUi(project, templateSettings, gptToolsConfigurable)

    init {
        panel.addTab("LLM Settings", llmSettingUi.component)
        panel.addTab("Templates", templateSettingUi.panel)
    }

    fun isModified(templateSetting: CodeTemplateApplicationSettings, llmSetting: LLMSettingsState): Boolean {
        return llmSettingUi.isModified(llmSetting) || 
               templateSettingUi.isModified(templateSetting)
    }

    fun resetFrom(templateSetting: CodeTemplateApplicationSettings, llmSetting: LLMSettingsState) {
        llmSettingUi.reset(llmSetting)
        templateSettingUi.resetFrom(templateSetting)
    }

    fun applyTo(templateSetting: CodeTemplateApplicationSettings, llmSetting: LLMSettingsState) {
        llmSettingUi.apply(llmSetting)
        templateSettingUi.applyTo(templateSetting)
    }
}