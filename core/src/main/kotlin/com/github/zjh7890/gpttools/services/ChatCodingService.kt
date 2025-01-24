package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.LLMCoroutineScope
import com.github.zjh7890.gpttools.agent.GenerateDiffAgent
import com.github.zjh7890.gpttools.llm.ChatMessage
import com.github.zjh7890.gpttools.llm.LlmConfig
import com.github.zjh7890.gpttools.llm.LlmProvider
import com.github.zjh7890.gpttools.settings.common.CommonSettings
import com.github.zjh7890.gpttools.toolWindow.chat.AutoDevInputTrigger
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.llmChat.ChatPanel
import com.github.zjh7890.gpttools.utils.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class ChatCodingService(val project: Project) : Disposable {
    var currentJob: Job? = null
    val sessionManager: SessionManager = project.getService(SessionManager::class.java)

    init {
        // Initialize any necessary components
    }

    /**
     * 处理用户输入的提示，并与 LLM 交互获取响应
     */
    fun handlePromptAndResponse(
        ui: ChatPanel,
        prompter: String,
        withDiff: Boolean,
        editingMessage: ChatContextMessage?,
        llmConfig: LlmConfig,
        trigger: AutoDevInputTrigger
    ) {
        val session = sessionManager.getCurrentSession()
        currentJob?.cancel()
        val projectStructure = DirectoryUtil.getDirectoryContents(project)

        var message = editingMessage
        if (editingMessage == null) {
            message = ChatContextMessage(ChatRole.user, prompter)
            val addMessage = ui.addMessage(prompter, true, prompter, null, message)
            addMessage.scrollToBottom()
            session.add(message)
        }

        if (trigger == AutoDevInputTrigger.CopyPrompt) {
            addContextToMessages(message!!, project)
            val chatHistory = sessionManager.exportChatHistory(false)
            ClipboardUtils.copyToClipboard(chatHistory)
            return
        }

        val messageView = ui.addMessage("Loading", chatMessage = null)
        messageView.scrollToBottom()

        ApplicationManager.getApplication().executeOnPooledThread {
            addContextToMessages(message!!, project)
            val messages: MutableList<ChatMessage> = session.transformMessages()
            sessionManager.saveSessions()
            ui.progressBar.isVisible = true
            ui.progressBar.isIndeterminate = true  // 设置为不确定状态
            ui.updateUI()
            val responseStream = LlmProvider.stream(messages, llmConfig = llmConfig)
            currentJob = LLMCoroutineScope.scope(project).launch {
                var text = ""
                var hasError = false  // 添加错误标志
                responseStream.onCompletion {
                    logger.warn("onCompletion ${it?.message}")
                }.catch {
                    logger.error("exception happens: ", it)
                    text = "exception happens: " + it.message.toString()
                    hasError = true  // 设置错误标志
                }.collect {
                    text += it
                    messageView.updateContent(text)
                }

                logger.warn("LLM response: ${JsonUtils.toJson(text)}")
                messageView.message = text
                messageView.reRender()

                ui.inputSection.showSendButton()
                ui.progressBar.isIndeterminate = false // 处理完成后恢复确定状态
                ui.progressBar.isVisible = false
                ui.updateUI()

                sessionManager.appendLocalMessage(ChatRole.assistant, text)
                sessionManager.saveSessions()

                // 只在没有错误时执行 GenerateDiffAgent
                if (!hasError && withDiff) {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        GenerateDiffAgent.apply(project, llmConfig, projectStructure, text, session, ui)
                    }
                }
            }
        }
    }

    /**
     * 为消息添加上下文信息，包括文件内容和项目目录结构
     */
    private fun addContextToMessages(message: ChatContextMessage, project: Project) {
        val contextBuilder = StringBuilder()

        if (CommonSettings.getInstance().withFiles && sessionManager.getCurrentSession().appFileTree.projectFileTrees.isNotEmpty()) {
            contextBuilder.append("相关项目文件内容：\n")
            val fileContents = sessionManager.getCurrentSession().appFileTree.projectFileTrees.joinToString("\n\n") { projectFileTree ->
"""
=== Project: ${projectFileTree.projectName} ===
${collectFileContents(projectFileTree.files, project).joinToString("\n\n")}
""".trimIndent()
            }
            contextBuilder.append(FileUtil.wrapBorder(fileContents))
            contextBuilder.append("\n\n")
        }

        // 添加项目目录结构（如果启用）
        if (CommonSettings.getInstance().withDir) {
            val projectStructure = DirectoryUtil.getProjectStructure(project)
            contextBuilder.append("""
        项目目录结构：
        ${FileUtil.wrapBorder(projectStructure)}
            """.trimIndent())
        }

        val context = contextBuilder.toString().trim()
        if (context.isNotBlank()) {
            message.context = context
        }
    }

    companion object {
        fun getInstance(project: Project): ChatCodingService {
            return project.getService(ChatCodingService::class.java)
        }

        private val logger = logger<ChatCodingService>()
    }
    /**
     * 停止当前正在执行的协程任务
     */
    fun stop() {
        currentJob?.cancel()
    }


    override fun dispose() {
        stop()
    }
}
