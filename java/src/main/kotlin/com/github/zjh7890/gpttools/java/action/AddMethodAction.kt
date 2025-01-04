package com.github.zjh7890.gpttools.java.action

import com.github.zjh7890.gpttools.settings.other.OtherSettingsState
import com.github.zjh7890.gpttools.toolWindow.treePanel.FileTreeListPanel
import com.github.zjh7890.gpttools.utils.GptToolsIcon
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon

class AddMethodAction : AnAction(
    "Analyzer - add method",
    "Add the current method to root classes and show the panel",
    GptToolsIcon.PRIMARY
), Iconable {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)

        if (project != null && editor != null && psiFile != null) {
            val offset = editor.caretModel.offset
            val element = psiFile.findElementAt(offset)
            val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
            val clazz = method?.containingClass

            if (method != null && clazz != null) {
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("GptFileTree")
                toolWindow?.show()
                val fileTreeListPanel = toolWindow?.contentManager?.getContent(0)?.component as? FileTreeListPanel
                fileTreeListPanel?.addMethod(clazz, method)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val settings = OtherSettingsState.getInstance()
        e.presentation.isVisible = settings.showAddClassAction
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun getIcon(flags: Int): Icon {
        return GptToolsIcon.PRIMARY
    }
}