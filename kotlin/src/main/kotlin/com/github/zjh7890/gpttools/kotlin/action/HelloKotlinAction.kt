package com.github.zjh7890.gpttools.kotlin.action
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
class HelloKotlinAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(
            "Hello from Kotlin Action!",
            "Kotlin Action Demo"
        )
    }
}