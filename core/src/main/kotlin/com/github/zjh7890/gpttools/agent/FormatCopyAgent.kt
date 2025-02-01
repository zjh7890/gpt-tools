package com.github.zjh7890.gpttools.agent

import com.github.zjh7890.gpttools.LLMCoroutineScope
import com.github.zjh7890.gpttools.llm.LlmConfig
import com.github.zjh7890.gpttools.llm.LlmProvider
import com.github.zjh7890.gpttools.services.AppFileTree
import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.services.ChatContextMessage
import com.github.zjh7890.gpttools.services.ChatSession
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.chat.MessageView
import com.github.zjh7890.gpttools.toolWindow.llmChat.ChatPanel
import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.JsonUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.util.*

object FormatCopyAgent {
    val logger = logger<FormatCopyAgent>()

    fun apply(
        project: Project,
        llmConfig: LlmConfig,
        projectStructure: String,
        response: String,
        currentSession: ChatSession,
        ui: ChatPanel
    ) {
        ui.progressBar.isVisible = true
        ui.progressBar.isIndeterminate = true  // 设置为不确定状态
        ui.inputSection.showStopButton()
        val border = FileUtil.determineBorder(response)
        val chatSession = ChatSession(
            id = UUID.randomUUID().toString(), type = "format",
            project = project, relevantProjects = mutableListOf(project)
        )

        chatSession.add(
            ChatContextMessage(
                ChatRole.user, """
下面是一个从网页拷贝过来的大模型的输出，请把内容换成 markdown 格式，除了格式，内容完全不变，返回结果不用 ``` 包裹。
${border}
${response}
${border}
""".trimIndent()
            )
        )

        var messageView: MessageView? = null
        // 添加一个空的消息视图用于流式更新
        ApplicationManager.getApplication().invokeAndWait {
            messageView = ui.addMessage("Formating", chatMessage = null)
            messageView!!.scrollToBottom()
        }

        val applyFlow = LlmProvider.stream(chatSession, llmConfig)
        val chatCodingService = ChatCodingService.getInstance(project)

        var responseText = ""

        chatCodingService.currentJob = LLMCoroutineScope.scope(project).launch {
            applyFlow.onCompletion {
                logger.warn("onCompletion ${it?.message}")
            }.catch {
                logger.error("exception happens: ", it)
                responseText = "exception happens: " + it.message.toString()
            }.collect {
                responseText += it
                messageView!!.updateContent(responseText)
            }

            chatCodingService.currentJob = null
            logger.warn("LLM response, GenerateDiffAgent: ${JsonUtils.toJson(responseText)}")

            // 更新最终内容
            messageView!!.message = responseText
            messageView!!.reRender()

            chatSession.add(ChatContextMessage(ChatRole.assistant, responseText))
            chatSession.exportChatHistory()
            ui.progressBar.isIndeterminate = false // 处理完成后恢复确定状态
            ui.progressBar.isVisible = false
        }
    }
}
