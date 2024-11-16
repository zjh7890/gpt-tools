package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.GptToolsIcon
import com.github.zjh7890.gpttools.utils.sendToChatWindow
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.util.IncorrectOperationException
import javax.swing.Icon

class CompleteThisAction : IntentionAction, Iconable {

    override fun getText(): String {
        return "Complete this"
    }

    override fun getFamilyName(): String {
        return "CompleteThis"
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return true
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return

        val caretModel: CaretModel = editor.caretModel
        val document = editor.document

        // Get the selected text or the element around the cursor if no text is selected
        val prefixText = caretModel.currentCaret.selectedText ?: ""

        // Get the language of the current file
        val language = file.language.displayName

        // Get the current cursor position
        val currentOffset = caretModel.offset
        val lineNumber = document.getLineNumber(currentOffset)

        val contextBeforeCursor = document.getText(com.intellij.openapi.util.TextRange(0, currentOffset))
        val contextAfterCursor =
            document.getText(com.intellij.openapi.util.TextRange(currentOffset, document.textLength))
        val border = FileUtil.determineBorder(file.virtualFile)

        val fileName = file.virtualFile.path

        // Update the content to send to the chat window
        sendToChatWindow(project, { contentPanel, chatCodingService ->
            chatCodingService.newSession()
            val inputText = """
在 ${'$'}{GPT_GENERATE_CODE_HERE} 处自动补全代码。
严格只返回占位符处新增的内容。
假设待补全代码是：
```
log.error(${'$'}{GPT_GENERATE_CODE_HERE});
```
那么你就返回：
```
"rpc authQueryService.getUserAuthListByUserIds error, req: {}", req
```

真实的待补全代码：
${border}
${contextBeforeCursor}${'$'}{GPT_GENERATE_CODE_HERE}${contextAfterCursor}
${border}
""".trimIndent()
            contentPanel.setInput(inputText)
        })
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun getIcon(flags: Int): Icon {
        return GptToolsIcon.PRIMARY
    }
}

