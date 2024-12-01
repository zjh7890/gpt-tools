package com.github.zjh7890.gpttools.goland.context

import com.github.zjh7890.gpttools.context.builder.PromptContextBuilder
import com.github.zjh7890.gpttools.context.builder.PromptContext
import com.goide.psi.GoFile
import com.goide.psi.GoFunctionDeclaration
import com.goide.psi.GoTypeSpec
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class GoPromptContextBuilder : PromptContextBuilder {
    override fun getContext(psiElement: PsiElement?, editor: Editor): PromptContext? {
        val project = psiElement?.project
        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project!!).getPsiFile(document)!!

        // 判断是否为 Go 文件
        if (psiFile !is GoFile) {
            return null
        }

        val currentOffset = editor.caretModel.offset
        val elementAtCaret = psiFile.findElementAt(currentOffset)

        // 获取当前位置的函数和类型声明
        val function = PsiTreeUtil.getParentOfType(elementAtCaret, GoFunctionDeclaration::class.java)
        val typeSpec = PsiTreeUtil.getParentOfType(elementAtCaret, GoTypeSpec::class.java)

        // 初始化上下文变量
        var methodInfo = ""
        var simplifyClassText = ""
        var methodName = ""
        var completeSignature = ""
        var className = ""
        val methodText = function?.text ?: ""

        if (function != null) {
            methodName = function.name ?: ""
            // 构建完整的函数签名
            completeSignature = buildFunctionSignature(function)
            methodInfo = function.text
        }

        if (typeSpec != null) {
            className = typeSpec.name ?: ""
            simplifyClassText = typeSpec.text
        }

        // 获取完整的类型声明文本
        val classText = typeSpec?.text ?: ""

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

    private fun buildFunctionSignature(function: GoFunctionDeclaration): String {
        val name = function.name ?: ""
        val parameters = function.signature?.parameters?.text ?: "()"
        val results = function.signature?.result?.text ?: ""

        return if (results.isEmpty()) {
            "func $name$parameters"
        } else {
            "func $name$parameters $results"
        }
    }
}