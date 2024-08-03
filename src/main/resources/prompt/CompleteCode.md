你是一个专业的程序员，补全 ${GPT_GENERATE_CODE_HERE} 处的代码。
代码：
```
package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.settings.actionPrompt.CodeTemplateApplicationSettingsService
import com.github.zjh7890.gpttools.settings.actionPrompt.PromptTemplate
import com.github.zjh7890.gpttools.utils.ClipboardUtils.copyToClipboard
import com.github.zjh7890.gpttools.utils.DrawioToMermaidConverter
import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.PsiUtils.getDependencies
import com.github.zjh7890.gpttools.utils.TemplateUtils
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
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
import java.util.stream.Collectors

class FixThisChunkAction : BaseIntentionAction()  {
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
        val highlightInfos = DaemonCodeAnalyzerImpl.getHighlights(editor.document, HighlightSeverity.WEAK_WARNING, project)

        // 找到光标所在处的 highlightInfo
        ${GPT_GENERATE_CODE_HERE}

//        val errorMessageList = listErrorMessages(project, editor, range)
        val code = editor.document.getText(range)

        val document = editor.document
        val currentOffset = editor.caretModel.offset
        val elementAtCaret = file.findElementAt(currentOffset)
        val clazz = PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass::class.java)
        if (clazz == null) {
            Messages.showMessageDialog(project, "Cursor is not inside a class!", "Error", Messages.getErrorIcon());
            return
        }

        val promptTemplate: PromptTemplate = CodeTemplateApplicationSettingsService.getTemplates()
            .firstOrNull { it.key == "FixThisChunkAction" } ?: return

        // 获取光标位置前后50行的文本
        val totalLines = document.lineCount
        val currentLine = document.getLineNumber(currentOffset)
        val startLine = maxOf(0, currentLine - 50)
        val endLine = minOf(totalLines - 1, currentLine + 50)

        val startOffset = document.getLineStartOffset(startLine)
        val currentLineEndOffset = document.getLineEndOffset(currentLine)
        val endOffset = document.getLineEndOffset(endLine)

        val textBeforeCaret = document.getText(TextRange(startOffset, currentLineEndOffset))
        val textAfterCaret = document.getText(TextRange(currentLineEndOffset, endOffset))

        val method = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java)

        try {
            val classes = getDependencies(clazz.containingFile.virtualFile, project)
            // clazz 和 classes 形成新 List, clazz 放在第一个
            val newClasses = listOf(clazz.containingFile.virtualFile) + classes
            val classInfos =
                newClasses.stream().map { x -> x.name }.collect(Collectors.toList()).joinToString("\n")
            val GPT_methodInfo = newClasses.map { "${it.name}\n```\n${FileUtil.readContentFromVirtualFile(it)}```\n" }.joinToString("\n")
            val GPT_className = clazz.name!!


            val map = mapOf(
                "GPT_methodInfo" to GPT_methodInfo,
                "GPT_methodName" to (method?.name ?: ""),
                "GPT_methodText" to (method?.text ?: ""),
                "GPT_className" to GPT_className,
                "GPT_selectionText" to (editor.selectionModel.selectedText ?: ""),
                "GPT_textBeforeCursor" to file.text.substring(0, editor.caretModel.offset),
                "GPT_textAfterCursor" to file.text.substring(editor.caretModel.offset),
                "GPT_allText" to file.text,
                "GPT_50LinesTextBeforeCaret" to textBeforeCaret,
                "GPT_50LinesTextAfterCaret" to textAfterCaret,
            )

            val result = TemplateUtils.replacePlaceholders(promptTemplate.value, map)
            Messages.showMessageDialog(project, classInfos, "Class Finder Results", Messages.getInformationIcon())
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
}
```
