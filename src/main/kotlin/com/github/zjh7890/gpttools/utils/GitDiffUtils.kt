package com.github.zjh7890.gpttools.utils


/**
 * @Author: zhujunhua
 * @Date: 2024/7/9 19:12
 */
object GitDiffUtils {
    fun parseGitDiffOutput(diffOutput: String): List<FileChange> {
        val changes = mutableListOf<FileChange>()
        var currentFileChange: FileChange? = null
        var originalFileLine = 0 // 对应原始文件的行
        var currentFileLine = 0 // 对应新文件的行
        var state = 0 // 1 delete, 2 modify
        var startOfAddLine = 0

        diffOutput.lineSequence().forEach { line ->
            when {
                line.startsWith("diff --git") -> {
                    if (state == 1) {
                        currentFileChange?.deletions?.add(LineRange(currentFileLine, currentFileLine + 1))
                        state = 0
                    } else if (state == 2) {
                        currentFileChange?.additions?.add(LineRange(startOfAddLine, currentFileLine))
                        state = 0
                        startOfAddLine = 0
                    }
                    currentFileChange?.let { changes.add(it) }
                    val filePath = line.substringAfter(" b/").trim()
                    currentFileChange = FileChange(filePath, "modified") // Default to modified unless stated otherwise
                }
                line.startsWith("+++") -> {}
                line.startsWith("---") -> {}
                line.startsWith("@@ ") -> {
                    // Extract start line from diff range information
                    val regex = """@@ -(\d+),\d+ \+(\d+),\d+ @@""".toRegex()
                    val matchResult = regex.find(line)
                    val (originalFileLineStr, currentFileLineStr) = matchResult!!.destructured
                    // 赋值给全局变量
                    originalFileLine = originalFileLineStr.toInt() - 1
                    currentFileLine = currentFileLineStr.toInt() - 1
//                    currentStartLine = lineInfo.substring(1).split(",")[0].toInt()
                }

                line.startsWith("+") && !line.startsWith("+++") -> {
                    if (state != 2) {
                        startOfAddLine = currentFileLine + 1
                    }
                    state = 2
                    currentFileLine++
                }

                line.startsWith("-") && !line.startsWith("---") -> {
                    if (state == 0) {
                        state = 1
                    }
                    originalFileLine++
                }

                else -> {
                    if (state == 1) {
                        currentFileChange?.deletions?.add(LineRange(currentFileLine, currentFileLine + 1))
                        state = 0
                    } else if (state == 2) {
                        currentFileChange?.additions?.add(LineRange(startOfAddLine, currentFileLine))
                        state = 0
                        startOfAddLine = 0
                    }
                    originalFileLine++
                    currentFileLine++
                }
            }
        }

        if (state == 1) {
            currentFileChange?.deletions?.add(LineRange(currentFileLine, currentFileLine + 1))
            state = 0
        } else if (state == 2) {
            currentFileChange?.additions?.add(LineRange(startOfAddLine, currentFileLine))
            state = 0
            startOfAddLine = 0
        }

        currentFileChange?.let { changes.add(it) } // Add the last parsed file
        return changes
    }
}

data class FileChange(
    val filePath: String,
    var changeType: String,
    val additions: MutableList<LineRange> = mutableListOf(),
    // 可能包含第 0 行和 第 n + 1行
    val deletions: MutableList<LineRange> = mutableListOf()
)

data class LineRange(val startLine: Int, val endLine: Int)
