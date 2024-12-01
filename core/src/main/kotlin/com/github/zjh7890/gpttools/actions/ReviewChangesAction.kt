package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.settings.embedTemplate.EmbedTemplateSettings
import com.github.zjh7890.gpttools.utils.*
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.BinaryContentRevision
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.psi.PsiMethod
import com.intellij.util.containers.stream
import org.jetbrains.annotations.NotNull
import java.nio.file.Path
import kotlin.math.min

class ReviewChangesAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val changes = e.getData(VcsDataKeys.CHANGES)

        if (changes.isNullOrEmpty()) {
            Messages.showWarningDialog(
                project,
                "No changes selected",
                "Warning"
            )
            return
        }
        val result = doReviewWithChanges(project, changes)
        ChatUtils.setToolWindowInput(project, result);
    }
    private fun doReviewWithChanges(
        project: Project,
        changes: Array<out Change>
    ): String {
        val basePath = project.basePath ?: throw RuntimeException("Project base path is null.")
        val filteredChanges = changes.stream()
            .filter { change -> !isBinaryOrTooLarge(change!!) }
            .filter {
                // Add any other necessary filtering
                true
            }
            .toList()
        if (filteredChanges.isEmpty()) {
            return "Nothing..."
        }
        // Generate patches and map to FileChange objects
        val fileChanges = filteredChanges.subList(0, min(filteredChanges.size, 500)).map { change ->
            val patches = IdeaTextPatchBuilder.buildPatch(
                project,
                listOf(change),
                Path.of(basePath),
                false,
                false
            )
            val filePatch = patches.firstOrNull() ?: throw RuntimeException("Failed to create patch for change: $change")
            FileChange(
                filePath = filePatch.afterName,
                changeType = when {
                    filePatch.isNewFile -> "create"
                    filePatch.isDeletedFile -> "delete"
                    else -> "modified"
                },
                change = change,
                filePatch = filePatch
            )
        }
        GitDiffUtils.parseGitDiffOutput(project, fileChanges)
        val res = StringBuilder()
        for (fileChange in fileChanges) {
            val patch = fileChange.filePatch
            if (patch !is TextFilePatch) continue
            val sb = StringBuilder()
            if (fileChange.change.type == Change.Type.NEW) {
                for (line in patch.hunks[0].lines) {
                    sb.appendLine("+ ${line.text}")
                }
            } else if (fileChange.change.type == Change.Type.DELETED) {
                for (line in patch.hunks[0].lines) {
                    sb.appendLine("- ${line.text}")
                }
            } else if (fileChange.change.type == Change.Type.MODIFICATION) {
                val list = GitDiffUtils.extractAffectedMethodsLines(project, fileChange)
                val psiFile = fileChange.change.afterRevision!!.file.virtualFile!!
                val lineCount = GitDiffUtils.getLineCount(psiFile)
                if (list.isEmpty()) {
                    var prev = 0
                    for (hunk in patch.hunks) {
                        if (prev != hunk.startLineAfter - 1) {
                            sb.append("// ...\n")
                        }
                        for (line in hunk.lines) {
                            sb.appendLine(
                                when (line.type) {
                                    PatchLine.Type.CONTEXT -> "  ${line.text}"
                                    PatchLine.Type.ADD -> "+ ${line.text}"
                                    PatchLine.Type.REMOVE -> "- ${line.text}"
                                }
                            )
                        }
                    }
                    if (patch.hunks[0].endLineAfter != lineCount) {
                        sb.append("// ...\n")
                    }
                } else {
                    var i = 0
                    var method: PsiMethod? = list[i]
                    val methodStartAndEndLines = PsiUtils.getMethodStartAndEndLines(method!!)
                    var consumedLine = methodStartAndEndLines.first
                    var prev = 0
                    for (hunk in patch.hunks) {
                        if (method != null && hunk.startLineAfter > consumedLine) {
                            while (consumedLine < min(hunk.startLineAfter, methodStartAndEndLines.second)) {
                                if (prev != consumedLine - 1) {
                                    sb.append("// ...\n")
                                }
                                sb.appendLine("  " + PsiUtils.getLineContent(psiFile, consumedLine, project))
                                prev = consumedLine
                                consumedLine++
                            }
                        }
                        if (prev != hunk.startLineAfter - 1) {
                            sb.append("// ...\n")
                        }
                        for (line in hunk.lines) {
                            sb.appendLine(
                                when (line.type) {
                                    PatchLine.Type.CONTEXT -> "  ${line.text}"
                                    PatchLine.Type.ADD -> "+ ${line.text}"
                                    PatchLine.Type.REMOVE -> "- ${line.text}"
                                }
                            )
                        }
                        if (consumedLine < hunk.endLineAfter && hunk.endLineAfter < methodStartAndEndLines.second) {
                            consumedLine = hunk.endLineAfter + 1
                        } else if (hunk.endLineAfter >= methodStartAndEndLines.second) {
                            i++
                            method = if (i < list.size) list[i] else null
                        }
                    }
                    if (patch.hunks[0].endLineAfter != lineCount) {
                        sb.append("// ...\n")
                    }
                }
            } else {
                // moved
                continue
            }

            res.appendLine(fileChange.filePath)
                .appendLine("```")
                .appendLine(sb.toString().trim())
                .appendLine("```\n")
        }

        val GPT_diffCode = res.toString()

        // 从全局设置中获取模板内容
        val templateContent = EmbedTemplateSettings.instance.getStoredCodeReviewTemplate()

        val map = mapOf("GPT_diffCode" to GPT_diffCode)
        return TemplateUtils.replacePlaceholders(templateContent, map)
    }
    private fun isBinaryOrTooLarge(@NotNull change: Change): Boolean {
        return isBinaryOrTooLarge(change.beforeRevision) || isBinaryOrTooLarge(change.afterRevision)
    }
    private fun isBinaryOrTooLarge(revision: ContentRevision?): Boolean {
        val virtualFile = (revision as? CurrentContentRevision)?.virtualFile ?: return false
        return isBinaryRevision(revision) || FileUtilRt.isTooLarge(virtualFile.length)
    }
    private fun isBinaryRevision(cr: ContentRevision?): Boolean {
        if (cr == null) return false
        return when (cr) {
            is BinaryContentRevision -> true
            else -> cr.file.fileType.isBinary
        }
    }
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}