package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.toolWindow.treePanel.FileTreeListPanel
import com.github.zjh7890.gpttools.utils.GptToolsIcon
import com.github.zjh7890.gpttools.settings.other.OtherSettingsState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.Icon

class AddFileAction : AnAction(), Iconable {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        val virtualFile: VirtualFile? = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE)
        if (project != null && virtualFile != null) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("GptToolsContextToolWindow")
            val fileTreeListPanel = toolWindow?.contentManager?.getContent(0)?.component as? FileTreeListPanel
            fileTreeListPanel?.addFile(virtualFile)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val settings = OtherSettingsState.getInstance()
        e.presentation.isVisible = settings.showAddFileAction
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun getIcon(flags: Int): Icon {
        return GptToolsIcon.PRIMARY
    }
}
