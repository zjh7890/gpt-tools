package com.github.zjh7890.gpttools.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.*
import io.ktor.utils.io.charsets.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object FileUtil {
    /**
     * 读取资源文件的内容，并以字符串形式返回。
     * @param filePath 资源文件相对于资源根目录的路径。
     * @return 文件的内容字符串，如果文件找不到或无法读取，则返回默认错误消息。
     */
    fun readResourceFile(filePath: String): String {
        val classLoader = this::class.java.classLoader
        return classLoader.getResourceAsStream(filePath)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.readText().replace("\r\n", "\n")
            }
        } ?: "未找到文件默认内容"
    }

    fun readFileInfoForLLM(virtualFile: VirtualFile?, project: Project): String {
        virtualFile ?: return ""
        var document: Document? = null
        ApplicationManager.getApplication().runReadAction {
            document = FileDocumentManager.getInstance().getDocument(virtualFile)
        }
        val text = document?.text ?: "未找到文件默认内容"
        val border = determineBorder(text)
        
        // 获取项目根目录的路径
        val projectPath = project.basePath
        // 获取文件相对于项目根目录的路径
        val relativePath = if (projectPath != null) {
            VfsUtil.getRelativePath(virtualFile, VfsUtil.findFileByIoFile(File(projectPath), true)!!) ?: virtualFile.name
        } else {
            virtualFile.name
        }
        
        return """
${relativePath}:
${border}
${text}
${border}
        """.trimIndent()
    }

    fun readFileInfoForLLM(virtualFile: VirtualFile?): String {
        virtualFile ?: return ""
        var document: Document? = null
        ApplicationManager.getApplication().runReadAction {
            document = FileDocumentManager.getInstance().getDocument(virtualFile)
        }
        val text = document?.text ?: "未找到文件默认内容"
        val border = determineBorder(text)
        return """
${border}
${text}
${border}
        """.trimIndent()
    }

    fun determineBorder(virtualFile: VirtualFile?): String {
        virtualFile ?: return "```"
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
        val text = document?.text?:"未找到文件默认内容"
        return determineBorder(text)
    }

    fun wrapBorder(fileContent: String): String {
        val border = determineBorder(fileContent)
        return "${border}\n${fileContent}\n${border}"
    }

    fun determineBorder(fileContent: String): String {
        val lines = fileContent.lines()
        var maxBackticks = 2  // Start from 2 to ensure at least 3 backticks in the border

        for (line in lines) {
            val trimmedLine = line.trimStart()
            var count = 0

            // Count the number of backticks at the start of the trimmed line
            while (count < trimmedLine.length && trimmedLine[count] == '`') {
                count++
            }

            // Update maxBackticks if a line starts with more backticks
            if (count > maxBackticks) {
                maxBackticks = count
            }
        }

        // The border should have one more backtick than the maximum found
        val borderBackticks = "`".repeat(maxBackticks + 1)
        return borderBackticks
    }

    fun readFileInfoForLLM(project: Project, fileList: List<String>): String {
        return fileList.mapNotNull { filePath ->
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(project.basePath + "/" + filePath)
            readFileInfoForLLM(virtualFile, project)
        }.joinToString("\n\n")
    }

    fun writeToFile(filePath: String, content: String) {
        val file = File(filePath)

        // 确保文件的父目录存在，如果不存在则创建
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }

        // 写入内容到文件
        file.writeText(content)


        
    }

    inline fun <reified T> readJsonFromFile(filePath: String): T? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            val content = file.readText()
            JsonUtils.parse(content, object : com.fasterxml.jackson.core.type.TypeReference<T>() {})
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun writeJsonToFile(filePath: String, data: Any) {
        try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            val jsonString = JsonUtils.toJsonByFormat(data)
            file.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}