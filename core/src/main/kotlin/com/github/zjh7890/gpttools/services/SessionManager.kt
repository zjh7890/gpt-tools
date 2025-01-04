package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.UUID

@Service(Service.Level.PROJECT)
class SessionManager(private val project: Project) : Disposable {
    private val logger = logger<SessionManager>()

    private val sessions = mutableMapOf<String, ChatSession>()
    private var currentSessionId: String = ""
    private val sessionHistoryListeners = mutableListOf<SessionHistoryListener>()

    private val sessionFilePath: String = getSessionFilePath()

    init {
        loadSessions()
    }

    private fun getSessionFilePath(): String {
        val userHome = System.getProperty("user.home")
        return "$userHome/.gpttools/chat_sessions.json"
    }

    private fun loadSessions() {
        val sessionsData: List<SerializableChatSession>? = FileUtil.readJsonFromFile(sessionFilePath)
        sessionsData?.forEach { data ->
            val session = data.toChatSession(project)
            sessions[session.id] = session
        }
        val currentProjectSessions = sessions.values.filter { it.project == project.name }

        if (currentProjectSessions.isEmpty()) {
            createNewSession()
        } else {
            currentSessionId = currentProjectSessions.maxByOrNull { it.startTime }?.id
                ?: currentProjectSessions.first().id
        }
    }

    /**
     * 创建一个新的会话
     */
    fun createNewSession() {
        val sessionId = UUID.randomUUID().toString()
        val newSession = ChatSession(
            id = sessionId,
            type = "chat",
            project = project.name,
            startTime = System.currentTimeMillis()
        )

        currentSessionId = sessionId
        sessions[sessionId] = newSession
        saveSessions()

        notifySessionListChanged()
    }

    /**
     * 获取当前激活的会话
     */
    fun getCurrentSession(): ChatSession {
        return sessions[currentSessionId] ?: throw IllegalStateException("No current session found")
    }

    /**
     * 获取所有会话列表
     */
    fun getSessionList(): List<ChatSession> {
        return sessions.values.toList()
    }

    /**
     * 设置当前激活的会话
     */
    fun setCurrentSession(sessionId: String) {
        if (sessions.containsKey(sessionId)) {
            currentSessionId = sessionId
            notifySessionListChanged()
        } else {
            logger.warn("Attempted to set a non-existent session: $sessionId")
        }
    }

    /**
     * 保存所有会话到文件
     */
    fun saveSessions() {
        val sessionsData = sessions.values.map { it.toSerializable() }
        FileUtil.writeJsonToFile(sessionFilePath, sessionsData)
        notifySessionListChanged()
    }

    /**
     * 添加会话监听器
     */
    fun addSessionListener(listener: SessionHistoryListener) {
        sessionHistoryListeners.add(listener)
    }

    /**
     * 通知所有监听器会话列表已更改
     */
    private fun notifySessionListChanged() {
        sessionHistoryListeners.forEach { it.sessionListChanged() }
    }

    /**
     * 将文件添加到当前会话
     */
    fun addFileToCurrentSession(file: VirtualFile) {
        val currentSession = getCurrentSession()
        val projectTree = currentSession.projectFileTrees.find { it.projectName == project.name }

        if (projectTree != null) {
            if (!projectTree.files.contains(file)) {
                projectTree.files += file
            }
        } else {
            currentSession.projectFileTrees += ProjectFileTree(project.name, listOf(file).toMutableList())
        }

        saveSessions()
        notifySessionListChanged()
    }

    /**
     * 添加本地消息到当前会话
     */
    fun appendLocalMessage(role: ChatRole, msg: String): ChatContextMessage {
        val message = ChatContextMessage(role, msg)
        getCurrentSession().add(message)
        saveSessions()
        notifySessionListChanged()
        return message
    }

    /**
     * 导出当前会话的聊天历史
     */
    fun exportChatHistory(invalidContext: Boolean = false): String {
        return getCurrentSession().exportChatHistory(invalidContext)
    }

    /**
     * 截断指定消息之后的所有消息
     */
    fun truncateMessagesAfter(chatMessage: ChatContextMessage) {
        val currentSession = getCurrentSession()
        val index = currentSession.messages.indexOf(chatMessage)
        if (index >= 0 && index < currentSession.messages.size - 1) {
            currentSession.messages.subList(index + 1, currentSession.messages.size).clear()
            saveSessions()
            notifySessionListChanged()
        }
    }

    override fun dispose() {
        sessionHistoryListeners.clear()
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): SessionManager {
            return project.getService(SessionManager::class.java)
        }
    }
}
