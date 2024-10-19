package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolWindowFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.ui.Messages

class AddToSessionFileListAction : AnAction("Add to Session File List") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val virtualFile: VirtualFile? = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        if (project == null || virtualFile == null) {
            Messages.showErrorDialog(project, "No file selected.", "Error")
            return
        }

        val chatCodingService = ChatCodingService.getInstance(project)
        chatCodingService.addFileToCurrentSession(virtualFile)
    }
}