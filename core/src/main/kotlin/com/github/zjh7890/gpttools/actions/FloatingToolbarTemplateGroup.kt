package com.github.zjh7890.gpttools.actions

import com.fasterxml.jackson.core.type.TypeReference
import com.github.zjh7890.gpttools.settings.template.CodeTemplateApplicationSettingsService
import com.github.zjh7890.gpttools.settings.template.PromptTemplate
import com.github.zjh7890.gpttools.utils.ChatUtils
import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.JsonUtils
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class FloatingToolbarTemplateGroup : ActionGroup("Floating Toolbar Actions", true), DumbAware {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        if (e == null) {
            return emptyArray()
        }
        e.project ?: return emptyArray()

        val settingsService = CodeTemplateApplicationSettingsService.instance
        return JsonUtils.parse(settingsService.state.templates, object : TypeReference<List<PromptTemplate>>() {})
            .filter { it.showInFloatingToolBar }
            .map { template -> CommonTemplateAction(template) }
            .toTypedArray()
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabled = hasSelection
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}