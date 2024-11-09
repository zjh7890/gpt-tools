package com.github.zjh7890.gpttools.actions

import com.fasterxml.jackson.core.type.TypeReference
import com.github.zjh7890.gpttools.settings.template.CodeTemplateApplicationSettingsService
import com.github.zjh7890.gpttools.settings.template.PromptTemplate
import com.github.zjh7890.gpttools.utils.GptToolsIcon
import com.github.zjh7890.gpttools.utils.JsonUtils
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import javax.swing.Icon

class TemplatePromptGroup : ActionGroup(), Iconable {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        if (e == null) {
            return emptyArray()
        }
        val project = e.project
        val editor = e.getData(LangDataKeys.EDITOR)
        if (project == null) {
            return emptyArray()
        }

        val settingsService = CodeTemplateApplicationSettingsService.instance
        return JsonUtils.parse(settingsService.state.templates, object : TypeReference<List<PromptTemplate>>() {}).map { template ->
            createActionForTemplate(template, project)
        }.filterNotNull().toTypedArray()
    }

    private fun createActionForTemplate(template: PromptTemplate, project: Project): AnAction? {
        if (template.key == "ClassFinderAction") {
            return ClassFinderAction(template)
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
        else if (template.key == "ServiceImplAction") {
            return ServiceImplAction(template)
        }
        else {
            println("Unsupported template key: ${template.key}")
            return null
        }
    }

    override fun isDumbAware(): Boolean = true
    override fun getIcon(flags: Int): Icon {
        return GptToolsIcon.PRIMARY
    }
}