package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.utils.ClipboardUtils.copyToClipboard
import com.github.zjh7890.gpttools.utils.PsiUtils
import com.github.zjh7890.gpttools.utils.PsiUtils.findClassesFromMethod
import com.github.zjh7890.gpttools.utils.PsiUtils.generateSignature
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.impl.compiled.ClsTypeParameterImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.jar.JarFile
import java.util.stream.Collectors
import kotlin.collections.HashSet


class ClassFinderAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        val editor: Editor? = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR)
        if (project == null || editor == null) {
            Messages.showMessageDialog(project, "Project or editor not found!", "Error", Messages.getErrorIcon())
            return
        }

        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
        val currentOffset = editor.caretModel.offset
        val elementAtCaret = psiFile?.findElementAt(currentOffset)
        val method = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java)
        if (method == null) {
            RuntimeException("why").printStackTrace();
            return;
        }

        val function = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java)
        val signature = generateSignature(method, false)
        val completeSignature = generateSignature(method, true)

        val containingClass = function?.containingClass ?: return

        val newClass = containingClass.copy() as PsiClass

        newClass.methods.filter {
            generateSignature(it, false) != signature
        }.forEach { newClass.deleteChildRange(it, it) }
        newClass.fields.filterNotNull().forEach { field ->
            try {
                newClass.deleteChildRange(field, field)
            } catch (ex: Exception) {
                // 在这里处理异常，例如打印错误日志或显示错误消息
                println("Error deleting field: ${ex.message}")
            }
        }

//        com.yupaopao.platform.common.dto.Response<com.yupaopao.bixin.biggie.api.entity.BiggieInfoDTO> null.queryBiggieInfo(long)

        // Output the result, modify as needed to handle the created class
        println(newClass.text)

        if (method != null) {
            try {
                val classes = findClassesFromMethod(method, project)
                val classInfos =
                    classes.stream().map { x -> x.className }.collect(Collectors.toList()).joinToString("\n")
                val prefix = "${completeSignature}\n```\n${newClass.text}\n```\n\n"

                val result = prefix + classes.joinToString("\n")
                Messages.showMessageDialog(project, classInfos, "Class Finder Results", Messages.getInformationIcon())
                copyToClipboard(result)
            } catch (ex: Exception) {
                Messages.showMessageDialog(project, "Error finding classes: ${ex.message}", "Error", Messages.getErrorIcon())
            }
        } else {
            Messages.showMessageDialog(project, "No method found at the cursor position.", "Info", Messages.getInformationIcon())
        }
    }
}


