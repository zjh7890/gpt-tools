package com.github.zjh7890.gpttools.actions

import com.fasterxml.jackson.core.type.TypeReference
import com.github.zjh7890.gpttools.settings.template.CodeTemplateApplicationSettingsService
import com.github.zjh7890.gpttools.settings.template.PromptTemplate
import com.github.zjh7890.gpttools.utils.JsonUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsDataKeys

class ReviewChangesAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val changes = e.getData(VcsDataKeys.CHANGES)
        
        if (changes.isNullOrEmpty()) {
            Messages.showWarningDialog(
                project,
                "No changes selected",
                "Warning"
            )
            return
        }

        // 从设置中获取模板
        val settingsService = CodeTemplateApplicationSettingsService.instance
        val templates = JsonUtils.parse(
            settingsService.state.templates, 
            object : TypeReference<List<PromptTemplate>>() {}
        )
        
        // 查找 CodeReviewPromptAction 对应的模板
        val promptTemplate = templates.find { it.key == "CodeReviewPromptAction" }!!
        
        val action = CodeReviewPromptAction(promptTemplate)
        action.reviewCode(project, changes)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}