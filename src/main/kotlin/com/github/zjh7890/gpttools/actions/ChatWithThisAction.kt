package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.GptToolsIcon
import com.github.zjh7890.gpttools.utils.sendToChatWindow
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import javax.swing.Icon

class ChatWithThisAction : IntentionAction, Iconable {

    override fun getText(): String {
        return "Chat with this"
    }

    override fun getFamilyName(): String {
        return "ChatWithThis"
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return true
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return

        // 获取当前文件的 VirtualFile
        val virtualFile = file.virtualFile

        // 将当前文件添加到会话
        val chatCodingService = ChatCodingService.getInstance(project)

        val caretModel: CaretModel = editor.caretModel
        val document = editor.document

        // Get the current cursor position
        val currentOffset = caretModel.offset
        val lineNumber = document.getLineNumber(currentOffset)

        val fileName = file.virtualFile.name

// 获取光标所在行的文本，并插入占位符
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineEndOffset = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, lineEndOffset))
        val positionInLine = currentOffset - lineStartOffset
        val lineWithCursor =
            lineText.substring(0, positionInLine) + "${'$'}{GPT_CURSOR_HERE}" + lineText.substring(positionInLine)

// 更新发送到聊天窗口的内容
        sendToChatWindow(project, { contentPanel, chatCodingService ->
            chatCodingService.newSession(true)
            chatCodingService.addFileToCurrentSession(virtualFile)
            val inputText = "\n" + """
在光标处实现以上需求。
光标位置：文件 $fileName，行号：$lineNumber
光标所在行 (${'$'}{GPT_CURSOR_HERE} 是光标所在处占位符，在代码中并不存在)：
```
$lineWithCursor
```
""".trim()
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

