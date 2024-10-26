package com.github.zjh7890.gpttools.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.zjh7890.gpttools.ShireCoroutineScope
import com.github.zjh7890.gpttools.agent.ContextCollectAgent
import com.github.zjh7890.gpttools.agent.GenerateDiffAgent
import com.github.zjh7890.gpttools.settings.common.CommonSettings
import com.github.zjh7890.gpttools.llm.ChatMessage
import com.github.zjh7890.gpttools.llm.LlmConfig
import com.github.zjh7890.gpttools.llm.LlmProvider
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolPanel
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolWindowFactory
import com.github.zjh7890.gpttools.toolWindow.llmChat.SessionListener
import com.github.zjh7890.gpttools.utils.CmdUtils
import com.github.zjh7890.gpttools.utils.Desc
import com.github.zjh7890.gpttools.utils.DirectoryUtil
import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters

@Service(Service.Level.PROJECT)
class ChatCodingService(val project: Project) : Disposable{
    private var currentJob: Job? = null
    val sessions = mutableMapOf<String, ChatSession>()
    var currentSessionId: String = ""

    init {
        loadSessions()
    }

    private val sessionListeners = mutableListOf<SessionListener>()

    fun getCurrentSession(): ChatSession {
        return sessions[currentSessionId]!!
    }

    fun getSessionList(): List<ChatSession> {
        return sessions.values.toList()
    }

    fun setCurrentSession(sessionId: String) {
        if (sessions.containsKey(sessionId)) {
            currentSessionId = sessionId
            // 如果有需要，通知其他监听器
        }
    }

    fun saveSessions() {
        // 序列化 sessions 并保存到文件
        val sessionsData = sessions.values.map { it.toSerializable() }
        val filePath = getSessionsFilePath()
        FileUtil.writeJsonToFile(filePath, sessionsData)
        // 通知监听器会话列表已更新
        notifySessionListChanged()
    }

    private fun loadSessions() {
        val filePath = getSessionsFilePath()
        val sessionsData: List<SerializableChatSession>? = FileUtil.readJsonFromFile(filePath)
        sessionsData?.forEach { data ->
            val session = data.toChatSession(project)  // 修改
            sessions[session.id] = session
        }
        if (sessions.isEmpty()) {
            newSession()
        }
        currentSessionId = sessions.keys.lastOrNull() ?: ""
    }

    private fun getSessionsFilePath(): String {
        val userHome = System.getProperty("user.home")
        return "$userHome/.gpttools/chat_sessions.json"
    }

    fun stop() {
        currentJob?.cancel()
    }

    fun updateWithContext(withContext: Boolean) {
        getCurrentSession().withContext = withContext
    }

    fun handlePromptAndResponse(
        ui: LLMChatToolPanel,
        prompter: String,
        searchContext: Boolean,
        editingMessage: ChatContextMessage?,
        llmConfig: LlmConfig
    ) {
        val session = getCurrentSession()
        currentJob?.cancel()
        val projectStructure = DirectoryUtil.getDirectoryContents(project)

        var message = editingMessage
        if (editingMessage == null) {
            message = ChatContextMessage(ChatRole.user, prompter)
            ui.addMessage(prompter, true, prompter, null, message)
            getCurrentSession().add(message)
        }
        val messageView = ui.addMessage("Loading", chatMessage = null)

        ApplicationManager.getApplication().executeOnPooledThread {
            if (searchContext) {
                val fileList =
                    ContextCollectAgent.collectContext(session, prompter, llmConfig, projectStructure, project)
                val fileContents = FileUtil.readFileInfoForLLM(project, fileList)

                val contextMessage = ChatContextMessage(ChatRole.user, "下面是文件的上下文信息: \n" + FileUtil.wrapBorder(fileContents))
                getCurrentSession().add(contextMessage)
                ApplicationManager.getApplication().invokeLater {
                    ui.addMessage(fileList.joinToString("\n"), chatMessage = message)
                }
            }

            addContextToMessages(message!!, project)
            val messages: MutableList<ChatMessage> = getCurrentSession().transformMessages()
            exportChatHistory()
            saveSessions()
            val response = LlmProvider.stream(messages, llmConfig = llmConfig)
            var result: String = ""
            currentJob = ShireCoroutineScope.scope(project).launch {
                result = ui.updateMessage(response, messageView!!)
                getCurrentSession().add(ChatContextMessage(ChatRole.assistant, result))
                exportChatHistory()
                saveSessions()
                ApplicationManager.getApplication().invokeLater {
                    if (CommonSettings.getInstance(project).generateDiff) {
                        GenerateDiffAgent.apply(project, llmConfig, projectStructure, result, messageView)
                    }
//                    val (actionType, actions) = parseModelReply(result)
//                    if (actionType == "执行动作") {
//                        confirmAndExecuteActions(actions, ui)
//                    }
                }
            }
        }
    }

