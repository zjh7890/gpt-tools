package com.github.zjh7890.gpttools.settings.actionPrompt

import com.intellij.openapi.options.Configurable
import javax.swing.*

class MyPluginConfigurable : Configurable {


    override fun getDisplayName(): String {
        return "My Plugin Settings"
    }

    private var myPanel: JPanel? = null
    private var myKeyTextField: JTextField? = null

    override fun createComponent(): JComponent? {
        myPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        val myKeyLabel = JLabel("My Key:")
        myKeyTextField = JTextField(CodeTemplateApplicationSettingsService.instance.state.templates)
        myPanel?.add(myKeyLabel)
        myPanel?.add(myKeyTextField)

        return myPanel
    }

    override fun isModified(): Boolean {
        return myKeyTextField?.text != CodeTemplateApplicationSettingsService.instance.state.templates
    }

    override fun apply() {
        CodeTemplateApplicationSettingsService.instance.state.templates = myKeyTextField?.text ?: ""
    }

    override fun reset() {
        myKeyTextField?.text = CodeTemplateApplicationSettingsService.instance.state.templates
    }

    override fun disposeUIResources() {
        myPanel = null
        myKeyTextField = null
    }
}