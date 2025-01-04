package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.agent.GenerateDiffAgent
import com.github.zjh7890.gpttools.settings.llmSetting.LLMSettingsState
import com.github.zjh7890.gpttools.toolWindow.chat.AutoDevInputSection
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolWindowFactory
import com.github.zjh7890.gpttools.utils.DirectoryUtil
import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.GptToolsIcon
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import javax.swing.JProgressBar
import javax.swing.JTextArea

class GenerateDiffAction(
    private val project: Project,
    private val progressBar: JProgressBar,
    private val inputSection: AutoDevInputSection,
    private val chatCodingService: ChatCodingService
) : AnAction("Generate diff based on this chat", "Generate diff based on this chat", AllIcons.Actions.ToggleVisibility) {

    override fun actionPerformed(e: AnActionEvent) {
        val projectStructure = DirectoryUtil.getDirectoryContents(project)
        val chatHistory = chatCodingService.sessionManager.exportChatHistory(true)
        val contentPanel = LLMChatToolWindowFactory.getPanel(project)

        ApplicationManager.getApplication().executeOnPooledThread {
            progressBar.isVisible = true
            progressBar.isIndeterminate = true
            GenerateDiffAgent.apply(
                project,
                LLMSettingsState.toLlmConfig(inputSection.getSelectedSetting()),
                projectStructure,
                chatHistory,
                chatCodingService.sessionManager.getCurrentSession(),
                contentPanel!!
            )
            progressBar.isIndeterminate = false
            progressBar.isVisible = false
        }
    }
}

