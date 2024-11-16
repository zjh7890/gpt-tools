package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.services.ChatCodingService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod

class AddToSessionFileListAction : AnAction("Add to Session File List") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val virtualFile: VirtualFile? = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)
        
        if (project == null || virtualFile == null) {
            Messages.showErrorDialog(project, "No file selected.", "Error")
            return
        }

        val chatCodingService = ChatCodingService.getInstance(project)
        
        if (editor != null && psiFile != null) {
            val offset = editor.caretModel.offset
            val element = psiFile.findElementAt(offset)
            
            // 获取引用的目标元素
            val reference = element?.parent?.reference
            val resolvedElement = reference?.resolve()
            
            if (resolvedElement is PsiClass || resolvedElement is PsiMethod) {
                // 如果引用指向 PsiClass 或 PsiMethod，添加该元素所在的文件
                chatCodingService.addFileToCurrentSession(resolvedElement.containingFile.virtualFile)
            } else {
                // 如果不是指向 PsiClass 或 PsiMethod 的引用，添加右键点击的文件
                chatCodingService.addFileToCurrentSession(virtualFile)
            }
        } else {
            // 如果没有编辑器上下文，直接添加右键点击的文件
            chatCodingService.addFileToCurrentSession(virtualFile)
        }
    }
}