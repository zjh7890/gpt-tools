package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.services.SessionManager
import com.github.zjh7890.gpttools.utils.ChatUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

class AddFilesToSessionBatchAction : AnAction("Add Files to Session") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)

        if (project == null || virtualFiles == null || virtualFiles.isEmpty()) {
            Messages.showErrorDialog(project, "No files selected.", "Error")
            return
        }

        val filesToAdd = mutableListOf<VirtualFile>()
        // 递归处理所选项目
        for (file in virtualFiles) {
            if (file.isDirectory) {
                collectFiles(file, filesToAdd)
            } else {
                filesToAdd.add(file)
            }
        }

        ChatUtils.activateToolWindowRun(project) { panel, service ->
            filesToAdd.forEach {
                SessionManager.getInstance(project).addFileToCurrentSession(it)
            }
        }
    }

    private fun collectFiles(directory: VirtualFile, files: MutableList<VirtualFile>) {
        directory.children?.forEach { child ->
            if (child.isDirectory) {
                collectFiles(child, files)
            } else {
                files.add(child)
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}