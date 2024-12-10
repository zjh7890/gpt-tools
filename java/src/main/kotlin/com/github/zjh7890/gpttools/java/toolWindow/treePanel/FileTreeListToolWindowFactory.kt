package com.github.zjh7890.gpttools.java.toolWindow.treePanel

import com.github.zjh7890.gpttools.settings.other.OtherSettingsState
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
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

        val copyFilesAction = object : AnAction("Copy Files", "Copy files from the selected node", AllIcons.Actions.Copy) {
            override fun actionPerformed(e: AnActionEvent) {
                panel.copyAllFiles(e.project!!)
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }

        val rerunAction = object : AnAction("Rerun", "Rerun dependency analysis", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                val panel = e.project?.let {
                    val toolWindow = ToolWindowManager.getInstance(it).getToolWindow("GptFileTree")
                    toolWindow?.contentManager?.getContent(0)?.component as? FileTreeListPanel
                }
                panel?.rerunAnalysis(e.project!!)
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }

        toolWindow.setTitleActions(listOf(copyFilesAction, removeAction, rerunAction))
    }
}