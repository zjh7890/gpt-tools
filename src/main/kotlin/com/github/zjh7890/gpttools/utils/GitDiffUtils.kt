package com.github.zjh7890.gpttools.utils

import com.github.zjh7890.gpttools.settings.actionPrompt.PromptTemplate
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
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
    fun parseGitDiffOutput(diffOutput: String): List<FileChange> {
        val changes = mutableListOf<FileChange>()
        var currentFileChange: FileChange? = null
        var originalFileLine = 0 // 对应原始文件的行
        var currentFileLine = 0 // 对应新文件的行
        var state = 0 // 1 delete, 2 modify
        var startOfAddLine = 0

        diffOutput.lineSequence().forEach { line ->
            when {
                line.startsWith("diff --git") -> {
                    if (state == 1) {
                        currentFileChange?.deletions?.add(LineRange(currentFileLine, currentFileLine + 1))
                        state = 0
                    } else if (state == 2) {
                        currentFileChange?.additions?.add(LineRange(startOfAddLine, currentFileLine))
                        state = 0
                        startOfAddLine = 0
                    }
                    currentFileChange?.let { changes.add(it) }
                    val filePath = line.substringAfter(" b/").trim()
                    currentFileChange = FileChange(filePath, "modified") // Default to modified unless stated otherwise
                }
                line.startsWith("+++") -> {}
                line.startsWith("---") -> {}
                line.startsWith("@@ ") -> {
                    // Extract start line from diff range information
                    val regex = """@@ -(\d+),\d+ \+(\d+),\d+ @@""".toRegex()
                    val matchResult = regex.find(line)
                    val (originalFileLineStr, currentFileLineStr) = matchResult!!.destructured
                    // 赋值给全局变量
                    originalFileLine = originalFileLineStr.toInt() - 1
                    currentFileLine = currentFileLineStr.toInt() - 1
//                    currentStartLine = lineInfo.substring(1).split(",")[0].toInt()
                }

                line.startsWith("+") && !line.startsWith("+++") -> {
                    if (state != 2) {
                        startOfAddLine = currentFileLine + 1
                    }
                    state = 2
                    currentFileLine++
                }

                line.startsWith("-") && !line.startsWith("---") -> {
                    if (state == 0) {
                        state = 1
                    }
                    originalFileLine++
                }

                else -> {
                    if (state == 1) {
                        currentFileChange?.deletions?.add(LineRange(currentFileLine, currentFileLine + 1))
                        state = 0
                    } else if (state == 2) {
                        currentFileChange?.additions?.add(LineRange(startOfAddLine, currentFileLine))
                        state = 0
                        startOfAddLine = 0
                    }
                    originalFileLine++
                    currentFileLine++
                }
            }
        }

        if (state == 1) {
            currentFileChange?.deletions?.add(LineRange(currentFileLine, currentFileLine + 1))
            state = 0
        } else if (state == 2) {
            currentFileChange?.additions?.add(LineRange(startOfAddLine, currentFileLine))
            state = 0
            startOfAddLine = 0
        }

        currentFileChange?.let { changes.add(it) } // Add the last parsed file
        return changes
    }

    fun parseGitDiffOutput(patchs: List<FilePatch>): List<FileChange> {
        val changes = mutableListOf<FileChange>()

        for (patch in patchs) {
            if (patch !is TextFilePatch) {
                continue
            }
            var currentFileChange: FileChange = FileChange(patch.afterName, "modified")
            for (hunk in patch.hunks) {
                var originalFileLine = hunk.startLineBefore - 1 // 对应原始文件的行
                var currentFileLine = hunk.startLineAfter - 1 // 对应新文件的行
                var state = 0 // 1 delete, 2 modify
                var startOfAddLine = 0
                for (line in hunk.lines) {
                    when {
                        line.type == PatchLine.Type.ADD -> {
                            if (state != 2) {
                                startOfAddLine = currentFileLine + 1
                            }
                            state = 2
                            currentFileLine++
                        }

                        line.type == PatchLine.Type.REMOVE -> {
                            if (state == 0) {
                                state = 1
                            }
                            originalFileLine++
                        }

                        else -> {
                            if (state == 1) {
                                currentFileChange?.deletions?.add(LineRange(currentFileLine, currentFileLine + 1))
                                state = 0
                            } else if (state == 2) {
                                currentFileChange?.additions?.add(LineRange(startOfAddLine, currentFileLine))
                                state = 0
                                startOfAddLine = 0
                            }
                            originalFileLine++
                            currentFileLine++
                        }
                    }
                }
                if (state == 1) {
                    currentFileChange?.deletions?.add(LineRange(currentFileLine, currentFileLine + 1))
                    state = 0
                } else if (state == 2) {
                    currentFileChange?.additions?.add(LineRange(startOfAddLine, currentFileLine))
                    state = 0
                    startOfAddLine = 0
                }
                currentFileChange?.let { changes.add(it) } // Add the last parsed file
            }
        }

        return changes
    }

    fun extractAffectedMethodsLines(project: Project, fileChanges: List<FileChange>, promptTemplate: PromptTemplate): MutableMap<String, List<PsiMethod>> {
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

    fun extractAffectedMethods(project: Project, fileChanges: List<FileChange>, promptTemplate: PromptTemplate) {
        ApplicationManager.getApplication().runReadAction {
            val affectedMethods = mutableListOf<PsiMethod>()
            fileChanges.forEach { change ->
                if (change.filePath.contains("src/test")) {
                    return@forEach
                }
                val psiFile = PsiManager.getInstance(project).findFile(project.baseDir.findFileByRelativePath(change.filePath)!!)
                psiFile?.let { file ->
                    val methods = PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java)
                    methods.forEach { method ->
                        val methodStartOffset = method.textRange.startOffset
                        val methodEndOffset = method.textRange.endOffset

                        change.additions.forEach { range ->
                            val startOffset = getLineStartOffset(file, range.startLine)
                            val endOffset = getLineEndOffset(file, range.endLine)
                            if (methodStartOffset <= endOffset && methodEndOffset >= startOffset) {
                                affectedMethods.add(method)
                            }
                        }

                        change.deletions.forEach { range ->
                            if (range.startLine == 0 || range.endLine > getLineCount(psiFile)) {
                                return@forEach
                            }
                            val startOffset = getLineStartOffset(file, range.startLine)
                            val endOffset = getLineEndOffset(file, range.endLine)
                            if (methodStartOffset < endOffset && methodEndOffset > startOffset) {
                                println("Affected Method: ${method.name}")
                            }
                        }
                    }
                }
            }
            // affectedMethods 去重
            val uniqueAffectedMethods = affectedMethods.distinctBy { it }
            val sb : StringBuilder = StringBuilder()
            uniqueAffectedMethods.forEach {
                sb.append("```\n").append(it.text).append("\n```\n\n")
            }

            val GPT_diffCode = sb.toString()
            val map = mapOf(
                "GPT_diffCode" to GPT_diffCode
            )
            val result = TemplateUtils.replacePlaceholders(promptTemplate.value, map)
            ClipboardUtils.copyToClipboard(result)
        }
    }

    fun getLineCount(psiFile: PsiFile): Int {
        val virtualFile = psiFile.virtualFile ?: return 0
        val document: Document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return 0
        return document.lineCount
    }

    fun getLineStartOffset(file: PsiFile, line: Int): Int {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return -1
        try {
            val lineStartOffset = document.getLineStartOffset(line - 1)
            return lineStartOffset  // line - 1 because line numbers are 0-based in Document
        } catch (e: IndexOutOfBoundsException) {
            e.printStackTrace();
            throw e
        }
    }

    fun getLineEndOffset(file: PsiFile, line: Int): Int {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return -1
        try {
            val lineEndOffset = document.getLineEndOffset(line - 1)
            return lineEndOffset  // Adjusting line number to 0-based
        } catch (e: IndexOutOfBoundsException) {
            e.printStackTrace();
            throw e
        }
    }
}

data class FileChange(
    val filePath: String,
    var changeType: String,
    val additions: MutableList<LineRange> = mutableListOf(),
    // 可能包含第 0 行和 第 n + 1行
    val deletions: MutableList<LineRange> = mutableListOf()
)

data class LineRange(val startLine: Int, val endLine: Int)
