package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.agent.FormatCopyAgent
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


class ApplyCopyAction(
    private val project: Project,
    private val progressBar: JProgressBar,
    private val inputSection: AutoDevInputSection,
    private val chatCodingService: ChatCodingService
) : AnAction("Apply copy content", "Apply copy content", GptToolsIcon.ApplyCopyIcon) {

    override fun actionPerformed(e: AnActionEvent) {
        val contentPanel = LLMChatToolWindowFactory.getPanel(project)

        val textArea = JTextArea(10, 50)
        textArea.lineWrap = true
        textArea.wrapStyleWord = true

        val scrollPane = JBScrollPane(textArea)

        val dialog = DialogBuilder(project)
            .apply {
                setTitle("Apply Copy Content")
                setCenterPanel(scrollPane)
                addOkAction()
                addCancelAction()
            }

        val result = dialog.show()

        if (result == DialogWrapper.OK_EXIT_CODE) {
            val message = textArea.text
            if (message.isNotBlank()) {
                ApplicationManager.getApplication().executeOnPooledThread {
                    progressBar.isVisible = true
                    progressBar.isIndeterminate = true

                    val projectStructure = DirectoryUtil.getDirectoryContents(project)
                    FormatCopyAgent.apply(
                        project,
                        LLMSettingsState.toLlmConfig(LLMSettingsState.getInstance().getFormatCopySetting()),
                        projectStructure,
                        message,
                        chatCodingService.sessionManager.getCurrentSession(),
                        contentPanel!!
                    )

                    progressBar.isIndeterminate = false
                    progressBar.isVisible = false
                }
            }
        }
    }
}
