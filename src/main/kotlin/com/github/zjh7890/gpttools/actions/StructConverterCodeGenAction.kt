package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.settings.actionPrompt.CodeTemplateApplicationSettingsService
import com.github.zjh7890.gpttools.settings.actionPrompt.PromptTemplate
import com.github.zjh7890.gpttools.utils.ClipboardUtils.copyToClipboard
import com.github.zjh7890.gpttools.utils.PsiUtils.findClassesFromMethod
import com.github.zjh7890.gpttools.utils.TemplateUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import java.util.stream.Collectors


class StructConverterCodeGenAction(val promptTemplate: PromptTemplate) : AnAction() {
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
            RuntimeException("why").printStackTrace();
            return;
        }

        val parameters = method.parameterList.parameters
        if (parameters.isEmpty()) {
            Messages.showMessageDialog(
                project,
                "The method has no parameters.",
                "Method Details",
                Messages.getInformationIcon()
            )
            return
        }
        val firstParameterType = parameters[0].type
        val returnType = method.returnType
        if (returnType == null) {
            Messages.showMessageDialog(
                project,
                "The method has no return type.",
                "Method Details",
                Messages.getInformationIcon()
            )
            return
        }

        try {
            val classes = findClassesFromMethod(method, project)
            val classInfos =
                classes.stream().map { x -> x.className }.collect(Collectors.toList()).joinToString("\n")

            val GPT_methodInfo = classes.joinToString("\n")
            val GPT_firstParamName = firstParameterType.presentableText
            val GPT_returnParamName = returnType.presentableText

            val map = mapOf(
                "GPT_methodInfo" to GPT_methodInfo,
                "GPT_firstParamName" to GPT_firstParamName,
                "GPT_returnParamName" to GPT_returnParamName
            )

            val result = TemplateUtils.replacePlaceholders(promptTemplate.value, map)
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


