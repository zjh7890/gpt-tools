package com.github.zjh7890.gpttools.toolWindow.chat.block

import com.github.zjh7890.gpttools.toolWindow.chat.MessageBlockType
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger

class MessageCodeBlockCharProcessor {
    private val codeBlockChar: Char = '`'
    private val changeBlockChar: Char = '-'
    private val changeStartBlock: String = "----- CHANGES START -----"
    private val changeEndBlock: String = "----- CHANGES END -----"
    private val logger = logger<MessageCodeBlockCharProcessor>()

    fun getParts(message: CompletableMessage): MutableList<MessageBlock> {
        val messageText = message.text
        val fullMessage = messageText
        val parts = mutableListOf<MessageBlock>()
        var index = 0
        var blockStart = 0
        var contextType = MessageBlockType.PlainText
        var currentBorderBacktickCount = 0

        while (index < messageText.length) {
            val char = messageText[index]

            // 对于非特殊字符，直接跳过
            if (char != codeBlockChar && char != changeBlockChar) {
                index++
                continue
            }

            when (contextType) {
                MessageBlockType.PlainText -> {
                    if (isChangeBlockStart(fullMessage, index)) {
                        contextType = MessageBlockType.CodeChange

                        // 处理从 PlainText 到 CodeChange 的转换
                        if (index > blockStart) {
                            pushPart(
                                blockStart,
                                messageText,
                                MessageBlockType.PlainText,
                                message,
                                parts,
                                index - 1,
                                currentBorderBacktickCount
                            )
                        }
                        blockStart = index

                        // 跳过变化块开始标记的长度
                        index += changeStartBlock.length
                        continue

                    } else {
                        val backtickCount = isCodeBlockStart(fullMessage, index)
                        if (backtickCount != null) {
                            contextType = MessageBlockType.CodeEditor
                            currentBorderBacktickCount = backtickCount

                            // 处理从 PlainText 到 CodeEditor 的转换
                            if (index > blockStart) {
                                pushPart(
                                    blockStart,
                                    messageText,
                                    MessageBlockType.PlainText,
                                    message,
                                    parts,
                                    index - 1,
                                    currentBorderBacktickCount
                                )
                            }
                            blockStart = index

                            // 跳过反引号的长度
                            index += backtickCount
                            continue
                        } else {
                            index++
                        }
                    }
                }

                MessageBlockType.CodeChange -> {
                    if (isChangeBlockEnd(fullMessage, index)) {
                        contextType = MessageBlockType.PlainText

                        // 跳过变化块结束标记的长度
                        index += changeEndBlock.length
                        // 处理从 CodeChange 到 PlainText 的转换
                        pushPart(
                            blockStart,
                            messageText,
                            MessageBlockType.CodeChange,
                            message,
                            parts,
                            index - 1,
                            currentBorderBacktickCount
                        )
                        blockStart = index
                        continue

                    } else if (isChangeBlockStart(fullMessage, index)) {
                        // 已经在 CodeChange 中，不能再次开始
                        logger.error("Type change suggests starting $contextType while it's already open")
                        index += changeStartBlock.length
                        continue
                    } else {
                        index++
                    }
                }

                MessageBlockType.CodeEditor -> {
                    if (isCodeBlockEnd(fullMessage, index, currentBorderBacktickCount)) {
                        contextType = MessageBlockType.PlainText

                        // 跳过反引号的长度
                        index += currentBorderBacktickCount
                        // 处理从 CodeEditor 到 PlainText 的转换
                        pushPart(blockStart, messageText, MessageBlockType.CodeEditor, message, parts, index - 1, currentBorderBacktickCount)
                        blockStart = index
                        continue
                    } else {
                        index++
                    }
                }
            }
        }

        if (blockStart < messageText.length) {
            pushPart(
                blockStart,
                messageText,
                contextType,
                message,
                parts,
                messageText.length - 1,
                currentBorderBacktickCount
            )
        }

        return parts
    }

    private fun pushPart(
        blockStart: Int,
        messageText: String,
        contextType: MessageBlockType,
        message: CompletableMessage,
        list: MutableList<MessageBlock>,
        partUpperOffset: Int,
        currentBorderBacktickCount: Int
    ) {
        val newPart = createPart(blockStart, partUpperOffset, messageText, contextType, message, currentBorderBacktickCount)
        list.add(newPart)
    }

    private fun isCodeBlockStart(fullMessage: String, charIndex: Int): Int? {
        if (fullMessage[charIndex] != codeBlockChar) return null

        // 检查是否为行首
        val isLineStart = charIndex == 0 || fullMessage[charIndex - 1] == '\n'
        if (!isLineStart) return null

        // 检测连续的反引号数量
        var backtickCount = 0
        var index = charIndex
        while (index < fullMessage.length && fullMessage[index] == codeBlockChar) {
            backtickCount++
            index++
        }

        // 至少需要3个反引号才能开始一个代码块
        return if (backtickCount >= 3) {
            backtickCount
        } else {
            null
        }
    }

    private fun isCodeBlockEnd(fullMessage: String, charIndex: Int, currentBorderBacktickCount: Int): Boolean {
        if (fullMessage[charIndex] != codeBlockChar) return false

        // 检查是否为行首
        val isLineStart = charIndex == 0 || fullMessage[charIndex - 1] == '\n'
        if (!isLineStart) return false

        // 检测连续的反引号数量
        var backtickCount = 0
        var index = charIndex
        while (index < fullMessage.length && fullMessage[index] == codeBlockChar) {
            backtickCount++
            index++
        }

        // 如果反引号数量与开始时的数量相同，则认为是结束边界
        return backtickCount == currentBorderBacktickCount
    }

    private fun isChangeBlockStart(fullMessage: String, index: Int): Boolean {
        // 检查是否为行首
        val isLineStart = index == 0 || fullMessage[index - 1] == '\n'
        if (!isLineStart) return false

        if (index + changeStartBlock.length > fullMessage.length) return false
        return fullMessage.regionMatches(index, changeStartBlock, 0, changeStartBlock.length, ignoreCase = true)
    }

    private fun isChangeBlockEnd(fullMessage: String, index: Int): Boolean {
        // 检查是否为行首
        val isLineStart = index == 0 || fullMessage[index - 1] == '\n'
        if (!isLineStart) return false

        if (index + changeEndBlock.length > fullMessage.length) return false
        return fullMessage.regionMatches(index, changeEndBlock, 0, changeEndBlock.length, ignoreCase = true)
    }

    companion object {
        private fun createPart(
            blockStart: Int,
            partUpperOffset: Int,
            messageText: String,
            contextType: MessageBlockType,
            message: CompletableMessage,
            currentBorderBacktickCount: Int
        ): MessageBlock {
            check(blockStart < messageText.length)
            check(partUpperOffset < messageText.length)

            val blockText = messageText.substring(blockStart, partUpperOffset + 1)
            val part: MessageBlock = when (contextType) {
                MessageBlockType.CodeEditor -> CodeBlock(blockText, language = Language.ANY, message)
                MessageBlockType.PlainText -> TextBlock(message)
                MessageBlockType.CodeChange -> CodeChange(message)
            }

            if (blockText.isNotEmpty()) {
                part.addContent(blockText)
            }

            return part
        }
    }
}