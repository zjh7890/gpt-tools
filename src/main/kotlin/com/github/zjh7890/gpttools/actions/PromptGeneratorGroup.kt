package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.settings.actionPrompt.CodeTemplateApplicationSettingsService
import com.github.zjh7890.gpttools.settings.actionPrompt.PromptTemplate
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project

class PromptGeneratorGroup : ActionGroup() {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        if (e == null) {
            return emptyArray()
        }
        val project = e.project
        val editor = e.getData(LangDataKeys.EDITOR)
        if (project == null) {
            return emptyArray()
        }

        val settingsService = CodeTemplateApplicationSettingsService.getInstance()
        return settingsService.state.templates.map { template ->
            createActionForTemplate(template, project)
        }.filterNotNull().toTypedArray()
    }

    private fun createActionForTemplate(template: PromptTemplate, project: Project): AnAction? {
        if (template.key == "ClassFinderAction") {
            return ClassFinderAction(template)
        }
        else if (template.key == "DiffAction") {
            return DiffAction(template)
        }
        else if (template.key == "FileTestAction") {
            return FileTestAction(template)
        }
        else if (template.key == "GenerateMethodTestAction") {
            return GenerateMethodTestAction(template)
        }
        else if (template.key == "GenerateRpcAction") {
            return GenerateRpcAction(template)
        }
        else if (template.key == "GenJsonAction") {
            return GenJsonAction(template)
        }
        else if (template.key == "StructConverterCodeGenAction") {
            return StructConverterCodeGenAction(template)
        }
        else if (template.key == "CodeReviewPromptAction") {
            return CodeReviewPromptAction(template)
        }
        else {
            IllegalArgumentException("Unsupported template key: ${template.key}").printStackTrace()
            return null
        }
    }

    override fun isDumbAware(): Boolean = true
}