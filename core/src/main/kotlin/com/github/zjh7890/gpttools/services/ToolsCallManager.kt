package com.github.zjh7890.gpttools.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.llmChat.ChatPanel
import com.github.zjh7890.gpttools.utils.CmdUtils
import com.github.zjh7890.gpttools.utils.Desc
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import javax.swing.JPanel
import javax.swing.JTextField
import java.awt.BorderLayout
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters

/**
 * ToolsCallManager 负责处理与工具调用相关的逻辑，包括解析模型回复和执行确认的动作。
 */
class ToolsCallManager(private val project: Project, private val sessionManager: SessionManager) {

    private val logger = org.slf4j.LoggerFactory.getLogger(ToolsCallManager::class.java)

    /**
     * 解析模型回复，提取动作描述和命令
     */
    fun parseModelReply(reply: String): List<Action> {
        val actions = mutableListOf<Action>()
        val lines = reply.split('\n')
        var captureCommand = false
        var commandType = ""
        var actionDesc: String? = null
        val actionCommand = mutableListOf<String>()
        var foundActionResponse = false

        for (line in lines) {
            val trimmedLine = line.trim()

            if ("GPT_ACTION_RESPONSE" in trimmedLine) {
                foundActionResponse = true
                continue
            }

            if (!foundActionResponse) {
                continue
            }

            if (trimmedLine.startsWith("```")) {
                if (captureCommand) {
                    actions.add(Action(actionDesc ?: "", commandType, actionCommand.joinToString("\n")))
                    captureCommand = false
                    actionCommand.clear()
                    commandType = ""
                    actionDesc = ""
                } else {
                    captureCommand = true
                    // 提取命令类型，例如 shell 或 custom
                    val codeFencePattern = Regex("""```(\w+)?""")
                    val match = codeFencePattern.matchEntire(trimmedLine)
                    commandType = match?.groupValues?.get(1) ?: ""
                }
                continue
            }

            if (captureCommand) {
                actionCommand.add(trimmedLine)
            } else if (trimmedLine.isNotEmpty()) {
                actionDesc = trimmedLine
            }
        }

        // 处理最后一个命令
        if (captureCommand && actionCommand.isNotEmpty()) {
            actions.add(Action(actionDesc ?: "", commandType, actionCommand.joinToString("\n")))
        }

        logger.info("Parsed Actions: $actions")
        return actions
    }

    /**
     * 确认并执行动作列表
     */
    fun confirmAndExecuteActions(actions: List<Action>, ui: ChatPanel) {
        ApplicationManager.getApplication().invokeLater {
            actions.forEachIndexed { idx, action ->
                val (description, commandType, command) = action
                val dialog = CommandDialog(project, description, command)
                if (dialog.showAndGet()) {
                    val modifiedCommand = dialog.getModifiedCommand()
                    if (modifiedCommand.isNotEmpty()) {
                        ApplicationManager.getApplication().executeOnPooledThread {
                            ApplicationManager.getApplication().runReadAction {
                                val result = CmdUtils.executeCmd(modifiedCommand, commandType, project)
                                ui.addMessage(result, chatMessage = null)
                                sessionManager.appendLocalMessage(ChatRole.assistant, result)
                            }
                        }
                    } else {
                        ui.addMessage("修改后的命令无效，跳过执行。", chatMessage = null)
                    }
                } else {
                    ui.addMessage("用户取消了命令 $idx 的执行。", chatMessage = null)
                }
            }
        }
    }

    fun getTools(): String {
        val builder = StringBuilder()
        val objectMapper = jacksonObjectMapper()
        val functions = ToolsService::class.memberFunctions.filter { function ->
            function.findAnnotation<Desc>() != null
        }
        functions.forEachIndexed { index, function ->
            val methodDescription = function.findAnnotation<Desc>()?.description ?: "No description"
            val name = function.name
            val parameters = function.valueParameters.associate { subParam ->
                val key: String = subParam.name ?: "unknown"
                val value: Any? = generateSampleParameterValues(subParam)
                key to value
            }
            val parametersJson = objectMapper.writeValueAsString(parameters)
            builder.append("${index + 1}. $name\n")
            builder.append("描述：$methodDescription\n")
            builder.append("入参：$parametersJson\n")
        }
        return builder.toString()
    }

    fun generateSampleParameterValues(parameter: KParameter): Any? {
        val type = parameter.type
        val classifier = type.classifier as? KClass<*>

        return when (classifier) {
            String::class -> "exampleString"
            Int::class, Long::class, Float::class, Double::class, Short::class, Byte::class -> 0
            Boolean::class -> true
            List::class, Set::class, Collection::class -> listOf("item1", "item2")
            else -> if (classifier != null && classifier.isData) {
                // For data classes, generate a map of property names to sample values
                val paramMap = mutableMapOf<String, Any?>()
                classifier.primaryConstructor?.parameters?.forEach { param ->
                    val paramName = param.name ?: "unknown"
                    val paramValue = generateSampleParameterValues(param)
                    paramMap[paramName] = paramValue
                }
                paramMap
            } else "unknown"
        }
    }



    /**
     * CommandDialog 用于显示命令编辑对话框
     */
    private class CommandDialog(
        project: Project,
        private val description: String,
        private val command: String
    ) : DialogWrapper(project) {

        private val textField = JTextField(command, 30)

        init {
            title = "Confirm Action"
            init()
        }

        override fun createCenterPanel(): JPanel {
            return JPanel(BorderLayout()).apply {
                add(JBLabel("Action Description:"), BorderLayout.NORTH)
                add(JBLabel(description), BorderLayout.CENTER)
                add(JBLabel("Modify Command:"), BorderLayout.SOUTH)
                add(textField, BorderLayout.SOUTH)
            }
        }

        fun getModifiedCommand(): String {
            return textField.text.trim()
        }
    }
}

/**
 * Action 数据类，表示一个可执行的动作
 */
data class Action(val description: String, val commandType: String, val command: String)
