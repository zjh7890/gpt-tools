package com.github.zjh7890.gpttools.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.zjh7890.gpttools.LLMCoroutineScope
import com.github.zjh7890.gpttools.agent.GenerateDiffAgent
import com.github.zjh7890.gpttools.llm.ChatMessage
import com.github.zjh7890.gpttools.llm.LlmConfig
import com.github.zjh7890.gpttools.llm.LlmProvider
import com.github.zjh7890.gpttools.settings.common.CommonSettings
import com.github.zjh7890.gpttools.toolWindow.chat.AutoDevInputTrigger
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.llmChat.ChatToolPanel
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolWindowFactory
import com.github.zjh7890.gpttools.toolWindow.llmChat.SessionListener
import com.github.zjh7890.gpttools.utils.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
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
class ChatCodingService(val project: Project) : Disposable {
    var currentJob: Job? = null
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
            val session = data.toChatSession(project)
            sessions[session.id] = session
        }
        // 获取当前项目的会话列表
        val currentProjectSessions = sessions.values
            .filter { it.project == project.name }

        if (currentProjectSessions.isEmpty()) {
            // 如果当前项目没有会话，创建新会话
            newSession()
        } else {
            // 设置当前项目最新的会话为当前会话
            currentSessionId = currentProjectSessions
                .maxByOrNull { it.startTime }
                ?.id
                ?: currentProjectSessions.first().id
        }
    }

    private fun getSessionsFilePath(): String {
        val userHome = System.getProperty("user.home")
        return "$userHome/.gpttools/chat_sessions.json"
    }

    fun stop() {
        currentJob?.cancel()
    }

    fun updateWithFiles(withFiles: Boolean) {
        CommonSettings.getInstance().withFiles = withFiles
        getCurrentSession().withFiles = withFiles
    }

    fun handlePromptAndResponse(
        ui: ChatToolPanel,
        prompter: String,
        withDiff: Boolean,
        editingMessage: ChatContextMessage?,
        llmConfig: LlmConfig,
        trigger: AutoDevInputTrigger
    ) {
        val session = getCurrentSession()
        currentJob?.cancel()
        val projectStructure = DirectoryUtil.getDirectoryContents(project)

        var message = editingMessage
        if (editingMessage == null) {
            message = ChatContextMessage(ChatRole.user, prompter)
            val addMessage = ui.addMessage(prompter, true, prompter, null, message)
            addMessage.scrollToBottom()
            getCurrentSession().add(message)
        }

        if (trigger == AutoDevInputTrigger.CopyPrompt) {
            addContextToMessages(message!!, project)
            val chatHistory = exportChatHistory(false)
            ClipboardUtils.copyToClipboard(chatHistory)
            return
        }

        val messageView = ui.addMessage("Loading", chatMessage = null)
        messageView.scrollToBottom()

        ApplicationManager.getApplication().executeOnPooledThread {
            addContextToMessages(message!!, project)
            val messages: MutableList<ChatMessage> = getCurrentSession().transformMessages()
            exportChatHistory()
            saveSessions()
            ui.progressBar.isVisible = true
            ui.progressBar.isIndeterminate = true  // 设置为不确定状态
            ui.updateUI()
            val responseStream = LlmProvider.stream(messages, llmConfig = llmConfig)
            currentJob = LLMCoroutineScope.scope(project).launch {
                var text = ""
                var hasError = false  // 添加错误标志
                responseStream.onCompletion {
                    logger.warn("onCompletion ${it?.message}")
                }.catch {
                    logger.error("exception happens: ", it)
                    text = "exception happens: " + it.message.toString()
                    hasError = true  // 设置错误标志
                }.collect {
                    text += it
                    messageView.updateContent(text)
                }

                logger.warn("LLM response: ${JsonUtils.toJson(text)}")
                messageView.message = text
                messageView.reRender()

                ui.inputSection.showSendButton()
                ui.progressBar.isIndeterminate = false // 处理完成后恢复确定状态
                ui.progressBar.isVisible = false
                ui.updateUI()

                getCurrentSession().add(ChatContextMessage(ChatRole.assistant, text))
                exportChatHistory()
                saveSessions()

                // 只在没有错误时执行 GenerateDiffAgent
                if (!hasError && withDiff) {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        GenerateDiffAgent.apply(project, llmConfig, projectStructure, text, getCurrentSession(), ui)
                    }
                }
            }
        }
    }

    private fun addContextToMessages(message: ChatContextMessage, project: Project) {
        val contextBuilder = StringBuilder()

        // 添加文件内容（如果启用且有文件）
        if (CommonSettings.getInstance().withFiles && getCurrentSession().projectFileTrees.isNotEmpty()) {
            contextBuilder.append("相关项目文件内容：\n")
            val fileContents = getCurrentSession().projectFileTrees.joinToString("\n\n") { projectFileTree ->
                """
                === Project: ${projectFileTree.projectName} ===
                ${collectFileContents(projectFileTree.files).joinToString("\n")}
                """.trimIndent()
            }
            contextBuilder.append(FileUtil.wrapBorder(fileContents))
            contextBuilder.append("\n\n")
        }

        // 添加项目目录结构（如果启用）
        if (CommonSettings.getInstance().withDir) {
            val projectStructure = DirectoryUtil.getProjectStructure(project)
            contextBuilder.append("""
                项目目录结构：
                ${FileUtil.wrapBorder(projectStructure)}
            """.trimIndent())
        }

        val context = contextBuilder.toString().trim()
        if (context.isNotBlank()) {
            message.context = context
        }
    }

    private fun collectFileContents(files: List<VirtualFile>): List<String> {
        return files.mapNotNull { file ->
            if (!file.isDirectory) {
                FileUtil.readFileInfoForLLM(file, project)
            } else {
                null // 忽略目录
            }
        }
    }

    private fun confirmAndExecuteActions(actions: List<Action>, ui: ChatToolPanel) {
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
        val newSession = ChatSession(
            id = sessionId,
            type = "chat",
            project = project.name
        )

        currentSessionId = sessionId
        sessions[sessionId] = newSession
        saveSessions()

        // 获取并刷新 panel 的 file list
        val contentPanel = LLMChatToolWindowFactory.getPanel(project)
        contentPanel?.refreshFileList()
    }

    fun exportChatHistory(invalidContext: Boolean = false): String {
        return getCurrentSession().exportChatHistory(invalidContext)
    }

    companion object {
        fun getInstance(project: Project): ChatCodingService {
            return project.getService(ChatCodingService::class.java)
        }

        private val logger = logger<ChatCodingService>()
    }

    override fun dispose() {
        // 清理资源
        stop()
        sessionListeners.clear()
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
        if (sessionListeners == null) {
            return
        }
        sessionListeners.forEach { listener ->
            listener.sessionListChanged()
        }
    }

    fun addFileToCurrentSession(file: VirtualFile) {
        val currentSession = getCurrentSession()
        val projectTree = currentSession.projectFileTrees.find { it.projectName == project.name }

        if (projectTree != null) {
            // 如果文件不在列表中才添加
            if (!projectTree.files.contains(file)) {
                currentSession.projectFileTrees = currentSession.projectFileTrees.map {
                    if (it.projectName == project.name) {
                        it.copy(files = it.files + file)
                    } else {
                        it
                    }
                }.toMutableList()
            }
        } else {
            // 如果项目不存在，创建新的 ProjectFileTree
            currentSession.projectFileTrees = (currentSession.projectFileTrees +
                    ProjectFileTree(project.name, listOf(file))).toMutableList()
        }

        saveSessions()

        // 获取并刷新 panel 的 file list
        val contentPanel = LLMChatToolWindowFactory.getPanel(project)
        contentPanel?.refreshFileList()
    }
}

