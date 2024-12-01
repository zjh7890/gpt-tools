package com.github.zjh7890.gpttools.actions

import com.fasterxml.jackson.core.type.TypeReference
import com.github.zjh7890.gpttools.settings.template.CodeTemplateApplicationSettingsService
import com.github.zjh7890.gpttools.settings.template.PromptTemplate
import com.github.zjh7890.gpttools.utils.GptToolsIcon
import com.github.zjh7890.gpttools.utils.JsonUtils
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.Iconable
import javax.swing.Icon

class TemplatePromptGroup : ActionGroup(), Iconable {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        if (e == null) {
            return emptyArray()
        }
        e.project ?: return emptyArray()

        val settingsService = CodeTemplateApplicationSettingsService.instance
        return JsonUtils.parse(settingsService.state.templates, object : TypeReference<List<PromptTemplate>>() {})
            .filter { it.showInEditorPopupMenu }
            .map { template -> CommonTemplateAction(template) }
            .toTypedArray()
    }

    override fun isDumbAware(): Boolean = true
    override fun getIcon(flags: Int): Icon {
        return GptToolsIcon.PRIMARY
    }
}