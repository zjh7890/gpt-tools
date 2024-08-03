package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.toolWindow.treePanel.FileTreeListPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager

class AddRecursiveFileAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        val virtualFile: VirtualFile? = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE)
        if (project != null && virtualFile != null) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("FileTreeListToolWindow")
            toolWindow?.show {
                ApplicationManager.getApplication().executeOnPooledThread {
                    ApplicationManager.getApplication().runReadAction {
                        val fileTreeListPanel = toolWindow.contentManager.getContent(0)?.component as? FileTreeListPanel
                        fileTreeListPanel?.addFileRecursively(virtualFile, project)
                    }
                }
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}