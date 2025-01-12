package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.context.ContextFileToolWindowFactory
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolWindowFactory
import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import java.util.UUID

@Service(Service.Level.PROJECT)
class SessionManager(private val project: Project) : Disposable {
    private val logger = logger<SessionManager>()

    private val sessions = mutableMapOf<String, ChatSession>()
    private var currentSession: ChatSession = ChatSession(project = project.name, projects = mutableListOf(project))
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
            currentSession = currentProjectSessions.maxByOrNull { it.startTime }
                ?: currentProjectSessions.first()
        }
    }

    /**
     * 创建一个新的会话
     */
    fun createNewSession() {
        // 先从当前会话中移除 project
        currentSession.projects.remove(project)

        val sessionId = UUID.randomUUID().toString()
        val newSession = ChatSession(
            id = sessionId,
            type = "chat",
            project = project.name,
            startTime = System.currentTimeMillis(),
            projects = mutableListOf(project)
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
        if (!currentSession.projects.contains(project)) {
            currentSession.projects.add(project)
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

    /**
     * 将文件添加到当前会话，解析文件中的所有类和方法
     */
    fun addFileToCurrentSession(file: VirtualFile) {
        if (!file.isValid || !file.isWritable) {
            logger.warn("Invalid or unwritable file: ${file.path}")
            return
        }

        val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(file) ?: run {
            logger.warn("Unable to find PsiFile for: ${file.path}")
            return
        }

        // 使用 PsiTreeUtil 查找所有 PsiClass 元素，包括嵌套类
        val classes: Collection<PsiClass> = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)

        if (classes.isEmpty()) {
            logger.info("No classes found in file: ${file.path}")
            return
        }

        for (psiClass in classes) {
            val className = psiClass.name
            if (className.isNullOrBlank()) {
                logger.warn("Found a class with no name in file: ${file.path}")
                continue
            }

            // 查找所有方法
            val methods: Array<PsiMethod> = psiClass.methods

            if (methods.isEmpty()) {
                // 如果类中没有方法，也可以选择只添加类
                addClassAndMethodToCurrentSession(className, "")
                logger.info("Added class '$className' with no methods from file: ${file.path}")
            } else {
                for (method in methods) {
                    val methodName = method.name
                    if (methodName.isNullOrBlank()) {
                        logger.warn("Found a method with no name in class '$className' from file: ${file.path}")
                        continue
                    }
                    addClassAndMethodToCurrentSession(className, methodName)
                    logger.info("Added method '$methodName' of class '$className' from file: ${file.path}")
                }
            }
        }

        // 保存会话并通知更新
        saveSessions()
        notifySessionListChanged()

        // 通知所有项目的 fileTreePanel 更新
        notifyAllFileTreePanels(currentSession)
    }

    /**
     * 将类和方法添加到当前会话
     */
    fun addClassAndMethodToCurrentSession(className: String, methodName: String) {
        // 获取当前 session 对应的 ProjectFileTree
        val projectTree = currentSession.projectFileTrees.find { it.projectName == project.name }
            ?: ProjectFileTree(project.name).also { currentSession.projectFileTrees.add(it) }

        // 添加类和方法
        currentSession.addClass(project.name, className, methodName)

        saveSessions()
        notifySessionListChanged()

        // 通知所有项目的 fileTreePanel 更新
        notifyAllFileTreePanels(currentSession)
    }

    /**
     * 移除选中的类或方法
     * @param className 如果为 null，表示移除整个项目的所有内容
     * @param methodNames 如果提供了 className，则移除对应类下的这些方法；如果为 null，则移除整个类
     */
    fun removeSelectedNodes(className: String?, methodNames: List<String>?) {
        val currentSession = getCurrentSession()
        val projectTree = currentSession.projectFileTrees.find { it.projectName == project.name } ?: return

        if (className == null) {
            // 移除整个项目的所有类和方法
            currentSession.projectFileTrees.remove(projectTree)
        } else {
            val projectClass = projectTree.classes.find { it.className == className }
            if (projectClass != null) {
                if (methodNames == null || methodNames.isEmpty()) {
                    // 移除整个类
                    projectTree.classes.remove(projectClass)
                } else {
                    // 移除指定的方法
                    projectClass.methods.removeAll { it.methodName in methodNames }
                }
            }
        }

        saveSessions()
        notifySessionListChanged()

        // 通知所有项目的 fileTreePanel 更新
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
        currentSession.projects.remove(project)
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): SessionManager {
            return project.getService(SessionManager::class.java)
        }
    }
}
