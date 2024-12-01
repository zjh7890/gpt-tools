package com.github.zjh7890.gpttools.rust.context

import com.github.zjh7890.gpttools.context.builder.PromptContextBuilder
import com.github.zjh7890.gpttools.context.builder.PromptContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType

class RustPromptContextBuilder : PromptContextBuilder {
    override fun getContext(psiElement: PsiElement?, editor: Editor): PromptContext? {
        val project = psiElement?.project
        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project!!).getPsiFile(document)!!

        // 判断是否为 Rust 文件
        if (psiFile !is RsFile) {
            return null
        }

        val currentOffset = editor.caretModel.offset
        val elementAtCaret = psiFile.findElementAt(currentOffset)

        // 获取当前位置的函数和实现块
        val function = elementAtCaret?.parentOfType<RsFunction>()
        val implItem = elementAtCaret?.parentOfType<RsImplItem>()

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
        }

        if (implItem != null) {
            // 获取实现块的类型名称
            className = implItem.typeReference?.text ?: ""
        }

        // 获取完整的实现块文本
        val classText = implItem?.text ?: ""

        // 如果存在函数和实现块，创建简化版本的实现块文本
        if (function != null && implItem != null) {
            val newImpl = implItem.copy() as RsImplItem

            // 移除其他函数，只保留当前函数
            PsiTreeUtil.findChildrenOfType(newImpl, RsFunction::class.java)
                .filter { it.name != function.name }
                .forEach { it.delete() }

            simplifyClassText = newImpl.text
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

    private fun buildFunctionSignature(function: RsFunction): String {
        // 使用 toString() 替代 text
        val visibility = function.visibility.toString() ?: ""
        val name = function.name ?: ""
        val params = function.valueParameterList?.valueParameterList?.joinToString(", ") {
            "${it.pat?.text ?: ""}: ${it.typeReference?.text ?: ""}"
        } ?: ""
        val returnType = function.retType?.typeReference?.text?.let { " -> $it" } ?: ""

        return "$visibility fn $name($params)$returnType"
    }
}