package com.github.zjh7890.gpttools.utils

import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.toolWindow.llmChat.ChatToolPanel
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolWindowFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

fun sendToChatWindow(
    project: Project,
    runnable: (ChatToolPanel, ChatCodingService) -> Unit,
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

fun sendToChatPanel(project: Project, runnable: (ChatToolPanel, ChatCodingService) -> Unit) {
    sendToChatWindow(project, runnable)
}

//fun sendToContextLess(
//    project: Project,
//    actionType: ChatActionType,
//    runnable: (LLMChatToolPanel, ChatCodingService) -> Unit,
//) {
//    val toolWindowManager = PlanCodeToolWindowFactory.getToolWindow(project) ?: run {
//        logger<ChatCodingService>().warn("Tool window not found")
//        return
//    }
//}

//    val chatCodingService = PlanCodeToolWindowFactory.chatCodingService
//    val contentPanel = PlanCodeToolWindowFactory.contentPanel
//
//    val contentManager = toolWindowManager.contentManager
//    val content = contentManager.factory.createContent(contentPanel, chatCodingService.getLabel(), false)
//
//    ApplicationManager.getApplication().invokeLater {
//        contentManager.removeAllContents(false)
//        contentManager.addContent(content)
//
//        toolWindowManager.activate {
//            runnable(contentPanel, chatCodingService)
//        }
//    }

