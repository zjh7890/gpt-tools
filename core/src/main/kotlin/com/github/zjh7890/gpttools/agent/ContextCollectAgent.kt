package com.github.zjh7890.gpttools.agent

import com.github.zjh7890.gpttools.llm.LlmConfig
import com.github.zjh7890.gpttools.llm.LlmProvider
import com.github.zjh7890.gpttools.services.ChatContextMessage
import com.github.zjh7890.gpttools.services.ChatSession
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.utils.CmdUtils
import com.github.zjh7890.gpttools.utils.ParseUtils
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import java.util.*

object ContextCollectAgent {
    private val logger = logger<ContextCollectAgent>()

    fun collectContext(
        userMessages: ChatSession,
        requestPrompt: String,
        llmConfig: LlmConfig,
        projectStructure: String,
        project: Project
    ) : List<String> {
        val chatSession = ChatSession(
            id = UUID.randomUUID().toString(), type = "collect",
            project = project,
            relevantProjects =  mutableListOf(project))
        chatSession.add(
            ChatContextMessage(ChatRole.user, """
你是一个代码上下文收集 agent，我会给你提供代码仓库的结构，你的目标是根据我的需求在代码仓库收集相关的文件，你完全不负责开发，开发会有别的 agent 负责，你不需要关心，但是你必须要收集完整的上下文文件：包括需求开发过程中需要参考查看的文件，需要改动的文件。
如果你在过程中想读取我本地项目信息，你可以返回 shell 命令给我，我会给你输出结果，注意 shell 的执行路径是项目根目录，如 你可以返回 `ls .` 获取根目录下的文件列表，返回 `cat src/main/HelloWorld.java` 读取 src/main 目录下的 HelloWorld.java 文件。任何时候，你想要获取项目信息，你都应该通过返回命令的形式。

返回格式说明：你的返回格式只有两种，当你还需要获取更多信息时，返回 shell 命令，当你已经收集了完整的上下文件时，返回一个 json 文件列表，注意，必须是你已经确定了完整的上下文文件时，才返回 json 列表，否则继续返回 shell 命令。
1. shell 命令返回示例如下
```shell
cat src/main/HelloWorld.java
cat src/main/SomeService.java
```
2. json 列表返回示例如下
```json
[
    "src/main/HelloWorld.java",
    "src/main/SomeService.java"
]
```

我本次的需求是：
${requestPrompt}

项目信息如下：
```
${projectStructure}
```
            """.trimIndent())
        )

        if (userMessages.messages.isNotEmpty()) {
            val exportedContent = userMessages.exportChatHistory()
            chatSession.add(ChatContextMessage(ChatRole.user,
                """
下面是我之前的对话历史，也给你作为收集上下文的参考：
${exportedContent}
""".trimIndent()))
        }

        for (i in 0 until 7) {
            val collectContextFlow = LlmProvider.stream(userMessages, llmConfig)
            var text = ""
            runBlocking {
                collectContextFlow.onCompletion {
                    logger.warn("onCompletion ${it?.message}")
                }.catch {
                    it.printStackTrace()
                }.collect {
                    text += it
                }
            }
            val parsedResponse = ParseUtils.processResponse(text)
            // 完成后处理最终结果
            try {
                val sb = StringBuilder()
                when {
                    parsedResponse.isShellCommand -> {
                        parsedResponse.shellCommands?.forEach { command ->
                            val result = CmdUtils.executeCmd(command, "shell", project)
                            sb.append(result + "\n\n")
                        }
                    }
                    parsedResponse.isFileList -> {
                        return parsedResponse.fileList ?: emptyList()
                    }
                    else -> {
                        logger.error("未知的响应格式")
                    }
                }
                userMessages.add(ChatContextMessage(ChatRole.user, sb.toString()))
            } catch (e: Exception) {
                logger.error("处理响应时出错: ${e.message}", e)
                throw e
            }
        }
        return emptyList()
    }
}
