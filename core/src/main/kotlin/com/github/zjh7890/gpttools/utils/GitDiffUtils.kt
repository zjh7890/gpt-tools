package com.github.zjh7890.gpttools.utils

import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

/**
 * @Author: zhujunhua
 * @Date: 2024/7/9 19:12
 */
object GitDiffUtils {
    fun parseGitDiffOutput(project: Project, fileChanges: List<FileChange>): List<FileChange> {
        val changes = mutableListOf<FileChange>()

        for (fileChange in fileChanges) {
            val patch = fileChange.filePatch

            if (patch !is TextFilePatch) continue

            // We already have a FileChange instance, so we don't need to create a new one
            var currentFileChange = fileChange

            for (hunk in patch.hunks) {
                var originalFileLine = hunk.startLineBefore - 1 // Corresponds to the original file's line number
                var currentFileLine = hunk.startLineAfter - 1 // Corresponds to the new file's line number
                var state = 0 // 1: delete, 2: modify
                var startOfAddLine = 0

                for (line in hunk.lines) {
                    when (line.type) {
                        PatchLine.Type.ADD -> {
                            if (state != 2) {
                                startOfAddLine = currentFileLine + 1
                            }
                            state = 2
                            currentFileLine++
                        }

                        PatchLine.Type.REMOVE -> {
                            if (state == 0) {
                                state = 1
                            }
                            originalFileLine++
                        }

                        else -> {
                            if (state == 1) {
                                currentFileChange.deletions.add(LineRange(currentFileLine, currentFileLine + 1))
                                state = 0
                            } else if (state == 2) {
                                currentFileChange.additions.add(LineRange(startOfAddLine, currentFileLine))
                                state = 0
                                startOfAddLine = 0
                            }
                            originalFileLine++
                            currentFileLine++
                        }
                    }
                }

                // Handle any remaining state after processing the lines
                if (state == 1) {
                    currentFileChange.deletions.add(LineRange(currentFileLine, currentFileLine + 1))
                    state = 0
                } else if (state == 2) {
                    currentFileChange.additions.add(LineRange(startOfAddLine, currentFileLine))
                    state = 0
                    startOfAddLine = 0
                }
            }

            changes.add(currentFileChange) // Add the processed FileChange to the list
        }

        return changes
    }

    fun extractAffectedMethodsLines(project: Project, fileChanges: List<FileChange>): MutableMap<String, List<PsiMethod>> {
        val map = mutableMapOf<String,List<PsiMethod>>()
        fileChanges.forEach { change ->
            if (change.filePath.contains("src/test")) {
                return@forEach
            }
            val psiFile = PsiManager.getInstance(project).findFile(project.baseDir.findFileByRelativePath(change.filePath)!!)
            psiFile?.let { file ->
                var methods = PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java)
                // methods 根据 method.textRange.startOffset  重排序
                methods = methods.sortedBy { it.textRange.startOffset }

                val affectedMethods = mutableListOf<PsiMethod>()
                map.put(change.filePath, affectedMethods)
                methods.forEach { method ->
                    val methodStartOffset = method.textRange.startOffset
                    val methodEndOffset = method.textRange.endOffset

                    change.additions.forEach { range ->
                        val startOffset = getLineStartOffset(file, range.startLine)
                        val endOffset = getLineEndOffset(file, range.endLine)
                        if (!affectedMethods.contains(method) && methodStartOffset <= endOffset && methodEndOffset >= startOffset) {
                            affectedMethods.add(method)
                        }
                    }

                    change.deletions.forEach { range ->
                        if (range.startLine == 0 || range.endLine > getLineCount(psiFile)) {
                            return@forEach
                        }
                        val startOffset = getLineStartOffset(file, range.startLine)
                        val endOffset = getLineEndOffset(file, range.endLine)
                        if (!affectedMethods.contains(method) && methodStartOffset < endOffset && methodEndOffset > startOffset) {
                            affectedMethods.add(method)
                        }
                    }
                }
            }
        }
        return map
    }

    fun extractAffectedMethodsLines(project: Project, fileChange: FileChange): List<PsiMethod> {
        val affectedMethods = mutableListOf<PsiMethod>()

        if (fileChange.filePath.contains("src/test")) {
            return affectedMethods // Skip test files
        }

        val psiFile = PsiManager.getInstance(project).findFile(project.baseDir.findFileByRelativePath(fileChange.filePath)!!)
        psiFile?.let { file ->
            var methods = PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java)
            // Sort methods by their start offset in the file
            methods = methods.sortedBy { it.textRange.startOffset }

            methods.forEach { method ->
                val methodStartOffset = method.textRange.startOffset
                val methodEndOffset = method.textRange.endOffset

                fileChange.additions.forEach { range ->
                    val startOffset = getLineStartOffset(file, range.startLine)
                    val endOffset = getLineEndOffset(file, range.endLine)
                    if (!affectedMethods.contains(method) && methodStartOffset <= endOffset && methodEndOffset >= startOffset) {
                        affectedMethods.add(method)
                    }
                }

                fileChange.deletions.forEach { range ->
                    if (range.startLine == 0 || range.endLine > getLineCount(psiFile)) {
                        return@forEach
                    }
                    val startOffset = getLineStartOffset(file, range.startLine)
                    val endOffset = getLineEndOffset(file, range.endLine)
                    if (!affectedMethods.contains(method) && methodStartOffset < endOffset && methodEndOffset > startOffset) {
                        affectedMethods.add(method)
                    }
                }
            }
        }

        return affectedMethods
    }

    fun getLineCount(psiFile: PsiFile): Int {
        val virtualFile = psiFile.virtualFile ?: return 0
        return getLineCount(virtualFile)
    }

    fun getLineCount(virtualFile: VirtualFile): Int {
        val document: Document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return 0
        return document.lineCount
    }

    fun getLineStartOffset(file: PsiFile, line: Int): Int {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return -1
        try {
            val lineStartOffset = document.getLineStartOffset(line)
            return lineStartOffset  // line - 1 because line numbers are 0-based in Document
        } catch (e: IndexOutOfBoundsException) {
            e.printStackTrace();
            throw e
        }
    }

    fun getLineEndOffset(file: PsiFile, line: Int): Int {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return -1
        try {
            val lineEndOffset = document.getLineEndOffset(line)
            return lineEndOffset  // Adjusting line number to 0-based
        } catch (e: IndexOutOfBoundsException) {
            e.printStackTrace();
            throw e
        }
    }
}



data class FileChange(
    val filePath: String,
    var changeType: String,  // create  delete  modified
    val additions: MutableList<LineRange> = mutableListOf(),
    // 可能包含第 0 行和 第 n + 1行
    val deletions: MutableList<LineRange> = mutableListOf(),

    val change: Change,  // Add non-null Change object
    val filePatch: FilePatch // Add non-null FilePatch object
)

data class LineRange(val startLine: Int, val endLine: Int)
