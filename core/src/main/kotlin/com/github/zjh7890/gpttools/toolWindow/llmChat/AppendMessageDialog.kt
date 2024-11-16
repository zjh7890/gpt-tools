package com.github.zjh7890.gpttools.toolWindow.llmChat

import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.*


class AppendMessageDialog(project: Project) : DialogWrapper(project) {
    // 修改 selectedRole 的类型和初始值
    var selectedRole: ChatRole = ChatRole.assistant
    var message: String = ""
    private val roleButtonGroup = ButtonGroup()
    private lateinit var messageField: JTextArea

    init {
        init()
        title = "Append Message"
    }

    private fun createRoleRadioButtons(): JComponent {
        val panel = JPanel()
        ChatRole.entries.forEach { role ->
            val radio = JRadioButton(role.roleName().capitalize()).apply {
                actionCommand = role.name
                if (role == ChatRole.assistant) isSelected = true
            }
            roleButtonGroup.add(radio)
            panel.add(radio)
        }
        return panel
    }

    override fun createCenterPanel(): JComponent {
        val rolePanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Role:", createRoleRadioButtons())
            .panel

        messageField = JTextArea(10, 60)
        val scrollPane = JScrollPane(messageField)
        val messagePanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Message:", scrollPane)
            .panel

        return JPanel(BorderLayout()).apply {
            add(rolePanel, BorderLayout.NORTH)
            add(messagePanel, BorderLayout.CENTER)
        }
    }

    override fun doOKAction() {
        selectedRole = ChatRole.valueOf(roleButtonGroup.selection?.actionCommand ?: "assistant")
        message = messageField.text
        super.doOKAction()
    }
}
