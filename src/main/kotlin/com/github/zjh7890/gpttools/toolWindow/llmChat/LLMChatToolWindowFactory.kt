package com.github.zjh7890.gpttools.toolWindow.llmChat

import com.github.zjh7890.gpttools.MyBundle
import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.intellij.icons.AllIcons
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
        createToolWindowContentStatic(project, toolWindow, true)
        
    }

    override fun init(toolWindow: ToolWindow) {
        toolWindow.setTitleActions(listOfNotNull(
            LLMNewChatAction(),
            ExportChatHistoryAction(),
            AppendMessageAction()

        ))
    }

    companion object {
        fun getToolWindow(project: Project): ToolWindow? {
            return ToolWindowManager.getInstance(project).getToolWindow(Util.id)
        }
        

        fun getPanel(project: Project): ChatToolPanel? {
            return LLMChatToolWindowFactory.getToolWindow(project)
                ?.contentManager?.getContent(0)?.component as? ChatToolPanel
        }

        fun createToolWindowContentStatic(project: Project, toolWindow: ToolWindow, reloadSession: Boolean = false) {
            val contentManager = toolWindow.contentManager

            // 创建 Chat Panel
            val chatPanel = ChatToolPanel(toolWindow.disposable, project)
            val chatContent = ContentFactory.getInstance()
                .createContent(chatPanel, MyBundle.message("chat.title"), false)
            contentManager.addContent(chatContent)

            // 创建 Chat History Panel
            val chatHistoryPanel = ChatHistoryPanel(project)
            val historyContent = ContentFactory.getInstance()
                .createContent(chatHistoryPanel, "Chat History", false)
            contentManager.addContent(historyContent)
            if (reloadSession) {
                ApplicationManager.getApplication().invokeLater {chatPanel.reloadConversation()}
            }
        }
    }
}

class LLMNewChatAction : AnAction("New Chat", "New Chat", AllIcons.Actions.Edit) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val contentPanel = LLMChatToolWindowFactory.getToolWindow(project)
            ?.contentManager?.getContent(0)?.component as? ChatToolPanel
        if (contentPanel == null) {
            throw IllegalStateException("Content panel is null")
        }
        contentPanel.newChatSession()
    }
}

class ExportChatHistoryAction : AnAction("Export Chat History", "Export the chat history", AllIcons.ToolbarDecorator.Export) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val chatCodingService = ChatCodingService.getInstance(project)
        val exportedContent = chatCodingService.exportChatHistory()
        ClipboardUtils.copyToClipboard(exportedContent)
    }
}

// 新增 AppendMessageAction 类
class AppendMessageAction : AnAction("Append Message", "Append a new message", AllIcons.ToolbarDecorator.AddIcon) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val contentPanel = LLMChatToolWindowFactory.getPanel(project)
        val dialog = AppendMessageDialog(project)
        dialog.show()
        if (dialog.isOK) {
            // 修改 actionPerformed 方法中的角色赋值
            val role: ChatRole = dialog.selectedRole
            val message = dialog.message
// 处理添加消息
            val chatCodingService = ChatCodingService.getInstance(project)
            val chatMessage = chatCodingService.appendLocalMessage(role, message)
            contentPanel?.addMessage(message, role == ChatRole.user, render = true, chatMessage = chatMessage)
        }
    }
}
