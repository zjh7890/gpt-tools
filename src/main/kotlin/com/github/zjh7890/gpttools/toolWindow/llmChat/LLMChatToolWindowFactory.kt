package com.github.zjh7890.gpttools.toolWindow.llmChat

import com.github.zjh7890.gpttools.MyBundle
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolWindowFactory.Companion.contentPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

class LLMChatToolWindowFactory : ToolWindowFactory, DumbAware {
    object Util {
        const val id = "LLMChat"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        contentPanel = LLMChatToolPanel(toolWindow.disposable, project)
        val content =
            ContentFactory.getInstance()
                .createContent(contentPanel, MyBundle.message("chat.title"), false)

        ApplicationManager.getApplication().invokeLater {
            toolWindow.contentManager.addContent(content)
        }
    }

    override fun init(toolWindow: ToolWindow) {
        toolWindow.setTitleActions(listOfNotNull(LLMClearChatHistoryAction()))
    }

    companion object {
        lateinit var contentPanel: LLMChatToolPanel

        fun getToolWindow(project: Project): ToolWindow? {
            return ToolWindowManager.getInstance(project).getToolWindow(Util.id)
        }
    }
}

class LLMClearChatHistoryAction : AnAction("Clear Chat History") {
    override fun actionPerformed(e: AnActionEvent) {
        // Assuming `ChatToolPanel` has a method `clearHistory()`
        contentPanel.resetChatSession()
    }
}
