package com.github.zjh7890.gpttools.cpp.context

import com.github.zjh7890.gpttools.context.builder.PromptContextBuilder
import com.github.zjh7890.gpttools.context.builder.PromptContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.cidr.lang.psi.OCFile
import com.jetbrains.cidr.lang.psi.OCFunctionDefinition
import com.jetbrains.cidr.lang.psi.OCDeclaration

class CppPromptContextBuilder : PromptContextBuilder {
    override fun getContext(psiElement: PsiElement?, editor: Editor): PromptContext? {
        val project = psiElement?.project
        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project!!).getPsiFile(document)!!

        if (psiFile !is OCFile) {
            return null
        }

        val currentOffset = editor.caretModel.offset
        val elementAtCaret = psiFile.findElementAt(currentOffset)

        // 使用 OCDeclaration 替代 OCClassDecl
        val clazz = PsiTreeUtil.getParentOfType(elementAtCaret, OCDeclaration::class.java)
        val method = PsiTreeUtil.getParentOfType(elementAtCaret, OCFunctionDefinition::class.java)

        var methodInfo = ""
        var simplifyClassText = ""
        var methodName = ""
        var completeSignature = ""
        var className = ""
        val methodText = method?.text ?: ""

        if (method != null) {
            methodName = method.name ?: ""
            completeSignature = method.text?.lines()?.firstOrNull() ?: ""
        }

        if (clazz != null) {
            className = clazz.type.name
        }

        val classText = clazz?.text ?: ""

        // 简化类文本处理
        if (method != null && clazz != null) {
            val newClass = clazz.copy() as OCDeclaration

            // 由于结构变化，可能需要调整这部分逻辑
            try {
                // 这里可能需要根据实际的 OCDeclaration 结构调整
                simplifyClassText = newClass.text
            } catch (ex: Exception) {
                // 忽略异常
            }
        }

        val promptContext = PromptContext(
            methodInfo = methodInfo,
            simplifyClassText = simplifyClassText,
            methodName = methodName,
            completeSignature = completeSignature,
            className = className,
            methodText = methodText,
            classText = classText
        )

        PromptContextBuilder.fillCommonField(psiElement, editor, promptContext)
        return promptContext
    }
}
