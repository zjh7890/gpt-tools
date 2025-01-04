package com.github.zjh7890.gpttools.agent

import com.github.zjh7890.gpttools.LLMCoroutineScope
import com.github.zjh7890.gpttools.llm.LlmConfig
import com.github.zjh7890.gpttools.llm.LlmProvider
import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.services.ChatContextMessage
import com.github.zjh7890.gpttools.services.ChatSession
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.chat.MessageView
import com.github.zjh7890.gpttools.toolWindow.llmChat.ChatPanel
import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.JsonUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.util.*

object GenerateDiffAgent {
    val logger = logger<GenerateDiffAgent>()

    fun apply(
        project: Project,
        llmConfig: LlmConfig,
        projectStructure: String,
        response: String,
        currentSession: ChatSession,
        ui: ChatPanel
    ) {
        ui.progressBar.isVisible = true
        ui.progressBar.isIndeterminate = true  // 设置为不确定状态
        ui.inputSection.showStopButton()
        val border = FileUtil.determineBorder(response)
        val chatSession = ChatSession(id = UUID.randomUUID().toString(), type = "apply", project = project.name)

        var fileContent = "No files."
        if (currentSession.projectFileTrees.isNotEmpty()) {
            fileContent = currentSession.projectFileTrees.joinToString("\n\n=== Project: ${project.name} ===\n\n") { projectTree ->
                projectTree.files.joinToString("\n\n") { file ->
                    FileUtil.readFileInfoForLLM(file, project)
                }
            }
        }

        chatSession.add(
            ChatContextMessage(
                ChatRole.user, """
你是一个改代码的 agent。别的 agent 已经给出了代码的修改意见，根据代码修改意见按照下面的格式返回文件的变更。

支持以下几种变更类型：
1. CREATE: 新建文件
2. MODIFY: 修改文件的部分内容
3. DELETE: 删除文件
4. REWRITE: 完全重写文件内容（当文件改动较大或需要大规模重构时使用）

以下是新增文件的返回示例（CREATE），由于是新增文件，ORIGINAL直接置空即可：
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

以下是更新文件的返回示例（MODIFY），由于最终修改对文件内容进行替换修改，调用这个函数前必须先读取这个文件，这一点很重要很重要很重要，否则你的 ORIGINAL 并不是文件真实内容，是你凭空产生的，导致最终修改文件会失败。另外，ORIGINAL 需要尽量保证在整个文件的唯一性，因为最终是使用 replace 函数修改文件内容，如果 ORIGINAL 在文件有多处的话，会导致错误的多余的修改：
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

以下是完全重写文件的返回示例（REWRITE），适用于文件改动较大或需要重构的情况。ORIGINAL 直接置空，直接使用 UPDATED 的内容替换整个文件：
----- CHANGES START -----
----- CHANGE START -----
path: HelloWorld.java
changeType: REWRITE
<<<< ORIGINAL
====
public class HelloWorld {
    private final Logger logger = LoggerFactory.getLogger(HelloWorld.class);
    
    public static void main(String[] args) {
        HelloWorld app = new HelloWorld();
        app.start();
    }
    
    public void start() {
        logger.info("New implementation with proper logging");
        // ... more new code
    }
}
>>>> UPDATED
----- CHANGE END -----
----- CHANGES END -----

以下是删除文件的返回示例（DELETE），由于是删除文件，ORIGINAL，UPDATE 都直接置空即可：
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

注意事项：
1. 对于小范围修改，使用 MODIFY 类型
2. 对于大范围修改或需要重构的情况，使用 REWRITE 类型
3. MODIFY 类型时，ORIGINAL 必须是文件中实际存在的内容
4. REWRITE 类型时，会直接使用 UPDATED 的内容替换整个文件，使用时 ORIGINAL 直接置空，UPDATED 块直接重写文件，所以 UPDATED 块要包含文件所有数据，如要使用，一个文件只会有一个 REWRITE 块

原项目文件:
${FileUtil.wrapBorder(fileContent)}

下面是修改意见：
${border}
${response}
${border}
""".trimIndent())
        )

        var messageView: MessageView? = null
        // 添加一个空的消息视图用于流式更新
        ApplicationManager.getApplication().invokeAndWait {
            messageView = ui.addMessage("Generating Diff", chatMessage = null)
            messageView!!.scrollToBottom()
        }

        val applyFlow = LlmProvider.stream(chatSession, llmConfig)
        val chatCodingService = ChatCodingService.getInstance(project)

        var responseText = ""

        chatCodingService.currentJob = LLMCoroutineScope.scope(project).launch {
            applyFlow.onCompletion {
                logger.warn("onCompletion ${it?.message}")
            }.catch {
                logger.error("exception happens: ", it)
                responseText = "exception happens: " + it.message.toString()
            }.collect {
                responseText += it
                messageView!!.updateContent(responseText)
            }

            chatCodingService.currentJob = null
            logger.warn("LLM response, GenerateDiffAgent: ${JsonUtils.toJson(responseText)}")

            // 更新最终内容
            messageView!!.message = responseText
            messageView!!.reRender()

            chatSession.add(ChatContextMessage(ChatRole.assistant, responseText))
            chatSession.exportChatHistory()
            ui.progressBar.isIndeterminate = false // 处理完成后恢复确定状态
            ui.progressBar.isVisible = false
        }
    }
}
