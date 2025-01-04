package com.github.zjh7890.gpttools.toolWindow.treePanel

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
            private var isAnalyzing = false
            
            override fun actionPerformed(e: AnActionEvent) {
                if (isAnalyzing) {
                    // TODO: 实现终止分析的逻辑
                    isAnalyzing = false
                    e.presentation.icon = AllIcons.Actions.Refresh
                    e.presentation.text = "Rerun"
                    e.presentation.description = "Rerun dependency analysis"
                } else {
                    isAnalyzing = true
                    e.presentation.icon = AllIcons.Actions.Suspend
                    e.presentation.text = "Stop"
                    e.presentation.description = "Stop dependency analysis"
                    
                    val panel = e.project?.let {
                        val toolWindow = ToolWindowManager.getInstance(it).getToolWindow("GptFileTree")
                        toolWindow?.contentManager?.getContent(0)?.component as? FileTreeListPanel
                    }
                    panel?.runAnalysis(e.project!!) {
                        // 分析完成的回调
                        isAnalyzing = false
                        e.presentation.icon = AllIcons.Actions.Refresh
                        e.presentation.text = "Rerun"
                        e.presentation.description = "Rerun dependency analysis"
                    }
                }
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }

        val expandAction = object : AnAction("Expand Selected", "Expand selected nodes recursively", AllIcons.Actions.Expandall) {
            override fun actionPerformed(e: AnActionEvent) {
                panel.expandSelectedNodes()
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = panel.tree.selectionPath != null
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }

        val collapseAction = object : AnAction("Collapse Selected", "Collapse selected nodes recursively", AllIcons.Actions.Collapseall) {
            override fun actionPerformed(e: AnActionEvent) {
                panel.collapseSelectedNodes()
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = panel.tree.selectionPath != null
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }

        toolWindow.setTitleActions(listOf(copyFilesAction, removeAction, expandAction, collapseAction, rerunAction))
    }
}