package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.settings.other.OtherSettingsState
import com.github.zjh7890.gpttools.utils.ClipboardUtils.copyToClipboard
import com.github.zjh7890.gpttools.utils.DrawioToMermaidConverter
import com.github.zjh7890.gpttools.utils.GptToolsIcon
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Iconable
import com.intellij.util.ui.FormBuilder
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextArea

class ConvertToMermaidAction : AnAction(), Iconable {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val settings = OtherSettingsState.getInstance()
        e.presentation.isVisible = settings.showConvertToMermaidAction
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val umlFunctionDialog = UMLDialog(project!!)

        umlFunctionDialog.show()
        if (umlFunctionDialog.isOK) {
            copyToClipboard("```\n" +  DrawioToMermaidConverter.convert(umlFunctionDialog.input1) + "\n```\n")
            // 在对话框关闭后处理并打印数据
//            println("UML Text: ${dialog.input1}")
//            println("Function Text: ${dialog.input2}")
        } else {
            return
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun getIcon(flags: Int): Icon {
        return GptToolsIcon.PRIMARY
    }
}

class UMLDialog(project: Project?) : DialogWrapper(project) {
    private val input1Area = JTextArea(10, 60)

    // 定义属性来保存输入数据
    var input1: String = ""

    init {
        init()
        title = "Input"
    }

    override fun createCenterPanel(): JComponent {
        val formBuilder = FormBuilder.createFormBuilder()
        formBuilder.addLabeledComponent("UML", JScrollPane(input1Area))  // 添加带滚动条的组件
//            .addLabeledComponent("UML Text:", scrollPaneForUML)  // 添加带滚动条的组件
//            .addLabeledComponent("Function Text:", scrollPaneForFunction)  // 添加带滚动条的组件
        return formBuilder.panel
    }

    override fun doOKAction() {
        // 在关闭前保存数据
        input1 = input1Area.text
        super.doOKAction()
    }

}
