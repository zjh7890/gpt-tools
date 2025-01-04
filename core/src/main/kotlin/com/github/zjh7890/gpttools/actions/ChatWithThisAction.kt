package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.services.SessionManager
import com.github.zjh7890.gpttools.utils.ChatUtils
import com.github.zjh7890.gpttools.utils.GptToolsIcon
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
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

        // 更新发送到聊天窗口的内容
        ChatUtils.sendToChatWindow(project, { contentPanel, chatCodingService ->
            SessionManager.getInstance(project).createNewSession()
            SessionManager.getInstance(project).addFileToCurrentSession(virtualFile)
            contentPanel.setInput("")
        })
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun getIcon(flags: Int): Icon {
        return GptToolsIcon.PRIMARY
    }
}

