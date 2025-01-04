package com.github.zjh7890.gpttools.services

import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable

@Serializable
data class SerializableChatSession @JvmOverloads constructor(
    val id: String = "",
    val messages: MutableList<ChatContextMessage> = mutableListOf(),
    val startTime: Long = 0L,
    val type: String = "",
    val withFiles: Boolean = true,
    val projectFileTrees: List<SerializableProjectFileTree> = emptyList(),
    val projectName: String = ""
) {
    /**
     * 转换为 ChatSession 实例
     */
    fun toChatSession(project: Project): ChatSession {
        val projectFileTrees = projectFileTrees.map { it.toProjectFileTree() }.toMutableList()
        return ChatSession(
            id = id,
            messages = messages,
            startTime = startTime,
            type = type,
            withFiles = withFiles,
            projectFileTrees = projectFileTrees,
            project = projectName
        )
    }
}
