package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.settings.actionPrompt.CodeTemplateApplicationSettingsService
import com.github.zjh7890.gpttools.settings.actionPrompt.PromptTemplate
import com.github.zjh7890.gpttools.utils.ClipboardUtils.copyToClipboard
import com.github.zjh7890.gpttools.utils.GptToolsIcon
import com.github.zjh7890.gpttools.utils.TemplateUtils
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.NotNull
import javax.swing.Icon

class FixThisChunkAction : BaseIntentionAction(), Iconable
{
    override fun isDumbAware(): Boolean = true

    override fun startInWriteAction(): Boolean = false
    override fun getText(): String = "gpt-tools Fix This"
    override fun getFamilyName(): String = "gpt-tools Fix This"

    override fun isAvailable(@NotNull project: Project, editor: Editor, psiFile: PsiFile): Boolean {
        val offset = editor.caretModel.offset
        val elementAtCaret = psiFile.findElementAt(offset)
        return hasErrorAt(project, elementAtCaret, editor)
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(@NotNull project: Project, editor: Editor, file: PsiFile) {
        val range = getCodeRange(editor)
        val currentOffset = editor.caretModel.offset

        val errorMessageList = listErrorMessages(project, editor, range)
        val errorMessage = StringBuilder()
        for (index in errorMessageList.indices) {
            errorMessage.append(index + 1)
            errorMessage.append(". ")
            errorMessage.append(errorMessageList.get(index) as String)
            errorMessage.append("\n")
        }
        val code = editor.document.getText(range)

        val document = editor.document

        val promptTemplate: PromptTemplate = CodeTemplateApplicationSettingsService.getTemplates()
            .firstOrNull { it.key == "FixThisChunkAction" } ?: return

        // 获取光标位置前后30行的文本
        val totalLines = document.lineCount
        val currentLine = document.getLineNumber(currentOffset)
        val startLine = maxOf(0, currentLine - 30)
        val endLine = minOf(totalLines - 1, currentLine + 30)

        val startOffset = document.getLineStartOffset(startLine)
        val currentLineEndOffset = document.getLineEndOffset(currentLine)
        val endOffset = document.getLineEndOffset(endLine)

        val textBeforeCaret = document.getText(TextRange(startOffset, currentLineEndOffset))
        val textAfterCaret = document.getText(TextRange(currentLineEndOffset, endOffset))

        try {

            val map = mapOf(
                "GPT_line" to code,
                "GPT_errorMessages" to errorMessage.toString(),
                "GPT_fileName" to file.name,
                "GPT_30LinesTextBeforeCaret" to textBeforeCaret,
                "GPT_30LinesTextAfterCaret" to textAfterCaret,
            )

            val result = TemplateUtils.replacePlaceholders(promptTemplate.value, map)
            copyToClipboard(result)
        } catch (ex: Exception) {
            Messages.showMessageDialog(project, "Error finding classes: ${ex.message}", "Error", Messages.getErrorIcon())
        }
    }

    private fun hasErrorAt(project: Project, element: PsiElement?, editor: Editor): Boolean {
        val range = getCodeRange(editor)
        val highlightInfos = DaemonCodeAnalyzerImpl.getHighlights(editor.document, HighlightSeverity.WEAK_WARNING, project)
        if (CollectionUtils.isEmpty(highlightInfos)) {
            return false
        }
        for (info in highlightInfos) {
            val infoStartOffset = info.startOffset
            val infoEndOffset = info.endOffset
            if (range.intersectsStrict(infoStartOffset, infoEndOffset)) {
                return true
            }
        }
        return false
    }

    private fun listErrorMessages(project: Project, editor: Editor, range: TextRange): List<String> {
        val highlightInfos = DaemonCodeAnalyzerImpl.getHighlights(editor.document, HighlightSeverity.WEAK_WARNING, project)
        val errorMessageList = mutableListOf<String>()
        for (info in highlightInfos) {
            val infoStartOffset = info.startOffset
            val infoEndOffset = info.endOffset
            if (range.intersectsStrict(infoStartOffset, infoEndOffset) && StringUtils.isNotBlank(info.description)) {
                errorMessageList.add(info.description)
            }
        }
        return errorMessageList
    }

    private fun getCodeRange(editor: Editor): TextRange {
        val selectionModel = editor.selectionModel
        var startOffset = selectionModel.selectionStart
        var endOffset = selectionModel.selectionEnd
        if (startOffset == endOffset) {
            val logicalPosition = editor.caretModel.logicalPosition
            startOffset = editor.document.getLineStartOffset(logicalPosition.line)
            endOffset = editor.document.getLineEndOffset(logicalPosition.line)
        }
        return TextRange(startOffset, endOffset)
    }

    override fun getIcon(flags: Int): Icon {
        return GptToolsIcon.PRIMARY
    }
}