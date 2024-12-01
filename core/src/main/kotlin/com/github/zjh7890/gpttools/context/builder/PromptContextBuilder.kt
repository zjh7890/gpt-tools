package com.github.zjh7890.gpttools.context.builder

import com.github.zjh7890.gpttools.agent.GenerateDiffAgent
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

interface PromptContextBuilder {
    fun getContext(psiElement: PsiElement?, editor: Editor): PromptContext?

    companion object {
        fun fillCommonField(psiElement: PsiElement?, editor: Editor, promptContext: PromptContext) {
            // 获取编辑器的文档对象
            val document = editor.document
            // 获取当前光标位置
            val offset = editor.caretModel.offset

            // 获取选中的文本
            val selectionModel = editor.selectionModel
            promptContext.selectedText = if (selectionModel.hasSelection())
                selectionModel.selectedText!!
            else null

            // 获取光标前后的文本
            promptContext.textBeforeCursor = document.text.substring(0, offset)
            promptContext.textAfterCursor = document.text.substring(offset)

            // 获取整个文档的文本
            promptContext.allText = document.text

            // 获取光标所在的方法文本（如果psiElement是方法的话）
            promptContext.methodText = psiElement?.text

            // 获取光标前后50行的文本
            val currentLine = document.getLineNumber(offset)
            val startLine = maxOf(0, currentLine - 50)
            val endLine = minOf(document.lineCount - 1, currentLine + 50)

            promptContext.text50LinesBeforeCaret =
                document.getText(
                    com.intellij.openapi.util.TextRange(
                        document.getLineStartOffset(startLine),
                        document.getLineEndOffset(currentLine)
                    )
                )

            promptContext.text50LinesAfterCaret =
                document.getText(
                    com.intellij.openapi.util.TextRange(
                        document.getLineStartOffset(currentLine),
                        document.getLineEndOffset(endLine)
                    )
                )
        }
    }
}

data class PromptContext(
    @WrapBorder
    var methodInfo: String? = null,
    @WrapBorder
    var simplifyClassText: String? = null,
    var methodName: String? = null,
    @WrapBorder
    var completeSignature: String? = null,
    var className: String? = null,
    @WrapBorder
    var methodText: String? = null,
    @WrapBorder
    val classText: String? = null,

    @WrapBorder
    var textBeforeCursor: String? = null,
    @WrapBorder
    var textAfterCursor: String? = null,
    @WrapBorder
    var allText: String? = null,
    @WrapBorder
    var text50LinesBeforeCaret: String? = null,
    @WrapBorder
    var text50LinesAfterCaret: String? = null,
    @WrapBorder
    var selectedText: String? = null,
)

@Target(AnnotationTarget.VALUE_PARAMETER) // 指定注解可以用在函数和类上
@Retention(AnnotationRetention.RUNTIME) // 指定注解在运行时可见
annotation class WrapBorder(

)

object PromptContextProvider {
    private val languageExtension = LanguageExtension<PromptContextBuilder>("com.github.zjh7890.gpttools.promptContextBuilder")
    private val providers: List<PromptContextBuilder>
    val logger = logger<GenerateDiffAgent>()

    init {
        val registeredLanguages = Language.getRegisteredLanguages()
        providers = registeredLanguages.mapNotNull(languageExtension::forLanguage)
    }

    @JvmStatic
    fun from(psiElement: PsiElement?, editor: Editor): PromptContext {
        for (provider in providers) {
            try {
                provider.getContext(psiElement, editor)?.let {
                    return it
                }
            } catch (e: Exception) {
                logger<PromptContextProvider>().error("Error while getting prompt context from $provider", e)
            }
        }

        val promptContext = PromptContext()
        PromptContextBuilder.fillCommonField(psiElement, editor, promptContext)
        return promptContext
    }
}
