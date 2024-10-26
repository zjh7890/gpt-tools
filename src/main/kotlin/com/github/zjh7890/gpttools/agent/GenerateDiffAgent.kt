package com.github.zjh7890.gpttools.agent

import CodeChangeBlockView
import com.github.zjh7890.gpttools.llm.LlmConfig
import com.github.zjh7890.gpttools.llm.LlmProvider
import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.services.ChatContextMessage
import com.github.zjh7890.gpttools.services.ChatSession
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.chat.MessageView
import com.github.zjh7890.gpttools.toolWindow.chat.block.CodeBlock
import com.github.zjh7890.gpttools.toolWindow.chat.block.MessageCodeBlockCharProcessor
import com.github.zjh7890.gpttools.toolWindow.chat.block.SimpleMessage
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolWindowFactory
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
        messageView: MessageView,
        currentSession: ChatSession
    ) {
        val border = FileUtil.determineBorder(response)
        val chatSession = ChatSession(id = UUID.randomUUID().toString(), type = "apply")

        var fileContent = "No files."
        if (currentSession.fileList.isNotEmpty()) {
            fileContent = currentSession.fileList.map { FileUtil.readFileInfoForLLM(it) }.joinToString("\n\n")
        }

        chatSession.add(
            ChatContextMessage(
                ChatRole.user, """
你是一个改代码的 agent。别的 agent 已经给出了代码的修改意见，根据代码修改意见按照下面的格式返回文件的变更
以下是你新增文件的返回示例，由于是新增文件，ORIGINAL直接置空即可：
----- CHANGES START -----
----- CHANGE START -----
path: HelloWorld.java
changeType: CREATE
<<<< ORIGINAL
====
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello World");
    }
}
>>>> UPDATED
----- CHANGE END -----
----- CHANGES END -----

以下是你更新文件的返回示例，由于最终修改对文件内容进行替换修改，调用这个函数前必须先读取这个文件，这一点很重要很重要很重要，否则你的 ORIGINAL 并不是文件真实内容，是你凭空产生的，导致最终修改文件会失败。另外，ORIGINAL 需要尽量保证在整个文件的唯一性，因为最终是使用 replace 函数修改文件内容，如果 ORIGINAL 在文件有多处的话，会导致错误的多余的修改：
----- CHANGES START -----
----- CHANGE START -----
path: yuer-live-core/src/main/java/com/yupaopao/live/common/constants/KafkaTopic.java
changeType: MODIFY
<<<< ORIGINAL
public static final String SUD_BULLET_NOTIFY = "SUD_BULLET_NOTIFY";
}
====
public static final String SUD_BULLET_NOTIFY = "SUD_BULLET_NOTIFY";

   /**
    * pc开播中切换品类
    */
   public static final String CHANGE_CATEGORY = "CHANGE_CATEGORY";
}
>>>> UPDATED
----- CHANGE END -----
----- CHANGES END -----

以下是你删除文件的返回示例，由于是删除文件，ORIGINAL，UPDATE 都直接置空即可：
----- CHANGES START -----
----- CHANGE START -----
path: HelloWorld.java
changeType: DELETE
<<<< ORIGINAL
====
>>>> UPDATED
----- CHANGE END -----
----- CHANGES END -----


你可以按照需要在 CHANGES START/CHANGES END 里组合多个 CHANGE START/CHANGE END，如：
----- CHANGES START -----
[CHANGE 1]
[CHANGE 2]
----- CHANGES END -----


原文件:
${FileUtil.wrapBorder(fileContent)}

下面是修改意见：
${border}
${response}
${border}
""".trimIndent())
        )

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
//            val parsedResponse = ParseUtils.processResponse(responseText)
//            // 完成后处理最终结果
//            try {
//                val sb = StringBuilder()
//                when {
//                    parsedResponse.isCustomCommand -> {
//                        parsedResponse.customCommands?.forEach { command ->
//                            val result = CmdUtils.executeCmd(command, "custom", project)
//                            sb.append(result + "\n\n")
//                        }
//                    }
//                }
//                if (sb.isNotEmpty()){
//                    chatSession.add(ChatContextMessage(ChatRole.user, sb.toString()))
//                    continue
//                }
//            } catch (e: Exception) {
//                logger.error("处理响应时出错: ${e.message}", e)
//                throw e
//            }

        ApplicationManager.getApplication().invokeLater {
            val contentPanel = LLMChatToolWindowFactory.getPanel(project)
            val chatCodingService = ChatCodingService.getInstance(project)
            val chatMessage = chatCodingService.appendLocalMessage(ChatRole.assistant, responseText)
            contentPanel?.addMessage(responseText, true, render = true, chatMessage = chatMessage)
        }
    }


}

data class FileModification(
    val file: String,
    val content: String
)