package com.github.zjh7890.gpttools.toolWindow.chat

enum class ChatRole {
    system,
    assistant,
    user;

    fun roleName(): String {
        return this.name.lowercase()
    }
}