package com.github.zjh7890.gpttools.toolWindow.chat.block

import com.github.zjh7890.gpttools.agent.GenerateDiffAgent.logger
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolWindowFactory
import com.github.zjh7890.gpttools.utils.CmdUtils
import com.github.zjh7890.gpttools.utils.ParseUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.psi.PsiManager

class AutoDevRunDevInsAction(val block: CodeBlock) : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.project ?: return
        e.presentation.isEnabled = block.code.languageId == "custom"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(com.intellij.openapi.actionSystem.PlatformDataKeys.EDITOR) ?: return
        val project = e.project ?: return

        val document = editor.document
        val text = document.text
        val file = FileDocumentManager.getInstance().getFile(document) ?: return

        val language = PsiManager.getInstance(project).findFile(file)?.language?.id ?: return

        val parsedResponse = ParseUtils.processResponse(block.msg)
        // 完成后处理最终结果
        try {
            val sb = StringBuilder()
            when {
                parsedResponse.isCustomCommand -> {
                    parsedResponse.customCommands?.forEach { command ->
                        val result = CmdUtils.executeCmd(command, "custom", project)
                        sb.append(result + "\n\n")
                    }
                }
            }

            LLMChatToolWindowFactory.getPanel(project)?.addMessage(sb.toString(), true, sb.toString(), true, null)
        } catch (e: Exception) {
            logger.error("处理响应时出错: ${e.message}", e)
            throw e
        }
    }
}
