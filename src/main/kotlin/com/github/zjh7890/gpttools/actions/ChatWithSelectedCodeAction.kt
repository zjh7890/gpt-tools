package com.github.zjh7890.gpttools.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolWindowFactory
import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread

class ChatWithSelectedCodeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText ?: return
        
        val project = e.project ?: return
        // 更新发送到聊天窗口的内容
        val chatToolPanel = LLMChatToolWindowFactory.getPanel(project)
        chatToolPanel?.setInput(chatToolPanel.inputSection.text + "\n"  + FileUtil.wrapBorder(selectedText))
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabled = hasSelection
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}