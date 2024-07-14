package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.settings.actionPrompt.CodeTemplateApplicationSettingsService
import com.github.zjh7890.gpttools.settings.actionPrompt.PromptTemplate
import com.github.zjh7890.gpttools.utils.*
import com.github.zjh7890.gpttools.utils.GitDiffUtils.parseGitDiffOutput
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.rd.util.string.println
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import java.io.File

class DiffAction : AnAction() {
    private val promptTemplate: PromptTemplate
        get() = CodeTemplateApplicationSettingsService.getInstance().state.templates[this::class.simpleName]!!

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return

        // Move the potentially long-running repository access to a background thread
        ApplicationManager.getApplication().executeOnPooledThread {
            val repositoryManager = GitUtil.getRepositoryManager(project)

            // 获取 LocalFileSystem 的实例
            val localFileSystem = LocalFileSystem.getInstance()

// 使用 project.basePath 获取 VirtualFile
            val virtualFile: VirtualFile? = localFileSystem.findFileByPath(project.basePath!!)

// 现在 virtualFile 是 VirtualFile? 类型，可以传递给 getRepositoryForRoot
            val repository = repositoryManager.getRepositoryForRoot(virtualFile)

            if (repository != null) {
                val currentBranchName = repository.currentBranchName ?: ""
                val fileChanges = updateAndFetchBranchDiff(repository, currentBranchName)
                extractAffectedMethods(project, fileChanges)
            }
        }
    }

    private fun updateAndFetchBranchDiff(repository: GitRepository, currentBranch: String): List<FileChange> {
        val project: Project = repository.project
        try {
            // Step 1: Fetch the latest changes from the remote repository
            executeGitCommand(repository, "fetch")

            // Step 2: Compare current branch with remote master branch

            var commandLine = GeneralCommandLine("git", "diff", "origin/master...$currentBranch")
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

    fun extractAffectedMethods(project: Project, fileChanges: List<FileChange>) {
        ApplicationManager.getApplication().runReadAction {
            val affectedMethods = mutableListOf<PsiMethod>()
            fileChanges.forEach { change ->
                if (change.filePath.contains("src/test") || !change.filePath.contains(".java")) {
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

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.text = promptTemplate.desc
    }
}

