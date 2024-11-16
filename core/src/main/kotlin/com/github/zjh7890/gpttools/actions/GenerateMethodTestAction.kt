package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.settings.template.PromptTemplate
import com.github.zjh7890.gpttools.utils.ClipboardUtils.copyToClipboard
import com.github.zjh7890.gpttools.utils.PsiUtils.findClassesFromMethod
import com.github.zjh7890.gpttools.utils.PsiUtils.generateSignature
import com.github.zjh7890.gpttools.utils.TemplateUtils
import com.github.zjh7890.gpttools.utils.sendToChatWindow
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import java.util.stream.Collectors


class GenerateMethodTestAction(val promptTemplate: PromptTemplate) : AnAction() {
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
        val method = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java)
        if (method == null) {
            Messages.showMessageDialog(project, "Method not found!", "Error", Messages.getErrorIcon())
            return;
        }

        val GPT_userInput =
            Messages.showInputDialog(project, "Enter some information:", "Input Needed", Messages.getQuestionIcon()) ?: ""
        val function = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java)
        val signature = generateSignature(method, false)
        val GPT_completeSignature = generateSignature(method, true)

        val containingClass = function?.containingClass ?: return

        val newClass = containingClass.copy() as PsiClass

        newClass.methods.filter {
            generateSignature(it, false) != signature
        }.forEach { newClass.deleteChildRange(it, it) }
        newClass.fields.filterNotNull().forEach { field ->
            try {
                newClass.deleteChildRange(field, field)
            } catch (ex: Exception) {
                // 在这里处理异常，例如打印错误日志或显示错误消息
                println("Error deleting field: ${ex.message}")
            }
        }

//        com.yupaopao.platform.common.dto.Response<com.yupaopao.bixin.biggie.api.entity.BiggieInfoDTO> null.queryBiggieInfo(long)

        // Output the result, modify as needed to handle the created class
        println(newClass.text)

        try {
            val classes = findClassesFromMethod(method, project)
            val classInfos =
                classes.stream().map { x -> x.className }.collect(Collectors.toList()).joinToString("\n")
            val GPT_methodInfo = classes.joinToString("\n")
            val GPT_methodName = method.name
            val GPT_simplifyClassText = newClass.text!!


            val map = mapOf(
                "GPT_methodInfo" to GPT_methodInfo,
                "GPT_userInput" to GPT_userInput,
                "GPT_simplifyClassText" to GPT_simplifyClassText,
                "GPT_methodName" to GPT_methodName,
                "GPT_completeSignature" to GPT_completeSignature
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


