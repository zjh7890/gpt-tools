package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.settings.actionPrompt.PromptTemplate
import com.github.zjh7890.gpttools.utils.FileChange
import com.github.zjh7890.gpttools.utils.GitDiffUtils.extractAffectedMethods
import com.github.zjh7890.gpttools.utils.GitDiffUtils.parseGitDiffOutput
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.repo.GitRepository
import java.io.File

class DiffAction(val promptTemplate: PromptTemplate) : AnAction() {
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
                val fileChanges = updateAndFetchBranchDiff(repository, currentBranchName, "origin/master")
                extractAffectedMethods(project, fileChanges, promptTemplate)
            }
        }
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

