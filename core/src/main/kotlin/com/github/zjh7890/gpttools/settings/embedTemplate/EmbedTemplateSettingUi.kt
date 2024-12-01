package com.github.zjh7890.gpttools.settings.embedTemplate

import com.github.zjh7890.gpttools.settings.template.EnhancedEditorDialog
import com.github.zjh7890.gpttools.settings.template.PromptTemplate
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.*

class EmbedTemplateSettingUi {
    val panel: JPanel
    // 缓存当前值
    private var currentCodeReviewTemplate: String
    private var currentFixThisTemplate: String

    init {
        // 初始化缓存值
        currentCodeReviewTemplate = EmbedTemplateSettings.instance.state.codeReviewTemplate
        currentFixThisTemplate = EmbedTemplateSettings.instance.state.fixThisTemplate

        val codeReviewButton = createButton("Code Review Template") { 
            showEditorDialog(
                "Edit Code Review Template",
                currentCodeReviewTemplate  // 使用缓存值
            )?.let { newContent ->
                currentCodeReviewTemplate = newContent  // 更新缓存
            }
        }

        val fixThisButton = createButton("Fix This Template") {
            showEditorDialog(
                "Edit Fix This Template",
                currentFixThisTemplate  // 使用缓存值
            )?.let { newContent ->
                currentFixThisTemplate = newContent  // 更新缓存
            }
        }

        // 使用 FormBuilder 构建 UI
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Code Review Template:"), codeReviewButton)
            .addVerticalGap(10)
            .addLabeledComponent(JBLabel("Fix Error Template:"), fixThisButton)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun createButton(text: String, onClick: () -> Unit): JButton {
        return JButton("Open Editor").apply {
            addActionListener { onClick() }
        }
    }

    private fun showEditorDialog(title: String, content: String): String? {
        val template = PromptTemplate(content)
        val editorDialog = EnhancedEditorDialog(template)
        if (editorDialog.showAndGet()) {
            return template.value
        }
        return null
    }

    fun isModified(): Boolean {
        val settings = EmbedTemplateSettings.instance.state
        return settings.codeReviewTemplate != currentCodeReviewTemplate ||
               settings.fixThisTemplate != currentFixThisTemplate
    }

    fun resetFrom() {
        // 重置缓存值为当前设置值
        currentCodeReviewTemplate = EmbedTemplateSettings.instance.state.codeReviewTemplate
        currentFixThisTemplate = EmbedTemplateSettings.instance.state.fixThisTemplate
    }

    fun applyTo() {
        // 应用缓存值到设置
        EmbedTemplateSettings.instance.state.codeReviewTemplate = currentCodeReviewTemplate
        EmbedTemplateSettings.instance.state.fixThisTemplate = currentFixThisTemplate
    }
}