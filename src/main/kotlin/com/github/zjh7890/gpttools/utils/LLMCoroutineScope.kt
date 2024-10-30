package com.github.zjh7890.gpttools.utils

import com.github.zjh7890.gpttools.services.ChatCodingService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Service(Service.Level.PROJECT)
class LLMCoroutineScope(val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + coroutineExceptionHandler)){
    companion object {
        val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Logger.getInstance(LLMCoroutineScope::class.java).error(throwable)
        }

        fun scope(project: Project): CoroutineScope = project.getService(LLMCoroutineScope::class.java).coroutineScope
    }
}
