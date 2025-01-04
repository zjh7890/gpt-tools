package com.github.zjh7890.gpttools.toolWindow.context

import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.services.ChatSession
import com.github.zjh7890.gpttools.toolWindow.llmChat.ChatFileTreeListPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.JComponent
import javax.swing.JPanel

class ContextFileToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val panel = ChatFileTreeListPanel(project)
        val content = contentFactory.createContent(panel, "Files", false)
        toolWindow.contentManager.addContent(content)

        // 添加工具窗口操作按钮（如移除节点、复制文件等）
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
                panel.copyAllFiles()
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = panel.tree.selectionPath != null
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

        val switchSessionAction = object : AnAction("Switch Session", "Switch to current IDEA project session", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                val openProjects = ProjectManager.getInstance().openProjects
                
                // 为每个项目获取其当前 ChatSession
                val projectSessions = openProjects.mapNotNull { proj ->
                    val service = ChatCodingService.getInstance(proj)
                    val session = service.getCurrentSession()
                    "${proj.name} - ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(session.startTime))}" to session
                }

                if (projectSessions.isEmpty()) {
                    Messages.showInfoMessage(project, "No available sessions found.", "Switch Session")
                    return
                }

                // 创建选择对话框
                val dialog = object : DialogWrapper(project, true) {
                    private val sessionList = JBList(projectSessions.map { it.first })
                    
                    init {
                        title = "Select Session"
                        init()
                    }

                    override fun createCenterPanel(): JComponent {
                        val panel = JPanel(BorderLayout())
                        panel.add(JBScrollPane(sessionList), BorderLayout.CENTER)
                        panel.preferredSize = Dimension(400, 300)
                        return panel
                    }

                    fun getSelectedSession(): ChatSession? {
                        val selectedIndex = sessionList.selectedIndex
                        return if (selectedIndex >= 0) projectSessions[selectedIndex].second else null
                    }
                }

                if (dialog.showAndGet()) {
                    val selectedSession = dialog.getSelectedSession()
                    if (selectedSession != null) {
                        panel.updateFileTree(selectedSession)
                    }
                }
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }

        toolWindow.setTitleActions(listOf(switchSessionAction, copyFilesAction, removeAction, expandAction, collapseAction))
    }

    /**
     * 实现 getPanel 方法，以便其他组件（如 ChatCodingService）能够获取到 ChatFileTreeListPanel
     */
    companion object {
        fun getPanel(project: Project): ChatFileTreeListPanel? {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("ContextFileToolWindow")
            return toolWindow?.contentManager?.getContent(0)?.component as? ChatFileTreeListPanel
        }
    }
}
