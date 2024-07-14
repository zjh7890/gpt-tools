package com.github.zjh7890.gpttools.toolWindow.llmChat

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.zjh7890.gpttools.llms.LLMProvider
import com.github.zjh7890.gpttools.llms.LlmFactory
import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.settings.llmSettings.GptToolSettings
import com.github.zjh7890.gpttools.toolWindow.chat.*
import com.github.zjh7890.gpttools.toolWindow.chat.block.CodeBlockView
import com.github.zjh7890.gpttools.toolWindow.chat.block.SimpleMessage
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

class LLMChatToolPanel(val disposable: Disposable?, val project: Project) :
    SimpleToolWindowPanel(true, true),
    NullableComponent {
    private val logger = logger<LLMChatToolPanel>()

    private var progressBar: JProgressBar
    private val myTitle = JBLabel("Conversation")
    private val myList = JPanel(VerticalLayout(JBUI.scale(10)))
    private var inputSection: AutoDevInputSection
    private val focusMouseListener: MouseAdapter
    private var panelContent: DialogPanel
    private val myScrollPane: JBScrollPane
    private val delaySeconds: String get() = GptToolSettings.getInstance().delaySeconds

    private var suggestionPanel: JPanel = JPanel(BorderLayout())

    private val chatCodingService: ChatCodingService = ChatCodingService.getInstance(project)

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

        inputSection = AutoDevInputSection(chatCodingService.project, disposable)
        inputSection.addListener(object : AutoDevInputListener {
            override fun onStop(component: AutoDevInputSection) {
                chatCodingService.stop()
                inputSection.showSendButton()
            }

            override fun onSubmit(component: AutoDevInputSection, trigger: AutoDevInputTrigger) {
                var prompt = component.text
                component.text = ""

                inputSection.showStopButton()

                if (prompt.isEmpty() || prompt == "\n") {
                    return
                }

                chatCodingService.handlePromptAndResponse(this@LLMChatToolPanel, prompt, false)
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
        val focusManager = IdeFocusManager.getInstance(chatCodingService.project)
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
        progressBar.isIndeterminate = true
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

    suspend fun updateMessage(content: Flow<String>): String {
        if (myList.componentCount > 0) {
            myList.remove(myList.componentCount - 1)
        }

        progressBar.isVisible = true

        val result = updateMessageInUi(content)

        progressBar.isIndeterminate = false
        progressBar.isVisible = false
        updateUI()

        return result
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

    /**
     * Updates the replaceable content in the UI using the provided `Flow<String>`.
     *
     * @param content The flow of strings to update the UI with.
     * @param postAction A function that is called when the "Replace Selection" button is clicked,
     *                            passing the current text to be replaced in the editor.
     */
    suspend fun updateReplaceableContent(content: Flow<String>, postAction: (text: String) -> Unit) {
        myList.remove(myList.componentCount - 1)
        val text = updateMessageInUi(content)

        progressBar.isIndeterminate = false
        progressBar.isVisible = false
        updateUI()

        postAction(text)
    }

    private suspend fun updateMessageInUi(content: Flow<String>): String {
        val messageView = MessageView("", ChatRole.Assistant, "", project)
        myList.add(messageView)
        val startTime = System.currentTimeMillis() // 记录代码开始执行的时间

        var text = ""
        content.onCompletion {
            logger.info("onCompletion ${it?.message}")
            inputSection.showSendButton()
        }.catch {
            it.printStackTrace()
        }.collect {
            text += it

            messageView.updateContent(text)
            messageView.scrollToBottom()
        }

        if (delaySeconds.isNotEmpty()) {
            val elapsedTime = System.currentTimeMillis() - startTime
            // waiting for the last message to be rendered, like sleep 5 ms?
            // 此处的 20s 出自 openAI 免费账户访问 3/min
            withContext(Dispatchers.IO) {
                val delaySec = delaySeconds.toLong()
                val remainingTime = maxOf(delaySec * 1000 - elapsedTime, 0)
                delay(remainingTime)
            }
        }

        messageView.reRenderAssistantOutput()

        return text
    }

    fun setInput(trimMargin: String) {
        inputSection.text = trimMargin
        this.focusInput()
    }

    /**
     * Resets the chat session by clearing the current session and updating the UI.
     */
    fun resetChatSession() {
        chatCodingService.clearSession()
        progressBar.isVisible = false
        myList.removeAll()
        this.hiddenProgressBar()
        updateUI()
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

    fun appendWebView(content: String, project: Project) {
//        val msg = SimpleMessage(content, content, ChatRole.System)
//        val webBlock = WebBlock(msg)
//        val blockView = WebBlockView(webBlock, project)
//        val codeView = CodeBlockView(CodeBlock(msg, language = HTMLLanguage.INSTANCE), project, {})
//
//        myList.add(FrontendCodeView(blockView, codeView))

        updateUI()
    }

    fun moveCursorToStart() {
        inputSection.moveCursorToStart()
    }

    fun showSuggestion(msg: @Nls String) {
        val label = panel {
            row {
                link(msg) {
                    inputSection.text = msg
                    inputSection.requestFocus()

                    suggestionPanel.removeAll()
                    updateUI()
                }.also {
                    it.component.foreground = JBColor.namedColor("Link.activeForeground", JBColor(Gray.x80, Gray.x8C))
                }
            }
        }

        suggestionPanel.add(label)
        updateUI()
    }
}
