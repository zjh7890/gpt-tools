// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.github.zjh7890.gpttools.toolWindow.chat.block

import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole


class SimpleMessage(
    override val displayText: String,
    override val text: String,
    val chatRole: ChatRole
) : CompletableMessage {
    private val textListeners: MutableList<MessageBlockTextListener> = mutableListOf()
    override fun getRole(): ChatRole = chatRole

    override fun addTextListener(textListener: MessageBlockTextListener) {
        textListeners += textListener
    }

    override fun removeTextListener(textListener: MessageBlockTextListener) {
        textListeners -= textListener
    }
}