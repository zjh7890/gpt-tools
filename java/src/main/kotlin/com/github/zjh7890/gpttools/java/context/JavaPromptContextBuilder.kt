package com.github.zjh7890.gpttools.java.context

import com.github.zjh7890.gpttools.context.builder.PromptContextBuilder
import com.github.zjh7890.gpttools.context.builder.PromptContext
import com.github.zjh7890.gpttools.java.util.PsiUtils.findClassesFromMethod
import com.github.zjh7890.gpttools.java.util.PsiUtils.generateSignature
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

class JavaPromptContextBuilder : PromptContextBuilder {
    override fun getContext(psiElement: PsiElement?, editor: Editor): PromptContext? {
        val project = psiElement?.project
        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project!!).getPsiFile(document)!!
        
        // 判断是否为 Java 文件
        if (psiFile !is PsiJavaFile) {
            return null
        }

        val currentOffset = editor.caretModel.offset
        val elementAtCaret = psiFile.findElementAt(currentOffset)

        // Retrieve the class and method at the current caret position
        val clazz = PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass::class.java)
        val method = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java)

        // Initialize context variables with default empty values
        var methodInfo = ""
        var simplifyClassText = ""
        var methodName = ""
        var completeSignature = ""
        var className = ""
        val methodText = method?.text ?: ""

        if (method != null) {
            methodName = method.name
            completeSignature = generateSignature(method, true)
            try {
                val classes = findClassesFromMethod(method, project)
                methodInfo = classes.joinToString("\n") { it.className }
            } catch (ex: Exception) {
                // If unable to find classes, keep methodInfo empty
                methodInfo = ""
            }
        }

        if (clazz != null) {
            className = clazz.name ?: ""
        }

        // 获取完整的类文本
        val classText = clazz?.text ?: ""

        // Populate context variables if possible
        if (method != null && clazz != null) {
            val signature = generateSignature(method, false)
            val newClass = clazz.copy() as PsiClass

            // Remove other methods and fields to simplify the class text
            newClass.methods.filter {
                generateSignature(it, false) != signature
            }.forEach { newClass.deleteChildRange(it, it) }

            newClass.fields.filterNotNull().forEach { field ->
                try {
                    newClass.deleteChildRange(field, field)
                } catch (ex: Exception) {
                    // Ignored to maintain robustness
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
            classText = classText,  // 添加 classText 字段
        )
        PromptContextBuilder.fillCommonField(psiElement, editor, promptContext)
        return promptContext
    }
}