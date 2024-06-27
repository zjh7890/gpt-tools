package com.github.zjh7890.gpttools.toolWindow.chat

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.NullableComponent
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

class ChatToolPanel(
    val disposable: Disposable?,
    val project: Project
) :
    SimpleToolWindowPanel(true, true),
    NullableComponent {
    private val logger = logger<ChatToolPanel>()

    private var progressBar: JProgressBar
    private val myTitle = JBLabel("Conversation")
    private val myList = JPanel(VerticalLayout(JBUI.scale(10)))
    private var inputSection: AutoDevInputSection
    private val focusMouseListener: MouseAdapter
    private var panelContent: DialogPanel
    private val myScrollPane: JBScrollPane

    private var suggestionPanel: JPanel = JPanel(BorderLayout())

    init {
        focusMouseListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                focusInput()
            }
        }

        myTitle.foreground = JBColor.namedColor("Label.infoForeground", JBColor(Gray.x80, Gray.x8C))
        myTitle.font = JBFont.label()

        myList.isOpaque = true
        myList.background = UIUtil.getListBackground()

        myScrollPane = JBScrollPane(
            myList,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        )
        myScrollPane.verticalScrollBar.autoscrolls = true
        myScrollPane.background = UIUtil.getListBackground()

        progressBar = JProgressBar()

        inputSection = AutoDevInputSection(project, disposable)
        inputSection.addListener(object : AutoDevInputListener {
            override fun onStop(component: AutoDevInputSection) {
                inputSection.showSendButton()
            }

            override fun onSubmit(component: AutoDevInputSection, trigger: AutoDevInputTrigger) {
                var functionCallsStr = component.text
                component.text = ""

//                inputSection.showStopButton()

                if (functionCallsStr.isEmpty() || functionCallsStr == "\n") {
                    return
                }

                addMessage(functionCallsStr, isMe = true)
            }
        })

        panelContent = panel {
            row { cell(myScrollPane).fullWidth().fullHeight() }.resizableRow()
            row { cell(suggestionPanel).fullWidth() }
            row { cell(progressBar).fullWidth() }
            row {
                border = JBUI.Borders.empty(8)
                cell(inputSection).fullWidth()
            }
        }

        setContent(panelContent)
    }

    fun focusInput() {
        val focusManager = IdeFocusManager.getInstance(project)
        focusManager.doWhenFocusSettlesDown {
            focusManager.requestFocus(this.inputSection.focusableComponent, true)
        }
    }

    /**
     * Add a message to the chat panel and update ui
     */
    fun addMessage(message: String, isMe: Boolean = false, displayPrompt: String = ""): MessageView {
        val role = if (isMe) ChatRole.User else ChatRole.Assistant
        val displayText = displayPrompt.ifEmpty { message }

        val messageView = MessageView(message, role, displayText, project)

        myList.add(messageView)
        updateLayout()
        scrollToBottom()
//        progressBar.isIndeterminate = true
        updateUI()
        return messageView
    }

    private fun updateLayout() {
        val layout = myList.layout
        for (i in 0 until myList.componentCount) {
            layout.removeLayoutComponent(myList.getComponent(i))
            layout.addLayoutComponent(null, myList.getComponent(i))
        }
    }

    fun getMessageView(): MessageView {
        return myList.getComponent(myList.componentCount - 1) as MessageView
    }

    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val verticalScrollBar = myScrollPane.verticalScrollBar
            verticalScrollBar.value = verticalScrollBar.maximum
        }
    }

    override fun isNull(): Boolean {
        return !isVisible
    }

    fun messageEnd(allEnd: Boolean) {
        val messageView: MessageView = myList.getComponent(myList.componentCount - 1) as MessageView
//        if (delaySeconds.isNotEmpty()) {
//            val elapsedTime = System.currentTimeMillis() - startTime
//            // waiting for the last message to be rendered, like sleep 5 ms?
//            // 此处的 20s 出自 openAI 免费账户访问 3/min
//            withContext(Dispatchers.IO) {
//                val delaySec = delaySeconds.toLong()
//                val remainingTime = maxOf(delaySec * 1000 - elapsedTime, 0)
//                delay(remainingTime)
//            }
//        }

        messageView.reRenderAssistantOutput()

        if (allEnd) {
            inputSection.showSendButton()

            progressBar.isIndeterminate = false
            progressBar.isVisible = false
        }

        updateUI()
    }


    fun setInput(trimMargin: String) {
        inputSection.text = trimMargin
        this.focusInput()
    }

    fun hiddenProgressBar() {
        progressBar.isVisible = false
    }

    fun removeLastMessage() {
        if (myList.componentCount > 0) {
            myList.remove(myList.componentCount - 1)
        }

        updateUI()
    }

    fun moveCursorToStart() {
        inputSection.moveCursorToStart()
    }

    /**
     * Resets the chat session by clearing the current session and updating the UI.
     */
    fun resetChatSession() {
        progressBar.isVisible = false
        myList.removeAll()
        this.hiddenProgressBar()
        updateUI()
    }


    companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}

