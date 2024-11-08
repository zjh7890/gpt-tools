package com.github.zjh7890.gpttools.toolWindow.treePanel

import com.github.zjh7890.gpttools.settings.other.OtherSettingsState
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class GptToolsContextToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = OtherSettingsState.getInstance().showGptToolsContextWindow

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val panel = FileTreeListPanel(project)
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)

        val removeAction = object : AnAction("Remove Node", "Remove the selected node", AllIcons.Actions.DeleteTag) {
            override fun actionPerformed(e: AnActionEvent) {
                panel.removeSelectedNodes()
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = panel.tree.selectionPath != null
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }

        // New Action to copy files
        val copyFilesAction = object : AnAction("Copy Files", "Copy files from the selected node", AllIcons.Actions.Copy) {
            override fun actionPerformed(e: AnActionEvent) {
                panel.copyAllFiles(e.project!!)
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }

        toolWindow.setTitleActions(listOf(copyFilesAction, removeAction))
    }
}

