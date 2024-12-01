package com.github.zjh7890.gpttools.pycharm.context

import com.github.zjh7890.gpttools.context.builder.PromptContextBuilder
import com.github.zjh7890.gpttools.context.builder.PromptContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyClass

class PythonPromptContextBuilder : PromptContextBuilder {
    override fun getContext(psiElement: PsiElement?, editor: Editor): PromptContext? {
        val project = psiElement?.project
        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project!!).getPsiFile(document)!!

        // 判断是否为 Python 文件
        if (psiFile !is PyFile) {
            return null
        }

        val currentOffset = editor.caretModel.offset
        val elementAtCaret = psiFile.findElementAt(currentOffset)

        // 获取当前光标位置的类和函数
        val pyClass = PsiTreeUtil.getParentOfType(elementAtCaret, PyClass::class.java)
        val pyFunction = PsiTreeUtil.getParentOfType(elementAtCaret, PyFunction::class.java)

        // 初始化上下文变量
        var methodInfo = ""
        var simplifyClassText = ""
        var methodName = ""
        var completeSignature = ""
        var className = ""
        val methodText = pyFunction?.text ?: ""

        if (pyFunction != null) {
            methodName = pyFunction.name ?: ""
            // 构建函数签名
            completeSignature = buildFunctionSignature(pyFunction)
            // 这里可以添加获取相关类的逻辑
            methodInfo = pyFunction.getDocStringValue() ?: ""
        }

        if (pyClass != null) {
            className = pyClass.name ?: ""
            // 获取完整的类文本
            val classText = pyClass.text ?: ""

            // 简化类文本，只保留当前函数
            if (pyFunction != null) {
                simplifyClassText = buildSimplifiedClassText(pyClass, pyFunction)
            }
        }

        val promptContext = PromptContext(
            methodInfo = methodInfo,
            simplifyClassText = simplifyClassText,
            methodName = methodName,
            completeSignature = completeSignature,
            className = className,
            methodText = methodText,
            classText = pyClass?.text ?: ""
        )

        PromptContextBuilder.fillCommonField(psiElement, editor, promptContext)
        return promptContext
    }

    private fun buildFunctionSignature(function: PyFunction): String {
        val name = function.name ?: ""
        val params = function.parameterList.parameters.joinToString(", ") {
            "${it.name}"
        }
        val returnType = function.annotation?.text?.let { " -> $it" } ?: ""
        return "def $name($params)$returnType"
    }

    private fun buildSimplifiedClassText(pyClass: PyClass, currentFunction: PyFunction): String {
        val className = pyClass.name ?: ""
        val classDefinition = "class $className:"
        val functions = pyClass.methods.filter { it == currentFunction }
        return buildString {
            appendLine(classDefinition)
            functions.forEach { function ->
                appendLine("    ${function.text}")
            }
        }
    }
}