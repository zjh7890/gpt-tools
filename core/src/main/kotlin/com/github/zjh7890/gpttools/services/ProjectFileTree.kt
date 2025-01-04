package com.github.zjh7890.gpttools.services

import com.intellij.openapi.vfs.VirtualFile

data class ProjectFileTree(
    val projectName: String,
    val files: MutableList<VirtualFile> = mutableListOf()
) {
    /**
     * 转换为可序列化的文件树对象
     */
    fun toSerializable(): SerializableProjectFileTree {
        return SerializableProjectFileTree(
            projectName = projectName,
            filePaths = files.map { it.path }
        )
    }
}
