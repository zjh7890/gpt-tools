package com.github.zjh7890.gpttools.utils

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.BufferedReader
import java.io.InputStreamReader

object FileUtil {
    /**
     * 读取资源文件的内容，并以字符串形式返回。
     * @param filePath 资源文件相对于资源根目录的路径。
     * @return 文件的内容字符串，如果文件找不到或无法读取，则返回默认错误消息。
     */
    fun readResourceFile(filePath: String): String {
        val classLoader = this::class.java.classLoader
        return classLoader.getResourceAsStream(filePath)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        } ?: "未找到文件默认内容"
    }

    fun readContentFromVirtualFile(virtualFile: VirtualFile?): String? {
        virtualFile ?: return null
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
        return document?.text
    }
}