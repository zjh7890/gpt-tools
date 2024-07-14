package com.github.zjh7890.gpttools.services

import com.azure.ai.openai.models.ChatRequestMessage
import com.github.zjh7890.gpttools.llms.LlmFactory
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolPanel
import com.github.zjh7890.gpttools.utils.LLMCoroutineScope
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Service
class ChatCodingService(val project: Project) {
    private val llmProvider = LlmFactory().create(project)
    private var currentJob: Job? = null

    fun stop() {
        currentJob?.cancel()
    }

    fun handlePromptAndResponse(
        ui: LLMChatToolPanel,
        prompter: String,
        keepHistory: Boolean
    ) {
        currentJob?.cancel()
        var requestPrompt = prompter
        var displayPrompt = prompter

        ui.addMessage(requestPrompt, true, displayPrompt)
        ui.addMessage("Loading")

        ApplicationManager.getApplication().executeOnPooledThread {
            val response = this.makeChatBotRequest(requestPrompt, keepHistory)
            currentJob = LLMCoroutineScope.scope(project).launch {
                ui.updateMessage(response)
            }
        }
    }

    fun handleMsgsAndResponse(
        ui: LLMChatToolPanel,
        messages: List<ChatRequestMessage>,
    ) {
        val requestPrompt = "requestPrompt";
        val systemPrompt = "systemPrompt";

        ui.addMessage(requestPrompt, true, requestPrompt)
        ui.addMessage("Loading...")

        ApplicationManager.getApplication().executeOnPooledThread {
            val response = llmProvider.stream(requestPrompt, systemPrompt)

            currentJob = LLMCoroutineScope.scope(project).launch {
                ui.updateMessage(response)
            }
        }
    }

    private fun makeChatBotRequest(requestPrompt: String, newChatContext: Boolean): Flow<String> {
        return llmProvider.stream(requestPrompt, "", keepHistory = !newChatContext)
    }

    fun clearSession() {
        llmProvider.clearMessage()
    }

    companion object {
        fun getInstance(project: Project): ChatCodingService = project.service()
    }
}
