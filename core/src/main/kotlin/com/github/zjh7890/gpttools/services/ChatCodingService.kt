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

        if (CommonSettings.getInstance().withFiles && sessionManager.getCurrentSession().projectTrees.isNotEmpty()) {
            contextBuilder.append("相关项目文件内容：\n")
            val fileContents = sessionManager.getCurrentSession().projectTrees.joinToString("\n\n") { projectFileTree ->
"""
=== Project: ${projectFileTree.projectName} ===
${collectFileContents(projectFileTree.files, project).joinToString("\n")}
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


        /**
         * 收集指定文件的内容，供 LLM 使用
         */
        fun collectFileContents(files: List<ProjectFile>, project: Project): List<String> {
            return files.mapNotNull { projectFile ->
                val virtualFile = project.baseDir.findFileByRelativePath(projectFile.fileName) ?: return@mapNotNull null

                // 如果用户配置了 whole = true，说明整文件都要
                if (projectFile.whole) {
                    // 直接读取文件内容
                    FileUtil.readFileInfoForLLM(virtualFile, project)
                } else {
                    // 同一个文件内，先把要处理的所有 PsiElement (类、方法等) 都收集到 elementsToProcess
                    val elementsToProcess = mutableListOf<PsiElement>()
                    val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@mapNotNull null

                    // 遍历用户指定的 Class/Method 信息
                    projectFile.classes.forEach { projectClass ->
                        // 查找对应的 PsiClass
                        val psiClass = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java).find {
                            if (projectClass.whole) {
                                // 如果指定了 whole = true，则用 qualifiedName 精确匹配
                                it.qualifiedName == projectClass.className
                            } else {
                                // 如果不需要整类，className 可能只写了简单类名，这里做简单匹配
                                it.name == projectClass.className
                            }
                        } ?: return@forEach

                        // 如果用户配置了对整个类都需要
                        if (projectClass.whole) {
                            // 只需要把这个类加进去一次
                            elementsToProcess.add(psiClass)
                        } else {
                            // 如果只需要其中部分方法，则逐个方法查找
                            projectClass.methods.forEach { projectMethod ->
                                val methods = psiClass.findMethodsByName(projectMethod.methodName, false)
                                // 找到参数类型匹配的方法
                                val matchedMethod = methods.find { method ->
                                    val paramTypes = method.parameterList.parameters.map { it.type.canonicalText }
                                    paramTypes == projectMethod.parameterTypes
                                } ?: return@forEach

                                elementsToProcess.add(matchedMethod)
                            }
                        }
                    }

                    // 如果本文件收集到了想要处理的 PsiElement
                    if (elementsToProcess.isNotEmpty()) {
                        // 此处只调用一次 depsInSingleFile，把当前文件内的所有类和方法一并传入
                        val fileContent = ElementsDepsInSingleFileAction.depsInSingleFile(elementsToProcess, project)
                        if (!fileContent.isNullOrBlank()) {
                            fileContent.trim()
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            }
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
