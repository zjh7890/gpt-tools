package com.github.zjh7890.gpttools.settings

import com.github.zjh7890.gpttools.settings.llmSetting.LLMSettingUi
import com.github.zjh7890.gpttools.settings.llmSetting.LLMSettingsState
import com.github.zjh7890.gpttools.settings.other.OtherSettingUi
import com.github.zjh7890.gpttools.settings.other.OtherSettingsState
import com.github.zjh7890.gpttools.settings.template.CodeTemplateApplicationSettings
import com.github.zjh7890.gpttools.settings.template.CodeTemplateApplicationSettingsService
import com.github.zjh7890.gpttools.settings.embedTemplate.EmbedTemplateSettingUi
import com.github.zjh7890.gpttools.settings.template.TemplateSettingUi
import com.intellij.openapi.application.ApplicationInfo
import javax.swing.JTabbedPane

class GptToolConfigUi(
    val templateSettings: CodeTemplateApplicationSettingsService,
    val gptToolsConfigurable: GptToolsConfigurable
) {
    val panel = JTabbedPane()
    private val llmSettingUi = LLMSettingUi()
    private val templateSettingUi = TemplateSettingUi(templateSettings, gptToolsConfigurable)
    private val embedTemplateUi = EmbedTemplateSettingUi()
    private val otherSettingUi = OtherSettingUi()

    init {
        panel.addTab("LLM Settings", llmSettingUi.component)
        panel.addTab("Template Prompt", templateSettingUi.panel)
        panel.addTab("Embed Template", embedTemplateUi.panel)
        if (ApplicationInfo.getInstance().versionName.contains("IDEA")) {
            panel.addTab("Others", otherSettingUi.component)
        }
    }

    fun isModified(templateSetting: CodeTemplateApplicationSettings, 
                   llmSetting: LLMSettingsState,
                   otherSetting: OtherSettingsState): Boolean {
        return llmSettingUi.isModified(llmSetting) || 
               templateSettingUi.isModified(templateSetting) ||
               otherSettingUi.isModified(otherSetting) ||
               embedTemplateUi.isModified()
    }

    fun resetFrom(templateSetting: CodeTemplateApplicationSettings, 
                  llmSetting: LLMSettingsState,
                  otherSetting: OtherSettingsState) {
        llmSettingUi.reset(llmSetting)
        templateSettingUi.resetFrom(templateSetting)
        otherSettingUi.reset(otherSetting)
        embedTemplateUi.resetFrom()
    }

    fun applyTo(templateSetting: CodeTemplateApplicationSettings, 
                llmSetting: LLMSettingsState,
                otherSetting: OtherSettingsState) {
        llmSettingUi.apply(llmSetting)
        templateSettingUi.applyTo(templateSetting)
        otherSettingUi.apply(otherSetting)
        embedTemplateUi.applyTo()
    }
}