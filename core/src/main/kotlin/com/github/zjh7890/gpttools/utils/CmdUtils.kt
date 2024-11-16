package com.github.zjh7890.gpttools.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.zjh7890.gpttools.services.ToolsService
import com.intellij.openapi.project.Project
import java.io.File
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.jvmErasure

/**
 * @Date: 2024/9/30 14:03
 */
object CmdUtils {
    fun executeCmd(cmd: String, commandType: String, project: Project) : String {
        val functions = ToolsService::class.memberFunctions.filter { function ->
            function.findAnnotation<Desc>() != null
        }

        if (commandType == "custom" || functions.any { cmd.contains(it.name) }) {
            val funcNameAndArgs = parseCustomCommand(cmd)
            if (funcNameAndArgs != null) {
                val (funcName, functionArguments) = funcNameAndArgs
                val result = executeCustom(funcName, functionArguments, project)
                val message = "命令: ${cmd}\n执行结果：\n$result"
                return message
            } else {
                val message = "无效的自定义命令格式, ${cmd}"
                return message
            }
        } else if(commandType == "shell") {
            try {
                val process = Runtime.getRuntime().exec(cmd, null, File(project.basePath))
                val output = process.inputStream.bufferedReader().readText()
                val errorOutput = process.errorStream.bufferedReader().readText()

                val message = "命令执行成功：${cmd}\n输出：\n```\n$output\n```\n错误输出：$errorOutput"
                return message
            } catch (e: Exception) {
                val message = "命令执行失败：${cmd}\n${e.message}"
                return message
            }
        } else {
            throw IllegalArgumentException("未知的命令类型: $commandType")
        }
    }

    // 更新 parseCustomCommand 方法
    private fun parseCustomCommand(cmd: String): Pair<String, String>? {
        val regex = Regex("""(\w+)\s*(\{.*\})""", RegexOption.DOT_MATCHES_ALL)
        val matchResult = regex.matchEntire(cmd.trim())
        return if (matchResult != null) {
            val funcName = matchResult.groupValues[1]
            val functionArguments = matchResult.groupValues[2]
            Pair(funcName, functionArguments)
        } else {
            null
        }
    }

    private fun executeCustom(funcName: String, functionArguments: String, project: Project): Any {
//        val cast = toolCall.cast<ChatCompletionsFunctionToolCall>()
//        val funcName = cast.function.name
//        val functionArguments = cast.function.arguments

        // functionArguments 示例: {"path": "live-service/api/File.txt"}
        // 反射找到对应的方法自动填充参数并调用

        // 反序列化 JSON 参数
        val objectMapper = jacksonObjectMapper()

        // 使用反射查找并调用函数
        val func = ToolsService::class.memberFunctions.find { it.name == funcName }
        return func?.let { function ->
            try {
                // 构造正确的参数类型
                val parameters =
                    function.parameters.drop(1)  // Drop the first parameter (receiver object)
                val args = mutableListOf<Any?>()
                val argsMap: Map<String, Any> = objectMapper.readValue(functionArguments)
                parameters.forEach { param ->
                    val paramName = param.name!!
                    val paramValue = argsMap[paramName]
                    val typedValue =
                        objectMapper.convertValue(paramValue, param.type.jvmErasure.javaObjectType)
                    args.add(typedValue)
                }

                val service = project.getService(ToolsService::class.java)
                // 调用函数
                val call = function.call(service, *args.toTypedArray())
                // 如果 call 是 string 类型，直接返回，否则 toJson

                val result = if (call is String) call else objectMapper.writeValueAsString(call)
                "Function '$funcName' executed with result: $result"
                return@let result
            } catch (e: Exception) {
                "Error executing function '$funcName': ${e.message}"
            }
        } ?: "Function '$funcName' not found"
    }
}