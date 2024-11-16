package com.github.zjh7890.gpttools.utils

import CodeChangeFile
import FileChangeItem
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.chat.block.MessageBlock
import com.github.zjh7890.gpttools.toolWindow.chat.block.MessageCodeBlockCharProcessor
import com.github.zjh7890.gpttools.toolWindow.chat.block.SimpleMessage
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

object ParseUtils {
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    fun parse(response: String): MutableList<MessageBlock> {
        val message = SimpleMessage(response, response, ChatRole.assistant)
        val processor = MessageCodeBlockCharProcessor()
        return processor.getParts(message)
    }

    fun parseCodeChanges(project: Project, textContent: String): CodeChangeFile {
        // Assume textContent is the diff content for one file.
        val fileDiff = textContent

        // Match old and new file paths
        val oldFileRegex = """---\s+([^\s]+)(?:\s+.*)?""".toRegex()
        val newFileRegex = """\+\+\+\s+([^\s]+)(?:\s+.*)?""".toRegex()

        val oldFileMatch = oldFileRegex.find(fileDiff)
        val newFileMatch = newFileRegex.find(fileDiff)

        val oldFilePath = oldFileMatch?.groups?.get(1)?.value
        val newFilePath = newFileMatch?.groups?.get(1)?.value

        // Determine changeType and filePath
        val changeType: String
        val filePath: String

        when {
            oldFilePath == "/dev/null" -> {
                changeType = "CREATE"
                filePath = newFilePath ?: "Unknown path"
            }
            newFilePath == "/dev/null" -> {
                changeType = "DELETE"
                filePath = oldFilePath ?: "Unknown path"
            }
            else -> {
                changeType = "MODIFY"
                filePath = newFilePath ?: oldFilePath ?: "Unknown path"
            }
        }

        // Remove any prefixes (like a/ or b/)
        val filePathClean = filePath.replace(Regex("^a/|^b/"), "")

        // Get filename and dirPath
        val pathParts = filePathClean.split("/").let {
            it.last() to it.dropLast(1).joinToString("/")
        }

        // Extract hunks
        val changeItems = mutableListOf<FileChangeItem>()

        // Hunk regex
        val hunkRegex = """(@@.*?@@)(.*?)(?=^@@|\Z)""".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))

        hunkRegex.findAll(fileDiff).forEach { hunkMatch ->
            val hunkHeader = hunkMatch.groups[1]?.value ?: ""
            val hunkContent = hunkMatch.groups[2]?.value ?: ""
            val originalLines = mutableListOf<String>()
            val updatedLines = mutableListOf<String>()

            hunkContent.lines().forEach { line ->
                when {
                    line.startsWith("-") -> originalLines.add(line.substring(1))
                    line.startsWith("+") -> updatedLines.add(line.substring(1))
                    line.startsWith(" ") || line.isEmpty() -> {
                        originalLines.add(line.trimStart())
                        updatedLines.add(line.trimStart())
                    }
                    else -> {
                        originalLines.add(line)
                        updatedLines.add(line)
                    }
                }
            }

            val originalChunk = originalLines.joinToString("\n")
            val updatedChunk = updatedLines.joinToString("\n")
            changeItems.add(FileChangeItem(originalChunk, updatedChunk))
        }

        // Handle CREATE or DELETE if no hunks
        if (changeItems.isEmpty() && changeType != "MODIFY") {
            val originalChunk = if (changeType == "DELETE") {
                val originalFile = LocalFileSystem.getInstance().findFileByPath(project.basePath + "/" + filePathClean)
                originalFile?.let {
                    FileDocumentManager.getInstance().getDocument(it)?.text ?: ""
                } ?: ""
            } else {
                ""
            }
            val updatedChunk = if (changeType == "CREATE") {
                val addedContent = fileDiff.lines()
                    .dropWhile { !it.startsWith("@@") }
                    .drop(1)
                    .joinToString("\n") { line ->
                        if (line.startsWith("+")) line.substring(1) else line
                    }
                addedContent
            } else {
                ""
            }
            changeItems.add(FileChangeItem(originalChunk, updatedChunk))
        }

        return CodeChangeFile(
            path = filePathClean,
            dirPath = pathParts.second,
            filename = pathParts.first,
            changeItems = changeItems,
            isMerged = false,
            changeType = changeType
        )
    }

    fun processResponse(response: String): LLMAgentResponse {
        val trimmedResponse = response.trim()
        val lines = trimmedResponse.lines()
        val shellCommands = mutableListOf<String>()
        val customCommands = mutableListOf<String>()
        var isShell = false
        var isCustom = false
        var isJson = false
        var jsonResponse = ""

        for (line in lines) {
            when {
                line.startsWith("```shell") -> {
                    isShell = true
                    isCustom = false
                    isJson = false
                }
                line.startsWith("```custom") -> {
                    isShell = false
                    isCustom = true
                    isJson = false
                }
                line.startsWith("```json") -> {
                    isShell = false
                    isCustom = false
                    isJson = true
                }
                line.startsWith("```") -> {
                    isShell = false
                    isCustom = false
                    isJson = false

                    // 当 JSON 块结束时，反序列化 JSON 响应
                    if (isJson) {
                        break
                    }
                }
                isShell -> {
                    shellCommands.add(line.trim())
                }
                isCustom -> {
                    customCommands.add(line.trim())
                }
                isJson -> {
                    // 将 JSON 内容收集起来
                    jsonResponse += line.trim()
                }
            }
        }

        return when {
            shellCommands.isNotEmpty() -> LLMAgentResponse(shellCommands = shellCommands, customCommands = null, fileList = null)
            customCommands.isNotEmpty() -> LLMAgentResponse(shellCommands = null, customCommands = customCommands, fileList = null)
            jsonResponse.isNotBlank() -> {
                // 反序列化 JSON 文件列表
                val fileList: List<String> = objectMapper.readValue(jsonResponse)
                LLMAgentResponse(shellCommands = null, customCommands = null, fileList = fileList)
            }
            else -> LLMAgentResponse(shellCommands = null, customCommands = null, fileList = null)
        }
    }
}

/**
 * 定义用于封装响应结果的内部数据类
 */
data class LLMAgentResponse(
    val shellCommands: List<String>?,
    val customCommands: List<String>?,
    val fileList: List<String>?
) {
    val isShellCommand: Boolean get() = shellCommands != null
    val isCustomCommand: Boolean get() = customCommands != null
    val isFileList: Boolean get() = fileList != null
}