package com.github.zjh7890.gpttools.toolWindow.chat


import CodeChangeBlockView
import com.github.zjh7890.gpttools.toolWindow.chat.block.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
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
import kotlin.jvm.internal.Ref

class MessageView(private val message: String, val role: ChatRole, private val displayText: String, val project: Project) :
    JBPanel<MessageView>() {
    private val myNameLabel: Component
    private val component: DisplayComponent = DisplayComponent(message)
    private var centerPanel: JPanel = JPanel(VerticalLayout(JBUI.scale(8)))

    init {
        isDoubleBuffered = true
        isOpaque = true
        background = JBColor(0xEAEEF7, 0x45494A)

        val authorLabel = JLabel()
        authorLabel.setFont(JBFont.h4())
        authorLabel.setText(when (role) {
            ChatRole.System -> "System"
            ChatRole.Assistant -> "Assistant"
            ChatRole.User -> "User"
        })
        myNameLabel = authorLabel

        this.border = JBEmptyBorder(8)
        layout = BorderLayout(JBUI.scale(8), 0)

        centerPanel = JPanel(VerticalLayout(JBUI.scale(8)))
        centerPanel.isOpaque = false
        centerPanel.border = JBUI.Borders.emptyRight(8)

        centerPanel.add(myNameLabel)
        add(centerPanel, BorderLayout.CENTER)

        if (role == ChatRole.User) {
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

    private fun createTitlePanel(): JPanel {
        val panel = BorderLayoutPanel()
        panel.setOpaque(false)
        panel.addToCenter(this.myNameLabel)

        val group = ActionUtil.getActionGroup("AutoDev.ToolWindow.Message.Toolbar.Assistant")

        if (group != null) {
            val toolbar = ActionToolbarImpl(javaClass.getName(), group, true)
            toolbar.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
            toolbar.component.setOpaque(false)
            toolbar.component.setBorder(JBUI.Borders.empty())
            toolbar.setTargetComponent(this)
            panel.addToRight(toolbar.component)
        }

        panel.setOpaque(false)
        return panel
    }

    private fun renderInPartView(message: SimpleMessage) {
        val parts = layoutAll(message)
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

            component.setForeground(JBUI.CurrentTheme.Label.foreground())
            centerPanel.add(component)
        }
    }

    private var answer: String = ""

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

    fun reRenderAssistantOutput() {
        ApplicationManager.getApplication().invokeLater {
            centerPanel.remove(component)
            centerPanel.updateUI()

            centerPanel.add(myNameLabel)
            centerPanel.add(createTitlePanel())

            val message = SimpleMessage(answer, answer, ChatRole.Assistant)
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
        private fun createPart(
            blockStart: Int,
            partUpperOffset: Int,
            messageText: String,
            currentContextType: Ref.ObjectRef<MessageBlockType>,
            message: CompletableMessage
        ): MessageBlock {
            check(blockStart < messageText.length)
            check(partUpperOffset < messageText.length)

            val blockText = messageText.substring(blockStart, partUpperOffset + 1)
            val part: MessageBlock = when (currentContextType.element!!) {
                MessageBlockType.CodeEditor -> CodeBlock(message)
                MessageBlockType.PlainText -> TextBlock(message)
                MessageBlockType.CodeChange -> CodeChange(message)
            }

            if (blockText.isNotEmpty()) {
                part.addContent(blockText)
            }

            return part
        }

        private fun pushPart(
            blockStart: Ref.IntRef,
            messageText: String,
            currentContextType: Ref.ObjectRef<MessageBlockType>,
            message: CompletableMessage,
            list: MutableList<MessageBlock>,
            partUpperOffset: Int
        ) {
            val newPart = createPart(blockStart.element, partUpperOffset, messageText, currentContextType, message)
            list.add(newPart)

            blockStart.element = partUpperOffset + 1
            currentContextType.element = MessageBlockType.PlainText
        }

        fun layoutAll(message: CompletableMessage): List<MessageBlock> {
            val messageText: String = message.displayText
            val contextTypeRef = Ref.ObjectRef<MessageBlockType>()
            contextTypeRef.element = MessageBlockType.PlainText

            val blockStart: Ref.IntRef = Ref.IntRef()

            val parts = mutableListOf<MessageBlock>()

            for ((index, item) in messageText.withIndex()) {
                val param = Parameters(item, index, messageText)
                val processor = MessageCodeBlockCharProcessor()
                val suggestTypeChange =
                    processor.suggestTypeChange(param, contextTypeRef.element, blockStart.element) ?: continue

                when {
                    suggestTypeChange.contextType == contextTypeRef.element -> {
                        if (suggestTypeChange.borderType == BorderType.START) {
                            logger.error("suggestTypeChange return ${contextTypeRef.element} START while there is already ${contextTypeRef.element} opened")
                        } else {
                            pushPart(blockStart, messageText, contextTypeRef, message, parts, index)
                        }
                    }

                    suggestTypeChange.borderType == BorderType.START -> {
                        if (index > blockStart.element) {
                            pushPart(blockStart, messageText, contextTypeRef, message, parts, index - 1)
                        }

                        blockStart.element = index
                        contextTypeRef.element = suggestTypeChange.contextType
                    }

                    else -> {
                        logger.error("suggestTypeChange return ${contextTypeRef.element} END when there wasn't open tag")
                    }
                }
            }

            if (blockStart.element < messageText.length) {
                pushPart(blockStart, messageText, contextTypeRef, message, parts, messageText.length - 1)
            }

            return parts
        }

    }
}
