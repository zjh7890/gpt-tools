package com.github.zjh7890.gpttools.goland.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class HelloGoAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showMessageDialog(
            e.project,
            "Hello Go!",
            "Greeting",
            Messages.getInformationIcon()
        )
    }
}