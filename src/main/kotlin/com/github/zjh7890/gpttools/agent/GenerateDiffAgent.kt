package com.github.zjh7890.gpttools.agent

import CodeChangeBlockView2
import com.github.zjh7890.gpttools.llm.LlmConfig
import com.github.zjh7890.gpttools.llm.LlmProvider
import com.github.zjh7890.gpttools.services.ChatContextMessage
import com.github.zjh7890.gpttools.services.ChatSession
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.chat.MessageView
import com.github.zjh7890.gpttools.toolWindow.chat.block.CodeBlock
import com.github.zjh7890.gpttools.utils.CmdUtils
import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.ParseUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import java.util.UUID

object GenerateDiffAgent {
    val logger = logger<GenerateDiffAgent>()

    fun apply(
        project: Project,
        llmConfig: LlmConfig,
        projectStructure: String,
        response: String,
        messageView: MessageView
    ) {
        val border = FileUtil.determineBorder(response)
        val chatSession = ChatSession(id = UUID.randomUUID().toString(), type = "apply")
        chatSession.add(
            ChatContextMessage(
                ChatRole.user, """
你是一个根据代码修改意见改代码的 agent。别的 agent 已经给出了代码的修改意见，所以你的工作就是读取代码仓库的现有代码，然后结合修改意见生成新的代码文件，文件必须完整，文件必须完整，文件必须完整，不要省略内容。

如果你想读取文件，你必须使用下面格式的返回，注意 custom 标识，这是必要的
```custom
readFileList {"files": ["src/main/HelloWorld.java", "src/main/SomeService.java"]}
```

下面是修改意见：
${border}
${response}
${border}

下面是项目的目录结构：
```
${projectStructure}
```
""".trimIndent())
        )

        while (true) {
            val applyFlow = LlmProvider.stream(chatSession, llmConfig)
            var responseText = ""
            runBlocking {
                applyFlow.onCompletion {
                    logger.warn("onCompletion ${it?.message}")
                }.catch {
                    it.printStackTrace()
                }.collect {
                    responseText += it
                }
            }
            chatSession.add(ChatContextMessage(ChatRole.assistant, responseText))
            val parsedResponse = ParseUtils.processResponse(responseText)
            // 完成后处理最终结果
            try {
                val sb = StringBuilder()
                when {
                    parsedResponse.isCustomCommand -> {
                        parsedResponse.customCommands?.forEach { command ->
                            val result = CmdUtils.executeCmd(command, "custom", project)
                            sb.append(result + "\n\n")
                        }
                    }
                }
                if (sb.isNotEmpty()){
                    chatSession.add(ChatContextMessage(ChatRole.user, sb.toString()))
                    continue
                }
            } catch (e: Exception) {
                logger.error("处理响应时出错: ${e.message}", e)
                throw e
            }

            // 处理响应，提取文件修改信息
            val codeChanges = ParseUtils.parse(responseText)
                .filter { it is CodeBlock }
                .map { it as CodeBlock }
                .filter { it.code.languageId.equals("diff", ignoreCase = true) }
                .map { ParseUtils.parseCodeChanges(project, it.getTextContent()) }
                .toList()

            ApplicationManager.getApplication().invokeLater() {
                messageView.centerPanel.add(CodeChangeBlockView2(project, codeChanges).getComponent())
                messageView.updateUI()
            }
            break;
        }
    }


}

data class FileModification(
    val file: String,
    val content: String
)