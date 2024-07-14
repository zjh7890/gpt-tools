package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.settings.actionPrompt.CodeTemplateApplicationSettingsService
import com.github.zjh7890.gpttools.settings.actionPrompt.PromptTemplate
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilBase

class PromptActionsProvider : ActionGroup() {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        if (e == null) {
            return emptyArray()
        }
        val project = e.project
        val editor = e.getData(LangDataKeys.EDITOR)
        if (project == null || editor == null) {
            return emptyArray()
        }

        val settingsService = CodeTemplateApplicationSettingsService.getInstance()
        return emptyArray()
//        return settingsService.state.templates.values.map { template ->
//            createActionForTemplate(template, project, editor)
//        }.toTypedArray()
    }

    private fun createActionForTemplate(template: PromptTemplate, project: Project, editor: Editor): AnAction {
        return object : AnAction(template.desc) {
            override fun actionPerformed(e: AnActionEvent) {
                // 这里可以插入具体触发模板内容的逻辑
                val file: PsiFile? = PsiUtilBase.getPsiFileInEditor(editor, project)
                val psiClass: PsiClass? = file?.let { PsiTreeUtil.getParentOfType(it.findElementAt(editor.caretModel.offset), PsiClass::class.java) }
                psiClass?.let {
                    createDelegateMethodAction(it, template.key, template.value, project, editor)
                }
            }
        }
    }

    private fun createDelegateMethodAction(psiClass: PsiClass, templateName: String, templateContent: String, project: Project, editor: Editor) {
        // 填充原有的方法实现，可能需要根据具体情况调整
    }

    override fun isDumbAware(): Boolean = true
}