package com.github.zjh7890.gpttools.kotlin.context

import com.github.zjh7890.gpttools.context.builder.PromptContextBuilder
import com.github.zjh7890.gpttools.context.builder.PromptContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinPromptContextBuilder : PromptContextBuilder {
    override fun getContext(psiElement: PsiElement?, editor: Editor): PromptContext? {
        val project = psiElement?.project
        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project!!).getPsiFile(document)!!

        // 判断是否为 Kotlin 文件
        if (psiFile !is KtFile) {
            return null
        }

        val currentOffset = editor.caretModel.offset
        val elementAtCaret = psiFile.findElementAt(currentOffset)

        // 获取当前光标位置的类和方法
        val clazz = PsiTreeUtil.getParentOfType(elementAtCaret, KtClass::class.java)
        val method = PsiTreeUtil.getParentOfType(elementAtCaret, KtNamedFunction::class.java)

        // 初始化上下文变量
        var methodInfo = ""
        var simplifyClassText = ""
        var methodName = ""
        var completeSignature = ""
        var className = ""
        val methodText = method?.text ?: ""

        if (method != null) {
            methodName = method.name ?: ""
            completeSignature = method.text.lines().first() // 获取方法签名（简化处理）
            try {
                // 这里可以添加获取方法相关类的逻辑
                methodInfo = "" // 根据需要实现
            } catch (ex: Exception) {
                methodInfo = ""
            }
        }

        if (clazz != null) {
            className = clazz.name ?: ""
        }

        // 获取完整的类文本
        val classText = clazz?.text ?: ""

        // 简化类文本处理
        if (method != null && clazz != null) {
            val newClass = clazz.copy() as KtClass
            // 这里可以添加简化类文本的逻辑，移除其他方法和字段
            simplifyClassText = newClass.text
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