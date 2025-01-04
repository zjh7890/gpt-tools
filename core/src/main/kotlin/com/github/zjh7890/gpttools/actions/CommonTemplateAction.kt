package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.context.builder.PromptContext
import com.github.zjh7890.gpttools.context.builder.PromptContextProvider
import com.github.zjh7890.gpttools.context.builder.WrapBorder
import com.github.zjh7890.gpttools.services.SessionManager
import com.github.zjh7890.gpttools.settings.template.PromptTemplate
import com.github.zjh7890.gpttools.utils.ChatUtils
import com.github.zjh7890.gpttools.utils.ClipboardUtils.copyToClipboard
import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.TemplateUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ui.FormBuilder
import org.apache.commons.lang3.StringUtils
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextArea
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

import com.github.zjh7890.gpttools.utils.GptToolsIcon
import kotlin.reflect.full.primaryConstructor

class CommonTemplateAction(val promptTemplate: PromptTemplate) : AnAction(promptTemplate.desc, null, GptToolsIcon.PRIMARY) {
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
        val elementAtCaret = file.findElementAt(currentOffset)
        val promptContext = PromptContextProvider.from(elementAtCaret, editor)

        // Create and display the dialog if necessary
        val dialog = UMLFunctionDialog(project, promptTemplate)
        if (promptTemplate.input1.isNotBlank()
            || promptTemplate.input2.isNotBlank()
            || promptTemplate.input3.isNotBlank()
            || promptTemplate.input4.isNotBlank()
            || promptTemplate.input5.isNotBlank()
        ) {
            dialog.show()
            if (!dialog.isOK) {
                return
            }
        }

        val dataClassToMap = dataClassToMap(promptContext!!)
        val inputMap = mapOf(
            "GPT_input1" to dialog.input1,
            "GPT_input2" to dialog.input2,
            "GPT_input3" to dialog.input3,
            "GPT_input4" to dialog.input4,
            "GPT_input5" to dialog.input5,
        )

        // Merge dataClassToMap and inputMap into mergedMap
        var mergedMap = dataClassToMap.mapValues { it.value ?: "" } + inputMap

        // Call checkVariables
        val missingVariables = TemplateUtils.checkVariables(promptTemplate.value, mergedMap)
        if (missingVariables.isNotEmpty()) {
            Messages.showMessageDialog(
                project,
                "Missing variables: ${missingVariables.joinToString(", ")}",
                "Warning",
                Messages.getWarningIcon()
            )
            return
        }

        mergedMap = mergedMap.mapValues { (key, value) ->
            if (value.isNotBlank() &&
                PromptContext::class.primaryConstructor?.parameters?.find { it.name == key.removePrefix("GPT_") }
                    ?.annotations?.any { it is WrapBorder } == true) {
                FileUtil.wrapBorder(value)
            } else {
                value ?: ""
            }
        } + inputMap

        val result = TemplateUtils.replacePlaceholders(promptTemplate.value, mergedMap)
        if (promptTemplate.newChat) {
            ChatUtils.sendToChatWindow(project) { contentPanel, chatCodingService ->
                SessionManager.getInstance(project).createNewSession()
                contentPanel.setInput("\n" + result)
            }
        } else {
            ChatUtils.activateToolWindowRun(project) { contentPanel, chatCodingService ->
                contentPanel.inputSection.text += "\n" + result
            }
        }
        copyToClipboard(result)
    }

    fun <T : Any> dataClassToMap(dataClass: T): Map<String, String?> {
        return dataClass::class.memberProperties.associate { property ->
            property.isAccessible = true
            "GPT_" + property.name to (property.getter.call(dataClass)?.toString())
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

    // Define properties to store input data
    var input1: String = ""
    var input2: String = ""
    var input3: String = ""
    var input4: String = ""
    var input5: String = ""

    init {
        init()
        title = "Input"
    }

    override fun createCenterPanel(): JComponent {
        val formBuilder = FormBuilder.createFormBuilder()

        if (StringUtils.isNotBlank(promptTemplate.input1)) {
            formBuilder.addLabeledComponent(promptTemplate.input1, JScrollPane(input1Area))
        }
        if (StringUtils.isNotBlank(promptTemplate.input2)) {
            formBuilder.addLabeledComponent(promptTemplate.input2, JScrollPane(input2Area))
        }
        if (StringUtils.isNotBlank(promptTemplate.input3)) {
            formBuilder.addLabeledComponent(promptTemplate.input3, JScrollPane(input3Area))
        }
        if (StringUtils.isNotBlank(promptTemplate.input4)) {
            formBuilder.addLabeledComponent(promptTemplate.input4, JScrollPane(input4Area))
        }
        if (StringUtils.isNotBlank(promptTemplate.input5)) {
            formBuilder.addLabeledComponent(promptTemplate.input5, JScrollPane(input5Area))
        }
        return formBuilder.panel
    }

    override fun doOKAction() {
        input1 = input1Area.text
        input2 = input2Area.text
        input3 = input3Area.text
        input4 = input4Area.text
        input5 = input5Area.text
        super.doOKAction()
    }
}