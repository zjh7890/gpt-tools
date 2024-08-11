// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.github.zjh7890.gpttools.toolWindow.chat.block

import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.intellij.openapi.actionSystem.DataKey

interface CompletableMessage {
    val text: String
    val displayText: String

    fun getRole(): ChatRole

    fun addTextListener(textListener: MessageBlockTextListener)
    fun removeTextListener(textListener: MessageBlockTextListener)

    companion object {
//        val key: DataKey<CompletableMessage> = DataKey.create("CompletableMessage")

    }
}

