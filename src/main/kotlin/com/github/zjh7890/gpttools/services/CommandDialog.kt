package com.github.zjh7890.gpttools.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JTextArea

class CommandDialog(
    project: Project,
    private val actionDesc: String,
    private var command: String
) : DialogWrapper(project, true) {

    private val commandInput = JTextArea(command)

    init {
        title = "确认执行命令"
        init()  // 初始化对话框
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("动作描述:") {
                label(actionDesc)
            }
            row("当前命令:") {
                scrollCell(commandInput)
                    .resizableColumn()  // 确保文本区域可以调整大小
                    .align(Align.FILL)  // 将文本区域对齐到对话框的可填充空间
            }
        }
    }

    fun getModifiedCommand(): String {
        return commandInput.text
    }
}