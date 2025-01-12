package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.services.SessionManager
import com.github.zjh7890.gpttools.utils.ChatUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiMethod

class AddMethodToSessionAction : AnAction("Add Method to Session") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val psiMethod = e.getData(CommonDataKeys.PSI_ELEMENT) as? PsiMethod

        if (project == null || psiMethod == null) {
            Messages.showErrorDialog(project, "No method selected.", "Error")
            return
        }

        ChatUtils.activateToolWindowRun(project) { panel, service ->
            SessionManager.getInstance(project).addClassAndMethodToCurrentSession(psiMethod)
        }
    }
}