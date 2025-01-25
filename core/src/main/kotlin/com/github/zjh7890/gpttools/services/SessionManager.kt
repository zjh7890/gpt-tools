package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolWindowFactory
import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import java.util.UUID

@Service(Service.Level.PROJECT)
class SessionManager(private val project: Project) : Disposable {
    private val logger = logger<SessionManager>()

    private val sessions = mutableMapOf<String, ChatSession>()
    private var currentSession: ChatSession = ChatSession(project = project, relevantProjects = mutableListOf(project))
    private val sessionFilePath: String = getSessionFilePath()

    init {
        loadSessions()
    }

    private fun getSessionFilePath(): String {
        val userHome = System.getProperty("user.home")
        return "$userHome/.gpttools/chat_sessions2.json"
    }

    private fun loadSessions() {
        try {
            val sessionsData: List<SerializableChatSession>? = FileUtil.readJsonFromFile(sessionFilePath)
            sessionsData?.forEach { data ->
                try {
                    val session = data.toChatSession()
                    sessions[session.id] = session
                } catch (e: Exception) {
                    logger.error("Failed to deserialize chat session: ${data}", e)
                }
            }
            val currentProjectSessions = sessions.values.filter { it.project == project }

            if (currentProjectSessions.isEmpty()) {
                createNewSession()
            } else {
                currentSession = currentProjectSessions.maxByOrNull { it.startTime }
                    ?: currentProjectSessions.first()
            }
        } catch (e: Exception) {
            logger.error("Failed to load chat sessions from file: $sessionFilePath", e)
            // 如果加载失败，创建新会话
            createNewSession()
        }
    }

    /**
     * 创建一个新的会话
     */
    fun createNewSession() {
        // 先从当前会话中移除 project
        currentSession.relevantProjects.remove(project)

        val sessionId = UUID.randomUUID().toString()
        val newSession = ChatSession(
            id = sessionId,
            type = "chat",
            project = project,
            startTime = System.currentTimeMillis(),
            relevantProjects = mutableListOf(project)
        )

        currentSession = newSession
        sessions[sessionId] = newSession
        saveSessions()

        notifySessionListChanged()
    }

    /**
     * 获取当前激活的会话
     */
    fun getCurrentSession(): ChatSession {
        return currentSession
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
    fun setCurrentSession(session: ChatSession, project: Project) {
        // 设置当前会话
        currentSession = session
        if (!currentSession.relevantProjects.contains(project)) {
            currentSession.relevantProjects.add(project)
        }

        // 获取并刷新所有项目的 fileTreePanel 的 file list
        notifyAllFileTreePanels(currentSession)
        notifySessionListChanged()
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
     * 通知所有监听器会话列表已更改
     */
    private fun notifySessionListChanged() {
        LLMChatToolWindowFactory.getHistoryPanel(project)?.loadConversationList()
    }

    fun addFileToCurrentSession(file: VirtualFile) {
        if (!file.isValid || !file.isWritable) {
            logger.warn("Invalid or unwritable file: ${file.path}")
            return
        }

        currentSession.appFileTree.addFile(file, project)

        // 保存会话并通知更新
        saveSessions()
        notifySessionListChanged()

        // 通知所有项目的 fileTreePanel 更新
        notifyAllFileTreePanels(currentSession)
    }

    fun addMethodToCurrentSession(psiMethod: PsiMethod) {
        currentSession.appFileTree.addMethod(psiMethod, project)

        saveSessions()
        notifySessionListChanged()

        // 通知所有项目的 fileTreePanel 更新
        notifyAllFileTreePanels(currentSession)
    }

    fun removeSelectedNodesFromCurrentSession(selectedNodes: List<Any>) {
        val appFileTree = currentSession.appFileTree

        // 先分类
        val methodList = mutableListOf<ProjectMethod>()
        val classList = mutableListOf<ProjectClass>()
        val fileList = mutableListOf<VirtualFile>()

        selectedNodes.forEach { node ->
            when (node) {
                is ProjectMethod -> methodList.add(node)
                is ProjectClass -> classList.add(node)
                is VirtualFile -> fileList.add(node)
                // 如果还要支持移除“包”/“模块”/“MavenDependency”，可在此增加分支
            }
        }

        // 依次移除
        fileList.forEach { file -> appFileTree.removeFile(file, project) }
        classList.forEach { cls -> appFileTree.removeClass(cls, project) }
        methodList.forEach { m -> appFileTree.removeMethod(m, project) }

        // 移除完毕后，保存 & 刷新
        saveSessions()
        notifyAllFileTreePanels(currentSession)
    }


    /**
     * 通知所有项目的 fileTreePanel 更新文件树
     */
    private fun notifyAllFileTreePanels(session: ChatSession) {
        val openProjects = ProjectManager.getInstance().openProjects
        openProjects.forEach { proj ->
            val panel = LLMChatToolWindowFactory.getPanel(proj)?.chatFileTreeListPanel
            panel?.updateFileTree(session)
            panel?.dependenciesTreePanel?.updateUI()
        }
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
        // 销毁时移除当前 project
        currentSession.relevantProjects.remove(project)
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): SessionManager {
            return project.getService(SessionManager::class.java)
        }
    }
}
