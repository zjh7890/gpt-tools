package com.github.zjh7890.gpttools.scala.context

import com.github.zjh7890.gpttools.context.builder.PromptContextBuilder
import com.github.zjh7890.gpttools.context.builder.PromptContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import com.intellij.psi.util.PsiTreeUtil

class ScalaPromptContextBuilder : PromptContextBuilder {
    override fun getContext(psiElement: PsiElement?, editor: Editor): PromptContext? {
        val project = psiElement?.project
        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project!!).getPsiFile(document)!!

        // 判断是否为 Scala 文件
        if (psiFile !is ScalaFile) {
            return null
        }

        val currentOffset = editor.caretModel.offset
        val elementAtCaret = psiFile.findElementAt(currentOffset)

        // 获取当前光标位置的类和方法
        val clazz = PsiTreeUtil.getParentOfType(elementAtCaret, ScClass::class.java)
        val method = PsiTreeUtil.getParentOfType(elementAtCaret, ScFunction::class.java)

        // 初始化上下文变量
        var methodInfo = ""
        var simplifyClassText = ""
        var methodName = ""
        var completeSignature = ""
        var className = ""
        val methodText = method?.text ?: ""

        if (method != null) {
            methodName = method.name
            // 获取方法签名
            completeSignature = "${method.name}${method.paramClauses() }: ${method.returnType?.presentableText ?: "Unit"}"

            // TODO: 如果需要，可以实现类似 findClassesFromMethod 的功能
            methodInfo = ""
        }

        if (clazz != null) {
            className = clazz.name ?: ""
        }

        // 获取完整的类文本
        val classText = clazz?.text ?: ""

        // 简化类文本，只保留当前方法
        if (method != null && clazz != null) {
            val newClass = clazz.copy() as ScClass

            // 移除其他方法
            for (function in newClass.functions().filter { it.name != methodName }) {
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
}