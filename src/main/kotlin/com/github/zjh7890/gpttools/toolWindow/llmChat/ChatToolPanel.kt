package com.github.zjh7890.gpttools.toolWindow.llmChat

import com.github.zjh7890.gpttools.agent.GenerateDiffAgent
import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.services.ChatContextMessage
import com.github.zjh7890.gpttools.settings.common.CommonSettings
import com.github.zjh7890.gpttools.settings.llmSetting.LLMSettingsState
import com.github.zjh7890.gpttools.toolWindow.chat.*
import com.github.zjh7890.gpttools.utils.DirectoryUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.NullableComponent
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class ChatToolPanel(val disposable: Disposable?, val project: Project) :
    SimpleToolWindowPanel(true, true),
    NullableComponent {
    private val logger = logger<ChatToolPanel>()

    var progressBar: JProgressBar
    val myTitle = JBLabel("Conversation")
    val myList = JPanel(VerticalLayout(JBUI.scale(10)))
    var inputSection: AutoDevInputSection
    val withContextCheckbox = JCheckBox("WithContext", true)
    val generateDiffCheckbox = JCheckBox("Generate Diff", CommonSettings.getInstance(project).generateDiff)
    val focusMouseListener: MouseAdapter
    var panelContent: DialogPanel
    val myScrollPane: JBScrollPane

    var suggestionPanel: JPanel = JPanel(BorderLayout())

    val chatCodingService: ChatCodingService = ChatCodingService.getInstance(project)

    var editingMessageView: MessageView? = null
    private val editingLabel = JLabel()
    // 定义退出编辑模式的动作
    val exitEditingAction = object : AnAction("Exit Editing", "Exit editing mode", AllIcons.Actions.Close) {
        override fun actionPerformed(e: AnActionEvent) {
            setEditingMessage(null)
        }
    }
    // 创建退出编辑模式的按钮
    private val exitEditingButton = ActionButton(exitEditingAction, exitEditingAction.templatePresentation.clone(), "", JBUI.size(16))

    private val editingPanel = JPanel(BorderLayout()).apply {
        isVisible = false
        add(editingLabel, BorderLayout.WEST)
        add(exitEditingButton, BorderLayout.EAST)
    }

    // 新增变量
    private val fileListPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isVisible = true
    }

    private val toggleFileListButton = JButton("Hide File List").apply {
        addActionListener {
            fileListPanel.isVisible = !fileListPanel.isVisible
            text = if (fileListPanel.isVisible) "Hide File List" else "Show File List"
            refreshFileList()
        }
    }

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
        // 添加鼠标点击监听器来激活工具窗口
        myList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                focusInput()
            }
        })

        myScrollPane = JBScrollPane(
            myList,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        )
        myScrollPane.verticalScrollBar.autoscrolls = true
        myScrollPane.background = UIUtil.getListBackground()

        editingPanel.isVisible = false

        progressBar = JProgressBar()
        progressBar.isVisible = false

        inputSection = AutoDevInputSection(chatCodingService.project, disposable)
        inputSection.addListener(object : AutoDevInputListener {
            override fun onStop(component: AutoDevInputSection) {
                chatCodingService.stop()
                progressBar.isVisible = false
                progressBar.isIndeterminate = false
                inputSection.showSendButton()
            }

            override fun onSubmit(component: AutoDevInputSection, trigger: AutoDevInputTrigger) {
                var prompt = component.text
                component.text = ""

                if (prompt.isEmpty() || prompt == "\n") {
                    return
                }

                inputSection.showStopButton()

                // 从 inputSection 获取选定的配置项
                val selectedSetting = inputSection.getSelectedSetting()
                // 将其转换为 LlmConfig
                val llmConfig = LLMSettingsState.toLlmConfig(selectedSetting)

                val withContext = withContextCheckbox.isSelected

                val ifEditing = editingMessageView != null
                if (ifEditing) {
                    // 编辑模式下，更新消息内容
                    editingMessageView?.chatMessage?.content = prompt
                    val editingMessage = editingMessageView?.chatMessage

                    // 截断 ChatSession 中的消息
                    val chatCodingService = ChatCodingService.getInstance(project)
                    chatCodingService.truncateMessagesAfter(editingMessageView!!.chatMessage!!)

                    // 删除 UI 中的后续 MessageView
                    removeMessageViewsAfter(editingMessageView!!)

                    // 更新当前 MessageView 的显示内容
                    editingMessageView?.updateContent(prompt)

                    editingMessageView?.reRender()

                    // 退出编辑模式
                    setEditingMessage(null)

                    // 重新处理该消息
                    chatCodingService.handlePromptAndResponse(
                        this@ChatToolPanel,
                        prompt,
                        trigger == AutoDevInputTrigger.SearchContext,
                        editingMessage,
                        llmConfig
                    )
                } else {
                    chatCodingService.handlePromptAndResponse(
                        this@ChatToolPanel,
                        prompt,
                        trigger == AutoDevInputTrigger.SearchContext,
                        null,
                        llmConfig
                    )
                }
            }
        })

        // 添加监听器
        withContextCheckbox.addActionListener {
            chatCodingService.updateWithContext(withContextCheckbox.isSelected)
        }

        generateDiffCheckbox.addActionListener {
            CommonSettings.getInstance(project).generateDiff = generateDiffCheckbox.isSelected
        }

        panelContent = panel {
            row { cell(myScrollPane).fullWidth().fullHeight() }.resizableRow()
            row { cell(suggestionPanel).fullWidth() }
            row { cell(editingPanel).fullWidth() }
            row { cell(progressBar).fullWidth() }
            row {
                cell(withContextCheckbox)
                cell(generateDiffCheckbox)
                // 添加生成 diff 的按钮
                cell(ActionButton(
                    GenerateDiffAction(project, progressBar, inputSection, chatCodingService),
                    Presentation().apply {
                        icon = AllIcons.Actions.ToggleVisibility
                        text = "Generate diff based on this chat"
                        description = "Generate diff based on this chat"
                    },
                    "",
                    JBUI.size(16)
                ))
            }
            row {
                border = JBUI.Borders.empty(8)
                cell(inputSection).fullWidth()
            }
            row {
                cell(fileListPanel).fullWidth()
            }
            row {
                cell(toggleFileListButton).fullWidth()
            }
        }

        setContent(panelContent)

    }

    // 重新加载当前对话
    fun reloadConversation() {
        myList.removeAll()
        val chatCodingService = ChatCodingService.getInstance(project)
        val currentSession = chatCodingService.getCurrentSession()
        currentSession.messages.forEach { message ->
            addMessage(message.content, message.role == ChatRole.user, render = true, chatMessage = message)
        }
        refreshFileList()  // 新增
        updateUI()
    }


    // 新增方法
    fun refreshFileList() {
        fileListPanel.removeAll()
        chatCodingService.getCurrentSession().fileList.forEach { file ->
            val filePanel = JPanel(BorderLayout()).apply {
                maximumSize = Dimension(Int.MAX_VALUE, 30)
                
                // 创建文件名标签
                val fileLabel = JLabel(file.name)
                add(fileLabel, BorderLayout.WEST)

                // 添加鼠标监听器处理双击事件
                fileLabel.addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (e.clickCount == 2) {
                            // 双击时打开文件
                            ApplicationManager.getApplication().invokeLater {
                                FileEditorManager.getInstance(project).openFile(file, true)
                            }
                        }
                    }
                })

                // 定义移除文件的动作
                val removeAction = object : AnAction("Remove File", "Remove File", AllIcons.Actions.Close) {
                    override fun actionPerformed(e: AnActionEvent) {
                        chatCodingService.getCurrentSession().fileList.remove(file)
                        refreshFileList()
                    }
                }
                // 创建移除按钮
                val removeButton = ActionButton(removeAction, removeAction.templatePresentation.clone(), "", JBUI.size(16))

                add(removeButton, BorderLayout.EAST)
            }
            fileListPanel.add(filePanel)
        }
        fileListPanel.revalidate()
        fileListPanel.repaint()
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
    fun addMessage(
        message: String,
        isMe: Boolean = false,
        displayPrompt: String = "",
        render: Boolean? = null,
        chatMessage: ChatContextMessage?
    ): MessageView {
        val role = if (isMe) ChatRole.user else ChatRole.assistant
        val displayText = displayPrompt.ifEmpty { message }

        val messageView = MessageView(message, role, displayText, project, render ?: (role == ChatRole.user), chatMessage, this)

        myList.add(messageView)
        updateLayout()
        scrollToBottom()
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
//        myList.remove(myList.componentCount - 1)
//        val text = updateMessageInUi(content, messageView)
//
//        progressBar.isIndeterminate = false
//        progressBar.isVisible = false
//        updateUI()
//
//        postAction(text)
    }

    fun setInput(trimMargin: String) {
        inputSection.text = trimMargin
        this.focusInput()
    }

    /**
     * Resets the chat session by clearing the current session and updating the UI.
     */
    fun newChatSession() {
        progressBar.isVisible = false
        myList.removeAll()
        chatCodingService.newSession()
        this.hiddenProgressBar()
        chatCodingService.saveSessions()
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

    // 设置编辑模式
    fun setEditingMessage(messageView: MessageView?) {
        editingMessageView = messageView
        if (messageView != null) {
            inputSection.setEditingMessage(messageView)
            updateEditingStatus("正在编辑消息...")
        } else {
            inputSection.setEditingMessage(null)
            updateEditingStatus(null)
        }

        focusInput()
    }

    // 更新编辑状态标签
    private fun updateEditingStatus(status: String?) {
        if (status != null) {
            editingLabel.text = status
            editingPanel.isVisible = true
        } else {
            editingLabel.text = ""
            editingPanel.isVisible = false
        }
    }

    /**
     * 从指定的 MessageView 之后，移除所有的 MessageView
     */
    fun removeMessageViewsAfter(messageView: MessageView) {
        val index = myList.components.indexOf(messageView)
        if (index >= 0 && index < myList.componentCount - 1) {
            // 从 index + 1 开始移除
            val removeCount = myList.componentCount - (index + 1)
            for (i in 0 until removeCount) {
                myList.remove(index + 1)
            }
            updateUI()
        }
    }
}

private class GenerateDiffAction(
    private val project: Project,
    private val progressBar: JProgressBar,
    private val inputSection: AutoDevInputSection,
    private val chatCodingService: ChatCodingService
) : AnAction("Generate diff based on this chat", "Generate diff based on this chat", AllIcons.Actions.ToggleVisibility) {

    override fun actionPerformed(e: AnActionEvent) {
        val projectStructure = DirectoryUtil.getDirectoryContents(project)
        val chatHistory = chatCodingService.exportChatHistory(true)
        val contentPanel = LLMChatToolWindowFactory.getPanel(project)

        ApplicationManager.getApplication().executeOnPooledThread {
            progressBar.isVisible = true
            progressBar.isIndeterminate = true
            GenerateDiffAgent.apply(
                project,
                LLMSettingsState.toLlmConfig(inputSection.getSelectedSetting()),
                projectStructure,
                chatHistory,
                chatCodingService.getCurrentSession(),
                contentPanel!!
            )
            progressBar.isIndeterminate = false
            progressBar.isVisible = false
        }
    }
}

