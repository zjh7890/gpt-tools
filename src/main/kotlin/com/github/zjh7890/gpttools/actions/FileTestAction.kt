package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.settings.template.PromptTemplate
import com.github.zjh7890.gpttools.utils.ClipboardUtils.copyToClipboard
import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.PsiUtils.getDependencies
import com.github.zjh7890.gpttools.utils.TemplateUtils
import com.github.zjh7890.gpttools.utils.sendToChatWindow
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import java.util.stream.Collectors


class FileTestAction(val promptTemplate: PromptTemplate) : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        val editor: Editor? = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR)
        if (project == null || editor == null) {
            Messages.showMessageDialog(project, "Project or editor not found!", "Error", Messages.getErrorIcon())
            return
        }

        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
        val currentOffset = editor.caretModel.offset
        val elementAtCaret = psiFile?.findElementAt(currentOffset)
        val clazz = PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass::class.java)
        if (clazz == null) {
            Messages.showMessageDialog(project, "Cursor is not inside a class!", "Error", Messages.getErrorIcon());
            return
        }

        try {
            val classes = getDependencies(clazz.containingFile.virtualFile, project)
            // clazz 和 classes 形成新 List, clazz 放在第一个
            val newClasses = listOf(clazz.containingFile.virtualFile) + classes

            val classInfos =
                newClasses.stream().map { x -> x.name }.collect(Collectors.toList()).joinToString("\n")
            val GPT_methodInfo = newClasses.map { FileUtil.readFileInfoForLLM(it, project) }.joinToString("\n\n")
            val GPT_className = clazz.name!!

            val map = mapOf(
                "GPT_methodInfo" to GPT_methodInfo,
                "GPT_className" to GPT_className,
            )

            val result = TemplateUtils.replacePlaceholders(promptTemplate.value, map)
            // Update the content to send to the chat window
            sendToChatWindow(project, { contentPanel, chatCodingService ->
                chatCodingService.newSession()
                contentPanel.setInput(result)
            })
            copyToClipboard(result)
        } catch (ex: Exception) {
            Messages.showMessageDialog(project, "Error finding classes: ${ex.message}", "Error", Messages.getErrorIcon())
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.text = promptTemplate.desc
    }
}