    private fun addContextToMessages(message: ChatContextMessage, project: Project) {
        var fileContent = "No files."
        if (getCurrentSession().fileList.isNotEmpty()) {
            fileContent = getCurrentSession().fileList.map { FileUtil.readFileInfoForLLM(it) }.joinToString("\n\n")
        }

        val projectInfo = """
当前项目名称：${project.name}
项目结构：
```
${DirectoryUtil.getDirectoryContents(project)}
```
""".trimIndent()

        val cmdInfo = """
你可以返回 shell 命令，注意 shell 的执行路径是项目根目录，如 你可以返回 `ls .` 获取根目录下的文件列表。

除了常见的 shell 命令，你还可以返回以下自定义命令获取项目信息(用```custom 标记)：
${getTools()}

以下是你返回命令的示例 （shell 命令用```shell标记，custom 命令用```custom标记, custom 命令后面跟一个 json 表示参数内容）：
1. [action desc]
```shell
action command
```shell
2. [action2 desc]
```custom
action2 {"projectName": "xxxProject"}
```
        """.trimIndent()

        val context = """
相关文件内容：
${FileUtil.wrapBorder(fileContent)}
""".trimIndent()

        message.context = context
    }

    private fun confirmAndExecuteActions(actions: List<Action>, ui: LLMChatToolPanel) {
        ApplicationManager.getApplication().invokeLater {
            actions.forEachIndexed { idx, action ->
                val (actionDesc, commandType, cmd) = action
                val dialog = CommandDialog(project, actionDesc, cmd)
                if (dialog.showAndGet()) {
                    val modifiedCommand = dialog.getModifiedCommand()
                    if (modifiedCommand.isNotEmpty()) {
                        ApplicationManager.getApplication().executeOnPooledThread {
                            ApplicationManager.getApplication().runReadAction {
                                val result = CmdUtils.executeCmd(modifiedCommand, commandType, project)
                                ui.addMessage(result, chatMessage = null)
                                appendLocalMessage(ChatRole.assistant, result)
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

    private fun parseModelReply(reply: String): Pair<String, List<Action>> {
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
                    actions.add(Action(actionDesc ?: "", commandType, actionCommand.joinToString("")))
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
            } else if(trimmedLine.isNotEmpty()) {
                actionDesc = trimmedLine
            }
        }

        return "执行动作" to actions
    }

    fun appendLocalMessage(role: ChatRole, msg: String): ChatContextMessage {
        val message = ChatContextMessage(role, msg)
        getCurrentSession().add(message)
        return message
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

    fun newSession(keepContext: Boolean = false) {
        val sessionId = UUID.randomUUID().toString()
        val newSession = ChatSession(id = sessionId, type = "chat")
        if (currentSessionId.isNotEmpty() && keepContext) {
//            newSession.fileList = getCurrentSession().fileList
        }

        currentSessionId = sessionId
        sessions[sessionId] = newSession
        saveSessions()

//        // init system prompt
//        val message = appendLocalMessage(
//            ChatRole.user, """
//你是一个专业的后端程序员，你会根据我后续提出的需求编辑我电脑本地的项目，你的回答尽量简洁，仅返回变更代码，省略文件无关内容。
//在提出需求的时候，我会附上相关的上下文，上下文的信息非常非常重要，在整个对话过程你都应该参考上下文进行作答，上下文包括以下内容：
//1. 需求相关的文件内容。包含了文件名及文件里的内容。
//2. 项目信息，包含了目录结构和目录下的文件名。
//
//**注意**：
//1. 如果上下文中的文件信息不够，你可向我询问。
//2. 任何时候，你都不应该假设项目内容，而是想我询问。
//""".trimIndent()
//        )
//        val contentPanel = LLMChatToolWindowFactory.getPanel(project)
//        contentPanel?.addMessage(message.content, true, render = true, chatMessage = message)
//
//        val message2 = appendLocalMessage(
//            ChatRole.user, """
//下面开始是我的需求。
//""".trimIndent()
//        )
//        contentPanel?.addMessage(message2.content, true, render = true, chatMessage = message2)
    }

    fun exportChatHistory(): String {
        return getCurrentSession().exportChatHistory()
    }

    companion object {
        fun getInstance(project: Project): ChatCodingService = project.service()

        private val logger = logger<ChatCodingService>()
    }

    override fun dispose() {
        TODO("Not yet implemented")
    }

    fun truncateMessagesAfter(chatMessage: ChatContextMessage) {
        val index = getCurrentSession()!!.messages.indexOf(chatMessage)
        if (index >= 0 && index < getCurrentSession().messages.size - 1) {
            // 保留到指定消息（包括它本身）
            getCurrentSession().messages.subList(index + 1, getCurrentSession().messages.size).clear()
        }
    }

    fun addSessionListener(listener: SessionListener) {
        sessionListeners.add(listener)
    }

    private fun notifySessionListChanged() {
        sessionListeners.forEach { it.sessionListChanged() }
    }

    fun addFileToCurrentSession(virtualFile: VirtualFile) {
        val currentSession = getCurrentSession()
        if (!currentSession.fileList.contains(virtualFile)) {
            currentSession.fileList.add(virtualFile)
            val contentPanel = LLMChatToolWindowFactory.getPanel(project)
            contentPanel?.refreshFileList()
            saveSessions()
        }
    }
}

data class Action(val description: String, val commandType: String, val command: String)

data class ChatSession(
    val id: String,
    val messages: MutableList<ChatContextMessage> = mutableListOf(),
    val startTime: Long = System.currentTimeMillis(),
    val type: String,
    var fileList: MutableList<VirtualFile> = mutableListOf(),
    var withContext: Boolean = true
) {
    // 添加序列化方法
    fun toSerializable(): SerializableChatSession {
        return SerializableChatSession(
            id = id,
            messages = messages,
            startTime = startTime,
            type = type,
            withContext = withContext,
            filePaths = fileList.map { it.path }.toMutableList()  // 新增
        )
    }

    fun add(message: ChatContextMessage) {
        messages.add(message)
    }

    // 清空会话
    fun clear() {
        messages.clear()
    }

    // 导出聊天历史记录
    fun exportChatHistory(): String {
        val chatHistory = transformMessages().joinToString("\n\n") {
            val border = FileUtil.determineBorder(it.content)
            """
${it.role}:
${border}
${it.content}
${border}
            """.trimIndent()
        }
        saveChatHistoryToFile(chatHistory)
        return chatHistory
    }

    // 保存聊天记录到文件
    private fun saveChatHistoryToFile(chatHistory: String) {
        try {
            // 使用 formatFileName 方法生成文件名
            val fileName = formatFileName(type)
            val filePath = getPluginDataDirectory() + "/chat_history/$fileName"
            // 写入文件
            FileUtil.writeToFile(filePath, chatHistory)
        } catch (e: Exception) {
            // 处理异常，例如日志记录
            logger.warn("Error saving chat history: ${e.message}")
        }
    }

    // 根据时间和类型格式化文件名
    private fun formatFileName(type: String): String {
        val date = Date(startTime)
        val dateFormat = SimpleDateFormat("yy-MM-dd HH:mm:ss.SSS")
        val formattedDate = dateFormat.format(date)
        return "$formattedDate-$type.txt"
    }

    // 获取插件数据存储目录
    private fun getPluginDataDirectory(): String {
        val userHome = System.getProperty("user.home")
        return "$userHome/.gpttools"
    }

    fun transformMessages(): MutableList<ChatMessage> {
        val chatMessages: MutableList<ChatMessage>
        if (withContext) {
            // 找到最后一个用户消息的索引
            val lastUserMessageIndex = messages.indexOfLast { it.role == ChatRole.user }

            // 映射每条消息，根据是否有 context 以及是否是最后一个用户消息进行处理
            chatMessages = messages.mapIndexed { index, it ->
                when {
                    // 1. 没有 context
                    it.context.isEmpty() -> ChatMessage(it.role, it.content)

                    // 3. 有 context 且是最后一个用户消息
                    it.role == ChatRole.user && index == lastUserMessageIndex -> {
                        ChatMessage(
                            it.role,
                            """
${it.content}
---
上下文信息：
${FileUtil.wrapBorder(it.context)}
                        """.trimIndent()
                        )
                    }

                    // 2. 有 context 但不是最后一个用户消息
                    it.role == ChatRole.user -> {
                        ChatMessage(
                            it.role,
                            """
${it.content}
---
上下文信息：
```
上下文已失效
```
                        """.trimIndent()
                        )
                    }

                    // 其他消息（如 assistant 角色的消息）
                    else -> ChatMessage(it.role, it.content)
                }
            }.toMutableList()
        } else {
            chatMessages = messages.map { ChatMessage(it.role, it.content) }.toMutableList()
        }
        return chatMessages
    }

    companion object {
        private val logger = logger<ChatCodingService>()

        fun fromSerializable(data: SerializableChatSession, project: Project): ChatSession {
            val virtualFiles = data.filePaths.mapNotNull { path ->
                LocalFileSystem.getInstance().findFileByPath(path)
            }.toMutableList()

            return ChatSession(
                id = data.id,
                messages = data.messages,
                startTime = data.startTime,
                type = data.type,
                fileList = virtualFiles,  // 新增
                withContext = data.withContext
            )
        }
    }
}

@Serializable
data class ChatContextMessage @JvmOverloads constructor(
    val role: ChatRole = ChatRole.user,
    var content: String = "",
    var context: String = "",
)

@Serializable
data class SerializableChatSession @JvmOverloads constructor(
    val id: String = "",
    val messages: MutableList<ChatContextMessage> = mutableListOf(),
    val startTime: Long = 0L,
    val type: String = "",
    val withContext: Boolean = true,
    val filePaths: MutableList<String> = mutableListOf()  // 新增
) {
    fun toChatSession(project: Project): ChatSession {
        return ChatSession.fromSerializable(this, project)
    }
}
