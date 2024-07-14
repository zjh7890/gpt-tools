package com.github.zjh7890.gpttools.settings.llmSettings

import com.github.zjh7890.gpttools.llms.LlmFactory
import com.github.zjh7890.gpttools.toolWindow.chat.fullWidthCell
import com.github.zjh7890.gpttools.utils.LLMCoroutineScope
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.swing.JLabel

fun Panel.testLLMConnection(project: Project?) {
    row {
        // test result
        val result = JLabel("")
        button("Test LLM Connection") {
            if (project == null) return@button
            result.text = ""

            // test custom engine
            LLMCoroutineScope.scope(project).launch {
                try {
                    val flowString: Flow<String> = LlmFactory.instance.create(project).stream("hi", "", false)
                    flowString.collect {
                        result.text += it
                    }
                } catch (e: Exception) {
                    result.text = e.message ?: "Unknown error"
                }
            }
        }

        fullWidthCell(result)
    }
}