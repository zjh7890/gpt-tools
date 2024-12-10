package com.github.zjh7890.gpttools.java.action

import com.github.zjh7890.gpttools.settings.other.OtherSettingsState
import com.github.zjh7890.gpttools.java.toolWindow.treePanel.FileTreeListPanel
import com.github.zjh7890.gpttools.utils.GptToolsIcon
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager

class AddRecursiveFileAction : AnAction("Analyzer - analyze file", "Add the current file to rootFiles and analyze dependencies", GptToolsIcon.PRIMARY) {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        val virtualFile: VirtualFile? = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE)
        if (project != null && virtualFile != null) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("GptFileTree")
            toolWindow?.show {
                ApplicationManager.getApplication().executeOnPooledThread {
                    val fileTreeListPanel = toolWindow.contentManager.getContent(0)?.component as? FileTreeListPanel
                    fileTreeListPanel?.addFileRecursively(virtualFile, project)
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val settings = OtherSettingsState.getInstance()
        e.presentation.isVisible = settings.showAddRecursiveFileAction
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}