package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.utils.ChatUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

class AddFileToSessionAction : AnAction("Add File to Session") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val virtualFile: VirtualFile? = e.getData(CommonDataKeys.VIRTUAL_FILE)

        if (project == null || virtualFile == null) {
            Messages.showErrorDialog(project, "No file selected.", "Error")
            return
        }

        ChatUtils.activateToolWindowRun(project) { panel, service ->
            service.addFileToCurrentSession(virtualFile)
        }
    }
}