package com.github.zjh7890.gpttools.llms

import com.github.zjh7890.gpttools.llms.llm.OpenAIProvider
import com.github.zjh7890.gpttools.settings.llmSettings.AIEngines
import com.github.zjh7890.gpttools.settings.llmSettings.GptToolSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service
class LlmFactory {
    private val aiEngine: AIEngines
        get() = AIEngines.values()
            .find { it.name.lowercase() == GptToolSettings.getInstance().aiEngine.lowercase() } ?: AIEngines.OpenAI

    fun create(project: Project): LLMProvider {
        return project.getService(OpenAIProvider::class.java)
    }

    companion object {
        val instance: LlmFactory = LlmFactory()
    }
}
