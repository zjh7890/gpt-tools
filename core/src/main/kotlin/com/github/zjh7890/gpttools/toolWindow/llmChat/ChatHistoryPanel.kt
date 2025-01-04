package com.github.zjh7890.gpttools.toolWindow.llmChat

import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.services.ChatSession
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

class ChatHistoryPanel(val project: Project) : JPanel(), SessionListener {

    private val chatCodingService = ChatCodingService.getInstance(project)

    private val conversationListModel = DefaultListModel<ChatSession>()
    private val conversationList = JBList<ChatSession>(conversationListModel)

    private val messageListModel = DefaultListModel<String>()
    private val messageList = JBList<String>(messageListModel)

    // 新增按钮字段
    private val restoreButton = JButton("Restore Session").apply {
        addActionListener {
            val selectedSession = conversationList.selectedValue
            if (selectedSession != null) {
                chatCodingService.setCurrentSession(selectedSession.id)
                val contentPanel = LLMChatToolWindowFactory.getPanel(project)
                contentPanel?.reloadConversation()

                // 跳转到 Chat Panel
                val toolWindow = LLMChatToolWindowFactory.getToolWindow(project)
                if (toolWindow != null) {
                    val contentManager = toolWindow.contentManager
                    val chatContent = contentManager.getContent(0)
                    if (chatContent != null) {
                        contentManager.setSelectedContent(chatContent)
                    }
                }
            }
        }
    }

    init {
        layout = BorderLayout()

        // 初始化对话列表
        loadConversationList()

        chatCodingService.addSessionListener(this)

        conversationList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        conversationList.addListSelectionListener {
            val selectedSession = conversationList.selectedValue
            if (selectedSession != null) {
                loadMessagesForSession(selectedSession)
            }
        }

        conversationList.cellRenderer = object : ListCellRenderer<ChatSession> {
            override fun getListCellRendererComponent(
                list: JList<out ChatSession>,
                value: ChatSession,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(value.startTime))
                val displayName = formattedTime
                val label = JLabel(displayName)
                label.isOpaque = true
                if (isSelected) {
                    label.background = list.selectionBackground
                    label.foreground = list.selectionForeground
                } else {
                    label.background = list.background
                    label.foreground = list.foreground
                }
                return label
            }
        }

        // 左侧为会话列表，右侧为消息列表
        val splitPane = JPanel(BorderLayout())

        // 将 restoreButton 添加到 splitPane 的顶部
        val leftPanel = JPanel(BorderLayout())
        leftPanel.add(restoreButton, BorderLayout.NORTH)
        leftPanel.add(JBScrollPane(conversationList), BorderLayout.CENTER)
        splitPane.add(leftPanel, BorderLayout.WEST)

        splitPane.add(JBScrollPane(messageList), BorderLayout.CENTER)
        splitPane.border = JBUI.Borders.empty()

        add(splitPane, BorderLayout.CENTER)
    }

    private fun loadConversationList() {
        conversationListModel.clear()
        val sessions = chatCodingService.getSessionList()
            .filter { it.project == project.name }
        val sortedSessions = sessions.sortedByDescending { it.startTime }
        sortedSessions.forEach { session ->
            conversationListModel.addElement(session)
        }
    }

    private fun loadMessagesForSession(session: ChatSession) {
        messageListModel.clear()
        session.messages.forEach { message ->
            val role = if (message.role == ChatRole.user) "用户" else "助手"
            messageListModel.addElement("$role: ${message.content}")
        }
    }

    override fun sessionListChanged() {
        SwingUtilities.invokeLater {
            loadConversationList()
        }
    }
}

interface SessionListener {
    fun sessionListChanged()
}
