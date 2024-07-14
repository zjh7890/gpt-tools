package com.github.zjh7890.gpttools.settings.llmSettings

import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nullable
import javax.swing.JComponent

class GptToolSettingsConfigurable : Configurable {
    private val component: LLMSettingComponent = LLMSettingComponent(GptToolSettings.getInstance())

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String = "gpt-tools"

    override fun apply() = component.exportSettings(GptToolSettings.getInstance())

    override fun reset() = component.applySettings(GptToolSettings.getInstance())
    override fun getPreferredFocusedComponent(): JComponent? = null

    @Nullable
    override fun createComponent(): JComponent = component.panel

    override fun isModified(): Boolean {
        val settings: GptToolSettings = GptToolSettings.getInstance()
        return component.isModified(settings)
    }
}