data class Action(val description: String, val commandType: String, val command: String)

data class ChatSession(
    val id: String,
    val messages: MutableList<ChatContextMessage> = mutableListOf(),
    val startTime: Long = System.currentTimeMillis(),
    val type: String,
    var projectFileTrees: MutableList<ProjectFileTree> = mutableListOf(),
    var withFiles: Boolean = true,
    val project: String
) {
    // 添加序列化方法
    fun toSerializable(): SerializableChatSession {
        return SerializableChatSession(
            id = id,
            messages = messages,
            startTime = startTime,
            type = type,
            withFiles = withFiles,
            projectFileTrees = projectFileTrees.map { it.toSerializable() }.toMutableList(),
            projectName = project
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
    fun exportChatHistory(invalidContext: Boolean = false): String {
        val chatHistory = transformMessages(invalidContext).joinToString("\n\n") {
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
        val dateFormat = SimpleDateFormat("yy-MM-dd HH-mm-ss")
        val formattedDate = dateFormat.format(date)
        return "$formattedDate-$type.txt"
    }

    // 获取插件数据存储目录
    private fun getPluginDataDirectory(): String {
        val userHome = System.getProperty("user.home")
        return "$userHome/.gpttools"
    }

    fun transformMessages(invalidContext: Boolean = false): MutableList<ChatMessage> {
        val chatMessages: MutableList<ChatMessage>
        // 找到最后一个用户消息的索引
        val lastUserMessageIndex = messages.indexOfLast { it.role == ChatRole.user }

        // 映射每条消息，根据是否有 context 以及是否是最后一个用户消息进行处理
        chatMessages = messages.mapIndexed { index, it ->
            when {
                // 1. 没有 context
                it.context.isBlank() -> ChatMessage(it.role, it.content)

                // 2. 有 context 但不是最后一个用户消息
                index != lastUserMessageIndex || invalidContext -> {
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
                // 3. 有 context 且是最后一个用户消息
                else -> {
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
            }
        }.toMutableList()
        return chatMessages
    }

    companion object {
        private val logger = logger<ChatCodingService>()
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
    val withFiles: Boolean = true,
    val projectFileTrees: List<SerializableProjectFileTree> = emptyList(),
    val projectName: String = ""
) {
    fun toChatSession(project: Project): ChatSession {
        val projectFileTrees = projectFileTrees.map { it.toProjectFileTree() }.toMutableList()
        return ChatSession(
            id = id,
            messages = messages,
            startTime = startTime,
            type = type,
            withFiles = withFiles,
            projectFileTrees = projectFileTrees,
            project = projectName
        )
    }
}

data class ProjectFileTree(
    val projectName: String,
    val files: List<VirtualFile>
) {
    fun toSerializable(): SerializableProjectFileTree {
        return SerializableProjectFileTree(
            projectName = projectName,
            filePaths = files.map { it.path }
        )
    }
}

@Serializable
data class SerializableProjectFileTree(
    val projectName: String,
    val filePaths: List<String>
) {
    fun toProjectFileTree(): ProjectFileTree {
        val files = filePaths.mapNotNull { path ->
            LocalFileSystem.getInstance().findFileByPath(path)
        }
        return ProjectFileTree(
            projectName = projectName,
            files = files
        )
    }
}
