package com.github.zjh7890.gpttools.toolWindow.chat

enum class ChatRole {
    System,
    Assistant,
    User;

    fun roleName(): String {
        return this.name.lowercase()
    }
}