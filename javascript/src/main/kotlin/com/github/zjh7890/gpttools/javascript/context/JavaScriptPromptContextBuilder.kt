package com.github.zjh7890.gpttools.javascript.context

import com.github.zjh7890.gpttools.context.builder.PromptContextBuilder
import com.github.zjh7890.gpttools.context.builder.PromptContext
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class JavaScriptPromptContextBuilder : PromptContextBuilder {
    override fun getContext(psiElement: PsiElement?, editor: Editor): PromptContext? {
        val project = psiElement?.project
        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project!!).getPsiFile(document)!!

        // 判断是否为 JavaScript 文件
        if (psiFile !is JSFile) {
            return null
        }

        val currentOffset = editor.caretModel.offset
        val elementAtCaret = psiFile.findElementAt(currentOffset)

        // 获取当前位置的类和方法
        val clazz = PsiTreeUtil.getParentOfType(elementAtCaret, JSClass::class.java)
        val method = PsiTreeUtil.getParentOfType(elementAtCaret, JSFunction::class.java)

        // 初始化上下文变量
        var methodInfo = ""
        var simplifyClassText = ""
        var methodName = ""
        var completeSignature = ""
        var className = ""
        val methodText = method?.text ?: ""

        if (method != null) {
            methodName = method.name ?: ""
            // 构建方法签名
            completeSignature = buildMethodSignature(method)
        }

        if (clazz != null) {
            className = clazz.name ?: ""
        }

        // 获取完整的类文本
        val classText = clazz?.text ?: ""

        // 如果存在方法和类，创建简化的类文本
        if (method != null && clazz != null) {
            val newClass = clazz.copy() as JSClass

            // 移除其他方法，只保留当前方法
            newClass.functions.filter { it.name != method.name }.forEach { function ->
                try {
                    newClass.deleteChildRange(function, function)
                } catch (ex: Exception) {
                    // 忽略异常以保持健壮性
                }
            }

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

    private fun buildMethodSignature(method: JSFunction): String {
        val params = method.parameters.joinToString(", ") { param ->
            "${param.name}${if (param.typeElement != null) ": ${param.typeElement?.text}" else ""}"
        }
        val returnType = method.returnType?.typeText ?: "void"
        return "${method.name}(${params}): ${returnType}"
    }
}