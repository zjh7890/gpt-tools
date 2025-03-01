package com.github.zjh7890.gpttools.toolWindow.chat

import CodeChangeBlockView
import com.github.zjh7890.gpttools.agent.GenerateDiffAgent
import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.services.ChatContextMessage
import com.github.zjh7890.gpttools.services.SessionManager
import com.github.zjh7890.gpttools.settings.llmSetting.LLMSettingsState
import com.github.zjh7890.gpttools.toolWindow.chat.block.*
import com.github.zjh7890.gpttools.toolWindow.llmChat.ChatPanel
import com.github.zjh7890.gpttools.toolWindow.llmChat.LLMChatToolWindowFactory
import com.github.zjh7890.gpttools.utils.DirectoryUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.*
import javax.swing.*

class MessageView(
    var message: String,
    val role: ChatRole,
    private val displayText: String,
    val project: Project,
    render: Boolean,
    val chatMessage: ChatContextMessage?,
    val chatPanel: ChatPanel
) : JBPanel<MessageView>() {
    private val myNameLabel: Component
    private val component: DisplayComponent = DisplayComponent(message)
    var centerPanel: JPanel = JPanel(VerticalLayout(JBUI.scale(8)))
    val componentList : MutableList<Component> = mutableListOf(component)

    init {
        isDoubleBuffered = true
        isOpaque = true
        background = when (role) {
            ChatRole.system -> JBColor(0xEAEEF7, 0x393b41)
            ChatRole.assistant -> JBColor(0xEAEEF7, 0x393b41)
            ChatRole.user -> JBColor(0xE0EEF7, 0x393b41)
        }


        val authorLabel = JLabel()
        authorLabel.font = JBFont.h4()
        authorLabel.text = when (role) {
            ChatRole.system -> "System"
            ChatRole.assistant -> "Assistant"
            ChatRole.user -> "User"
        }
        myNameLabel = authorLabel

        this.border = JBEmptyBorder(8)
        layout = BorderLayout(JBUI.scale(8), 0)

        centerPanel = JPanel(VerticalLayout(JBUI.scale(8)))
        centerPanel.isOpaque = false
        centerPanel.border = JBUI.Borders.emptyRight(8)


//        centerPanel.add(myNameLabel)
        centerPanel.add(createTitlePanel(this))
        add(centerPanel, BorderLayout.CENTER)

        if (role == ChatRole.user || render) {
            ApplicationManager.getApplication().invokeLater {
                val simpleMessage = SimpleMessage(displayText, message, role)
                renderInPartView(simpleMessage)
            }
        } else {
            component.updateMessage(message)
            component.revalidate()
            component.repaint()
            centerPanel.add(component)
        }
    }

    private fun createTitlePanel(messageView: MessageView): JPanel {
        val panel = BorderLayoutPanel()
        panel.isOpaque = false
        panel.addToCenter(this.myNameLabel)

        val group = DefaultActionGroup().apply {
            if (role == ChatRole.user) {
                add(EditMessageAction(messageView, chatPanel))
            }
            // 如果需要，您可以为助手消息添加其他操作
            if (role == ChatRole.assistant) {
                add(ApplyResponseAction(messageView))
            }
        }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("MessageViewToolbar", group, true)
        toolbar.component.isOpaque = false
        toolbar.component.border = JBUI.Borders.empty()
        toolbar.targetComponent = this
        panel.addToRight(toolbar.component)

        panel.isOpaque = false
        return panel
    }

    fun renderInPartView(message: SimpleMessage) {
        val processor = MessageCodeBlockCharProcessor()
        val parts = processor.getParts(message)
        parts.forEach {
            val blockView = when (it) {
                is CodeBlock -> {
                    CodeBlockView(it, project) { }
                }
                is CodeChange -> {
                    CodeChangeBlockView(it, project)
                }
                else -> TextBlockView(it)
            }

            blockView.initialize()
            val component = blockView.getComponent() ?: return@forEach

            component.foreground = JBUI.CurrentTheme.Label.foreground()
            componentList.add(component)
            centerPanel.add(component)
        }
    }

    var answer: String = ""

    fun updateContent(content: String) {
        this.answer = content

        MessageWorker(content).execute()
    }

    fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val bounds: Rectangle = bounds
            scrollRectToVisible(bounds)
        }
    }

    fun reRender() {
        ApplicationManager.getApplication().invokeLater {
            for (displayComponent in componentList) {
                centerPanel.remove(displayComponent)
            }
            componentList.clear()
            centerPanel.updateUI()

            val message = SimpleMessage(answer, answer, ChatRole.assistant)
            renderInPartView(message)

            centerPanel.revalidate()
            centerPanel.repaint()
        }
    }

    internal inner class MessageWorker(private val message: String) : SwingWorker<Void?, String?>() {
        @Throws(Exception::class)
        override fun doInBackground(): Void? {
            return null
        }

        override fun done() {
            try {
                get()
                component.updateMessage(message)
                component.updateUI()
            } catch (e: Exception) {
                logger.error(message, e.message)
            }
        }
    }

    companion object {
        private val logger = logger<MessageView>()
    }
}

class EditMessageAction(private val messageView: MessageView, val chatPanel: ChatPanel) :
    AnAction("Edit", "Edit this message", AllIcons.Actions.Edit) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val chatCodingService = ChatCodingService.getInstance(project)
        // 设置 AutoDevInputSection 为编辑模式，并设置要编辑的消息
        chatPanel.setEditingMessage(messageView)
    }
}

class ApplyResponseAction(val messageView: MessageView) : AnAction("Generate diff based on this message", "Generate diff according to this message", AllIcons.Actions.ToggleVisibility) {
    override fun actionPerformed(e: AnActionEvent) {
        if (e.project == null) {
            return
        }
        val projectStructure = DirectoryUtil.getDirectoryContents(e.project!!)
        val chatCodingService = ChatCodingService.getInstance(e.project!!)
        val contentPanel = LLMChatToolWindowFactory.getPanel(e.project!!)

        ApplicationManager.getApplication().executeOnPooledThread {
            GenerateDiffAgent.apply(
                e.project!!,
                LLMSettingsState.getLlmConfig(),
                projectStructure,
                messageView.message,
                SessionManager.getInstance(e.project!!).getCurrentSession(),
                contentPanel!!
            )
        }
    }
}
