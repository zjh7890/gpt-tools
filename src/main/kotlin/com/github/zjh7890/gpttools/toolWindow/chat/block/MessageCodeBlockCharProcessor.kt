// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.github.zjh7890.gpttools.toolWindow.chat.block

import com.github.zjh7890.gpttools.toolWindow.chat.MessageBlockType

enum class BorderType {
    START,
    END
}

class Parameters(val char: Char, val charIndex: Int, val fullMessage: String)
class ContextChange(@JvmField val contextType: MessageBlockType, @JvmField val borderType: BorderType)

class MessageCodeBlockCharProcessor {
    private val codeBlockChar: Char = '`'
    private val borderBlock: String = "```"
    private val changeStartBlock: String = "repo-relative-path-for-gpt-tools"

    fun suggestTypeChange(
        parameters: Parameters,
        currentContextType: MessageBlockType,
        blockStart: Int
    ): ContextChange? {
        if (parameters.char != codeBlockChar
            && parameters.char != '\n'
            && parameters.char != '-'
            && parameters.char != changeStartBlock[0]) return null

        // 检测代码更改区块的开始和结束
        when (currentContextType) {
            MessageBlockType.PlainText -> {
                if (isChangeBlockStart(parameters.fullMessage, parameters.charIndex)) {
                    return ContextChange(MessageBlockType.CodeChange, BorderType.START)
                } else if (isCodeBlockStart(parameters)) {
                    return ContextChange(MessageBlockType.CodeEditor, BorderType.START)
                }
            }

            MessageBlockType.CodeChange -> {
                if (isChangeBlockEnd(parameters.fullMessage, parameters.charIndex, blockStart)) {
                    return ContextChange(MessageBlockType.CodeChange, BorderType.END)
                }
            }

            MessageBlockType.CodeEditor -> {
                if (isCodeBlockEnd(parameters, blockStart)) {
                    return ContextChange(MessageBlockType.CodeEditor, BorderType.END)
                }
            }
        }
        return null
    }

    private fun isCodeBlockEnd(parameters: Parameters, blockStart: Int): Boolean {
        if (parameters.charIndex - blockStart < 5) {
            return false
        }
        val fullMessage = parameters.fullMessage
        val charIndex = parameters.charIndex
        return when {
            parameters.char == codeBlockChar && charIndex == fullMessage.length - 1 -> {
                val subSequence = fullMessage.subSequence(charIndex - 3, charIndex + 1)
                subSequence == "\n$borderBlock"
            }

            parameters.char == '\n' && (charIndex - 3) - 1 >= 0 -> {
                val subSequence = fullMessage.subSequence(charIndex - 4, charIndex)
                subSequence == "\n$borderBlock"
            }

            else -> false
        }
    }

    private fun isCodeBlockStart(parameters: Parameters): Boolean {
        if (parameters.char == codeBlockChar && parameters.charIndex + 3 < parameters.fullMessage.length) {
            val isLineStart = parameters.charIndex == 0 || parameters.fullMessage[parameters.charIndex - 1] == '\n'
            if (isLineStart) {
                val subSequence = parameters.fullMessage.subSequence(parameters.charIndex, parameters.charIndex + 3)
                return subSequence.all { it == codeBlockChar }
            }
        }
        return false
    }

    // 检测代码更改区块的开始
    private fun isChangeBlockStart(fullMessage: String, index: Int): Boolean {
        // 确保在字符串的边界内查找
        if (index + changeStartBlock.length > fullMessage.length) return false
        return fullMessage.regionMatches(index, changeStartBlock, 0, changeStartBlock.length, ignoreCase = true)
    }

    private fun isChangeBlockEnd(fullMessage: String, index: Int, blockStart: Int): Boolean {
        // 确保在字符串的边界内查找
        // 从 index 向前查找，检查是否匹配 changeEndBlock
        val regex = "-----\n(.*?)-----".toRegex(setOf(RegexOption.DOT_MATCHES_ALL))
        val matchResult = regex.find(fullMessage.substring(blockStart, index + 1))
        return matchResult != null
    }
}