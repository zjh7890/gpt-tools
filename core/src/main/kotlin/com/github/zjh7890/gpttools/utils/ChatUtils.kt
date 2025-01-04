package com.github.zjh7890.gpttools.utils

import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.toolWindow.llmChat.ChatPanel
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolWindowFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

object ChatUtils {
    fun sendToChatWindow(
        project: Project,
        runnable: (ChatPanel, ChatCodingService) -> Unit,
    ) {
        val chatCodingService = ChatCodingService.getInstance(project)

        val toolWindowManager = LLMChatToolWindowFactory.getToolWindow(project) ?: run {
            logger<ChatCodingService>().warn("Tool window not found")
            return
        }

        ApplicationManager.getApplication().invokeLater {
            toolWindowManager.contentManager.removeAllContents(false)
            LLMChatToolWindowFactory.createToolWindowContentStatic(project, toolWindowManager)

            toolWindowManager.activate {
                val contentPanel = LLMChatToolWindowFactory.getPanel(project)
                if (contentPanel != null) {
                    runnable(contentPanel, chatCodingService)
                }
            }
        }
    }

    fun activateToolWindowRun(
        project: Project,
        runnable: (ChatPanel, ChatCodingService) -> Unit,
    ) {
        val chatCodingService = ChatCodingService.getInstance(project)

        val toolWindowManager = LLMChatToolWindowFactory.getToolWindow(project) ?: run {
            logger<ChatCodingService>().warn("Tool window not found")
            return
        }

        ApplicationManager.getApplication().invokeLater {
            toolWindowManager.activate {
                val contentPanel = LLMChatToolWindowFactory.getPanel(project)
                if (contentPanel != null) {
                    runnable(contentPanel, chatCodingService)
                }
            }
        }
    }

    fun setToolWindowInput(
        project: Project,
        input: String
    ) {
        // 设置内容并激活窗口
        activateToolWindowRun(project) { panel, service ->
            panel.setInput(input)
        }
    }
}
