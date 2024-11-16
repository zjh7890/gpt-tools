package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.settings.template.PromptTemplate
import com.github.zjh7890.gpttools.utils.ClipboardUtils.copyToClipboard
import com.github.zjh7890.gpttools.utils.DrawioToMermaidConverter
import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.PsiUtils.getDependencies
import com.github.zjh7890.gpttools.utils.TemplateUtils
import com.github.zjh7890.gpttools.utils.sendToChatWindow
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ui.FormBuilder
import org.apache.commons.lang3.StringUtils
import java.util.stream.Collectors
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextArea


class ServiceImplAction(val promptTemplate: PromptTemplate) : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        val editor: Editor? = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR)
        if (project == null || editor == null) {
            Messages.showMessageDialog(project, "Project or editor not found!", "Error", Messages.getErrorIcon())
            return
        }

        val document = editor.document
        val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
        if (file == null) {
            Messages.showMessageDialog(project, "File not found!", "Error", Messages.getErrorIcon())
            return
        }
        val currentOffset = editor.caretModel.offset
        val elementAtCaret = file?.findElementAt(currentOffset)
        val clazz = PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass::class.java)
        if (clazz == null) {
            Messages.showMessageDialog(project, "Cursor is not inside a class!", "Error", Messages.getErrorIcon());
            return
        }

        // 获取光标位置前后50行的文本
        val totalLines = document.lineCount
        val currentLine = document.getLineNumber(currentOffset)
        val startLine = maxOf(0, currentLine - 50)
        val endLine = minOf(totalLines - 1, currentLine + 50)

        val startOffset = document.getLineStartOffset(startLine)
        val currentLineEndOffset = document.getLineEndOffset(currentLine)
        val endOffset = document.getLineEndOffset(endLine)

        val textBeforeCaret = document.getText(TextRange(startOffset, currentLineEndOffset))
        val textAfterCaret = document.getText(TextRange(currentLineEndOffset, endOffset))

        val method = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java)

        // 在适当的地方创建和显示对话框
        val dialog = UMLFunctionDialog(project, promptTemplate)

        if (promptTemplate.input1.isNotBlank()
            || promptTemplate.input2.isNotBlank()
            || promptTemplate.input3.isNotBlank()
            || promptTemplate.input4.isNotBlank()
            || promptTemplate.input5.isNotBlank()) {
            dialog.show()
            if (dialog.isOK) {
                // 在对话框关闭后处理并打印数据
//            println("UML Text: ${dialog.input1}")
//            println("Function Text: ${dialog.input2}")
            } else {
                return
            }
        }

        try {
            val classes = getDependencies(clazz.containingFile.virtualFile, project)
            // clazz 和 classes 形成新 List, clazz 放在第一个
            val newClasses = listOf(clazz.containingFile.virtualFile) + classes
            val classInfos =
                newClasses.stream().map { x -> x.name }.collect(Collectors.toList()).joinToString("\n")
            val GPT_methodInfo = newClasses.map { FileUtil.readFileInfoForLLM(it, project) }.joinToString("\n\n")
            val GPT_className = clazz.name!!

            if (promptTemplate.input1.contains("UML Text")) {
                dialog.input1 = DrawioToMermaidConverter.convert(dialog.input1)
            }
            if (promptTemplate.input2.contains("UML Text")) {
                dialog.input2 = DrawioToMermaidConverter.convert(dialog.input2)
            }
            if (promptTemplate.input3.contains("UML Text")) {
                dialog.input3 = DrawioToMermaidConverter.convert(dialog.input3)
            }
            if (promptTemplate.input4.contains("UML Text")) {
                dialog.input4 = DrawioToMermaidConverter.convert(dialog.input4)
            }
            if (promptTemplate.input5.contains("UML Text")) {
                dialog.input5 = DrawioToMermaidConverter.convert(dialog.input5)
            }

            val map = mapOf(
                "GPT_methodInfo" to GPT_methodInfo,
                "GPT_methodName" to (method?.name ?: ""),
                "GPT_methodText" to (method?.text ?: ""),
                "GPT_className" to GPT_className,
                "GPT_selectionText" to (editor.selectionModel.selectedText ?: ""),
                "GPT_textBeforeCursor" to file.text.substring(0, editor.caretModel.offset),
                "GPT_textAfterCursor" to file.text.substring(editor.caretModel.offset),
                "GPT_allText" to file.text,
                "GPT_input1" to dialog.input1,
                "GPT_input2" to dialog.input2,
                "GPT_input3" to dialog.input3,
                "GPT_input4" to dialog.input4,
                "GPT_input5" to dialog.input5,
                "GPT_50LinesTextBeforeCaret" to textBeforeCaret,
                "GPT_50LinesTextAfterCaret" to textAfterCaret,
            )

            val result = TemplateUtils.replacePlaceholders(promptTemplate.value, map)
            // Update the content to send to the chat window
            sendToChatWindow(project, { contentPanel, chatCodingService ->
                chatCodingService.newSession()
                contentPanel.setInput(result)
            })
            copyToClipboard(result)
        } catch (ex: Exception) {
            Messages.showMessageDialog(project, "Error finding classes: ${ex.message}", "Error", Messages.getErrorIcon())
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.text = promptTemplate.desc
    }
}

class UMLFunctionDialog(project: Project?, val promptTemplate: PromptTemplate) : DialogWrapper(project) {
    private val input1Area = JTextArea(10, 60)
    private val input2Area = JTextArea(10, 80)
    private val input3Area = JTextArea(10, 80)
    private val input4Area = JTextArea(10, 80)
    private val input5Area = JTextArea(10, 80)

    // 定义属性来保存输入数据
    var input1: String = ""
    var input2: String = ""
    var input3: String = ""
    var input4: String = ""
    var input5: String = ""

    init {
        init()
        title = "Enter UML and Function Text"
    }

    override fun createCenterPanel(): JComponent {
        val formBuilder = FormBuilder.createFormBuilder()

        if (StringUtils.isNotBlank(promptTemplate.input1)) {
            formBuilder.addLabeledComponent(promptTemplate.input1, JScrollPane(input1Area))  // 添加带滚动条的组件
        }
        if (StringUtils.isNotBlank(promptTemplate.input2)) {
            formBuilder.addLabeledComponent(promptTemplate.input2, JScrollPane(input2Area))  // 添加带滚动条的组件
        }
        if (StringUtils.isNotBlank(promptTemplate.input3)) {
            formBuilder.addLabeledComponent(promptTemplate.input3, JScrollPane(input3Area))  // 添加带滚动条的组件
        }
        if (StringUtils.isNotBlank(promptTemplate.input4)) {
            formBuilder.addLabeledComponent(promptTemplate.input4, JScrollPane(input4Area))  // 添加带滚动条的组件
        }
        if (StringUtils.isNotBlank(promptTemplate.input5)) {
            formBuilder.addLabeledComponent(promptTemplate.input5, JScrollPane(input5Area))  // 添加带滚动条的组件
        }
//            .addLabeledComponent("UML Text:", scrollPaneForUML)  // 添加带滚动条的组件
//            .addLabeledComponent("Function Text:", scrollPaneForFunction)  // 添加带滚动条的组件
        return formBuilder.panel
    }

    override fun doOKAction() {
        // 在关闭前保存数据
        input1 = input1Area.text
        input2 = input2Area.text
        input3 = input3Area.text
        input4 = input4Area.text
        input5 = input5Area.text

        super.doOKAction()
    }
}


