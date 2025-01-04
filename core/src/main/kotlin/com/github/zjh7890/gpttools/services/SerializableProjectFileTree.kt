package com.github.zjh7890.gpttools.services

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.Serializable

@Serializable
data class SerializableProjectFileTree(
    val projectName: String = "",
    val filePaths: List<String> = emptyList()
) {
    /**
     * 转换为 ProjectFileTree 实例
     */
    fun toProjectFileTree(): ProjectFileTree {
        val files = filePaths.mapNotNull { path ->
            LocalFileSystem.getInstance().findFileByPath(path)
        }.toMutableList()
        return ProjectFileTree(
            projectName = projectName,
            files = files
        )
    }
}
