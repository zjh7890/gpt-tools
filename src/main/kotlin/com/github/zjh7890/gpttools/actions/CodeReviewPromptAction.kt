package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.settings.actionPrompt.PromptTemplate
import com.github.zjh7890.gpttools.toolWindow.treePanel.FileTreeListPanel
import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.github.zjh7890.gpttools.utils.FileChange
import com.github.zjh7890.gpttools.utils.GitDiffUtils.extractAffectedMethodsLines
import com.github.zjh7890.gpttools.utils.GitDiffUtils.getLineCount
import com.github.zjh7890.gpttools.utils.GitDiffUtils.parseGitDiffOutput
import com.github.zjh7890.gpttools.utils.PsiUtils
import com.github.zjh7890.gpttools.utils.PsiUtils.getMethodStartAndEndLines
import com.github.zjh7890.gpttools.utils.TemplateUtils
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diff.impl.patch.*
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.*
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.util.containers.stream
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogDataKeys
import git4idea.GitUtil
import git4idea.changes.filePath
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.repo.GitRepository
import org.jetbrains.annotations.NotNull
import java.io.File
import java.nio.file.Path
import kotlin.math.min

class CodeReviewPromptAction(val promptTemplate: PromptTemplate) : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        // Make changes available for diff action
        val vcsLog = event.getData(VcsLogDataKeys.VCS_LOG)
        val details: List<VcsFullCommitDetails> = vcsLog?.selectedDetails?.toList() ?: return
        val selectList = event.getData(VcsDataKeys.SELECTED_CHANGES) ?: return

        ProgressManager.getInstance().runProcessWithProgressSynchronously(Runnable {

            ApplicationManager.getApplication().runReadAction {
                val repositoryManager: VcsRepositoryManager = VcsRepositoryManager.getInstance(project)
                val repository = repositoryManager.getRepositoryForFile(project.baseDir)

                if (repository == null) {
                    Messages.showMessageDialog(project, "Repository not found!", "Error", Messages.getErrorIcon())
                    return@runReadAction
                }
            }

        }, "Prepare Repository", true, project)
        doReviewWithChanges(project, details, selectList)
    }

    open fun doReviewWithChanges(
        project: Project,
        details: List<VcsFullCommitDetails>,
        changes: Array<out Change>
    ) {
        val basePath = project.basePath ?: throw RuntimeException("Project base path is null.")
        val filteredChanges = changes.stream()
            .filter { change -> !isBinaryOrTooLarge(change!!) }
            .filter {
//                val filePath = it.afterRevision?.file
//                if (filePath != null) {
//                    ignoreFilePatterns.none { pattern ->
//                        pattern.matches(Path.of(it.afterRevision!!.file.path))
//                    }
//                } else {
//                    true
//                }
                true
            }
            .toList()
        if (filteredChanges.isEmpty()) {
            return
        }

        val patches = IdeaTextPatchBuilder.buildPatch(
            project,
            filteredChanges.subList(0, min(filteredChanges.size, 500)),
            Path.of(basePath),
            false,
            true
        )

        val diffOutput = parseGitDiffOutput(project, patches)
        val methodsLinesMap = extractAffectedMethodsLines(project, diffOutput)
//        val res = mutableListOf<String>()
        val res = StringBuilder()
        for (patch in patches) {
            if (patch !is TextFilePatch) {
                continue
            }
            val psiFile = PsiManager.getInstance(project).findFile(project.baseDir.findFileByRelativePath(patch.filePath)!!)
            val lineCount = getLineCount(psiFile!!)
            val list = methodsLinesMap.get(patch.filePath)
            val sb = StringBuilder()
            if (list.isNullOrEmpty()) {
                var prev = 0
                // 不需要特殊处理
                for (hunk in patch.hunks) {
                    if (prev != hunk.startLineAfter - 1) {
                        sb.append("// ...\n")
                    }
                    for (line in hunk.lines) {
                        if (line.type == PatchLine.Type.CONTEXT) {
                            sb.appendLine("  " + line.text)
                        } else if (line.type == PatchLine.Type.ADD) {
                            sb.appendLine("+ " + line.text)
                        } else if (line.type == PatchLine.Type.REMOVE) {
                            sb.appendLine("- " + line.text)
                        }
                    }
                }
                if (patch.hunks.get(0).endLineAfter != lineCount) {
                    sb.append("// ...\n")
                }
            } else {
                var i = 0
                var method : PsiMethod? = list.get(i)
                val methodStartAndEndLines = getMethodStartAndEndLines(method!!)
                var consumedLine = methodStartAndEndLines.first
                var prev = 0
                for (hunk in patch.hunks) {
                    if (method != null && hunk.startLineAfter >  consumedLine) {
                        while (consumedLine < Math.min(hunk.startLineAfter, methodStartAndEndLines.second)) {
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
                        if (line.type == PatchLine.Type.CONTEXT) {
                            sb.appendLine("  " + line.text)
                        } else if (line.type == PatchLine.Type.ADD) {
                            sb.appendLine("+ " + line.text)
                        } else if (line.type == PatchLine.Type.REMOVE) {
                            sb.appendLine("- " + line.text)
                        }
                    }
                    if (consumedLine < hunk.endLineAfter && hunk.endLineAfter < methodStartAndEndLines.second) {
                        consumedLine = hunk.endLineAfter + 1
                    } else if (hunk.endLineAfter >= methodStartAndEndLines.second) {
                        i++
                        if (i < list.size) {
                            method = list.get(i)
                        } else {
                            method = null
                        }
                    }
                }
                if (patch.hunks.get(0).endLineAfter != lineCount) {
                    sb.append("// ...\n")
                }
            }
            res.appendLine(patch.filePath)
            res.appendLine("```")
                .appendLine(sb.toString().trim())
                .appendLine("```\n")
        }
        val GPT_diffCode = res.toString()
        val map = mapOf(
            "GPT_diffCode" to GPT_diffCode
        )
        val result = TemplateUtils.replacePlaceholders(promptTemplate.value, map)
        ClipboardUtils.copyToClipboard(result)
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

    fun getChangesBetweenCommits(project: Project, commitHash1: String, commitHash2: String): List<Change>? {
        val repositoryManager = GitUtil.getRepositoryManager(project)
        val repository = repositoryManager.repositories.firstOrNull() // 获取第一个仓库，或者适当选择仓库

//        if (repository != null) {
//            val hash1 = HashImpl.build(commitHash1)
//            val hash2 = HashImpl.build(commitHash2)
//
//            return try {
//
////                GitHistoryUtils.getDiff(project, repository.root, commit1, commit2).allChanges
////                GitHistoryUtils.collectChangesBetweenCommits(hash1, hash2, repository)
//            } catch (e: Exception) {
//                e.printStackTrace()
//                null
//            }
//        } else {
//            return null
//        }
        return null
    }


    private fun updateAndFetchBranchDiff(repository: GitRepository, currentBranch: String, baseBranch: String): List<FileChange> {
        val project: Project = repository.project
        try {
            // Step 1: Fetch the latest changes from the remote repository
            executeGitCommand(repository, "fetch")

            // Step 2: Compare current branch with remote master branch

            var commandLine = GeneralCommandLine("git", "diff", "$baseBranch...$currentBranch")
            commandLine.workDirectory = File(repository.root.path)
            var processHandler = OSProcessHandler(commandLine)
            val stringBuilder = StringBuilder()
            processHandler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    stringBuilder.append(event.text)
                }
            })
            processHandler.startNotify()
            processHandler.waitFor()

            val fileChanges = parseGitDiffOutput(stringBuilder.toString())

            // Do something with fileChanges, like displaying them in the UI or logging
            fileChanges.forEach { change ->
//                println("File: ${change.filePath}, Type: ${change.changeType}, Additions: ${change.additions.size}, Deletions: ${change.deletions.size}")
            }

            return fileChanges
        } catch (e: Exception) {
            throw e
        }
    }

    private fun executeGitCommand(repository: GitRepository, vararg command: String) {
        val commandLine = GeneralCommandLine("git", *command)
        commandLine.workDirectory = File(repository.root.path)

        val processHandler = OSProcessHandler(commandLine)
        processHandler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                println("Git command output: ${event.text}")
            }
        })
        processHandler.startNotify()
        processHandler.waitFor()
    }

    private fun executeGitCommand(project: Project, repository: GitRepository, vararg command: String) {
        // 获取 Git 实例
        val git = Git.getInstance()

        // 将字符串命令转换为 GitCommand，注意这需要你的命令确实存在于 GitCommand 枚举中
        val gitCommand = GitCommand.FETCH
        val arguments = command.drop(1)

        // 执行命令
        val result: GitCommandResult = git.runCommand {
            git4idea.commands.GitLineHandler(project, repository.root, gitCommand).apply {
                addParameters(arguments)
            }
        }

        // 处理命令执行结果
        if (result.success()) {
            println("Command executed successfully.")
            result.output.forEach { println(it) }
        } else {
            println("Command execution failed: ${result.errorOutput.joinToString("\n")}")
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.text = promptTemplate.desc
    }
}

