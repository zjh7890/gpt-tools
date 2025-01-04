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
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon

class AddClassAction : AnAction(
    "Analyzer - add class",
    "Add the current class to root classes and show the panel",
    GptToolsIcon.PRIMARY
), Iconable {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)

        if (project != null && editor != null && psiFile != null) {
            val offset = editor.caretModel.offset
            val element = psiFile.findElementAt(offset)
            val clazz = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)

            if (clazz != null) {
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("GptFileTree")
                toolWindow?.show()
                val fileTreeListPanel = toolWindow?.contentManager?.getContent(0)?.component as? FileTreeListPanel
                fileTreeListPanel?.addClass(clazz, true)
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