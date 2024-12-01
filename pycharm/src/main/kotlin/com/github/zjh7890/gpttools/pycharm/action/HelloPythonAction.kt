package com.github.zjh7890.gpttools.pycharm.action
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
class HelloPythonAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showMessageDialog(
            e.project,
            "Hello Python!",
            "Greeting",
            Messages.getInformationIcon()
        )
    }
}