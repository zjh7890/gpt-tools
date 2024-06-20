package com.github.zjh7890.gpttools.toolWindow.chat

import com.github.zjh7890.gpttools.MyBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

class ChatToolWindowFactory : ToolWindowFactory, DumbAware {
    object Util {
        const val id = "Chat"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        contentPanel = ChatToolPanel(toolWindow.disposable, project)
        val content =
            ContentFactory.getInstance()
                .createContent(contentPanel, MyBundle.message("chat.title"), false)

        ApplicationManager.getApplication().invokeLater {
            toolWindow.contentManager.addContent(content)
        }
    }

    override fun init(toolWindow: ToolWindow) {
//        toolWindow.setTitleActions(listOfNotNull(ActionUtil.getActionGroup("AutoDev.ToolWindow.Chat.TitleActions")))
    }

    companion object {
        lateinit var contentPanel: ChatToolPanel

        fun getToolWindow(project: Project): ToolWindow? {
            return ToolWindowManager.getInstance(project).getToolWindow(Util.id)
        }
    }
}
