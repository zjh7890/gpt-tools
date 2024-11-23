package com.github.zjh7890.gpttools.toolWindow.llmChat

import com.github.zjh7890.gpttools.agent.GenerateDiffAgent
import com.github.zjh7890.gpttools.components.welcome.WelcomePanel
import com.github.zjh7890.gpttools.services.ChatCodingService
import com.github.zjh7890.gpttools.services.ChatContextMessage
import com.github.zjh7890.gpttools.settings.common.CommonSettings
import com.github.zjh7890.gpttools.settings.common.CommonSettingsListener
import com.github.zjh7890.gpttools.settings.llmSetting.LLMSettingsState
import com.github.zjh7890.gpttools.toolWindow.chat.*
import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.github.zjh7890.gpttools.utils.DirectoryUtil
import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.GptToolsIcon
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
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.Disposer
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
import kotlinx.coroutines.flow.Flow
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class ChatToolPanel(val disposable: Disposable?, val project: Project) :
    SimpleToolWindowPanel(true, true),
    NullableComponent,
    Disposable {
    private val logger = logger<ChatToolPanel>()

    var progressBar: JProgressBar
    val myTitle = JBLabel("Conversation")
    val myList = JPanel(VerticalLayout(JBUI.scale(10)))
    var inputSection: AutoDevInputSection
    val withFilesCheckbox = JCheckBox("WithFiles", CommonSettings.getInstance().withFiles)
    val withDirCheckbox = JCheckBox("WithDir", CommonSettings.getInstance().withDir).apply {
        toolTipText = "Send with project directory structure, without files"
    }
    val generateDiffCheckbox = JCheckBox("Generate Diff", CommonSettings.getInstance().generateDiff)
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

    private val toggleFileListButton = ActionButton(
        object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                fileListPanel.isVisible = !fileListPanel.isVisible
                // 更新 presentation 的图标
                e.presentation.icon = if (fileListPanel.isVisible) {
                    AllIcons.Actions.Collapseall
                } else {
                    AllIcons.Actions.Expandall
                }
                refreshFileList()
            }
        },
        Presentation().apply {
            icon = AllIcons.Actions.Collapseall
            text = "Toggle file list"
            description = "Toggle file list"
        },
        "",
        JBUI.size(16)
    )

    init {
        // 订阅设置变更
        ApplicationManager.getApplication().messageBus
            .connect(disposable ?: project)
            .subscribe(
                CommonSettingsListener.TOPIC,
                object : CommonSettingsListener {
                    override fun onSettingsChanged() {
                    generateDiffCheckbox.isSelected = CommonSettings.getInstance().generateDiff
                    withFilesCheckbox.isSelected = CommonSettings.getInstance().withFiles
                    withDirCheckbox.isSelected = CommonSettings.getInstance().withDir
                }
                }
            )

        // 注册到 project 的 Disposable 树中
        project.messageBus.connect(this)
        
        focusMouseListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                focusInput()
            }
        }

        myList.add(WelcomePanel())
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

                val withFiles = withFilesCheckbox.isSelected

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
        withFilesCheckbox.addActionListener {
            CommonSettings.getInstance().withFiles = withFilesCheckbox.isSelected
            chatCodingService.updateWithFiles(withFilesCheckbox.isSelected)
        }

        withDirCheckbox.addActionListener {
            CommonSettings.getInstance().withDir = withDirCheckbox.isSelected
        }

        generateDiffCheckbox.addActionListener {
            CommonSettings.getInstance().generateDiff = generateDiffCheckbox.isSelected
        }

        panelContent = panel {
            row { cell(myScrollPane).fullWidth().fullHeight() }.resizableRow()
            row { cell(progressBar).fullWidth() }
            row {
                border = JBUI.Borders.empty(8)
                cell(inputSection).fullWidth()
            }
            row { cell(editingPanel).fullWidth() }
            row {
                cell(withFilesCheckbox)
                cell(withDirCheckbox)
                cell(generateDiffCheckbox)
                cell(toggleFileListButton)
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
                // Add the "Copy prompt" button
                cell(ActionButton(
                    CopyPromptAction(project, chatCodingService),
                    Presentation().apply {
                        icon = GptToolsIcon.ToPromptIcon
                        text = "Copy prompt"
                        description = "Copy prompt"
                    },
                    "",
                    JBUI.size(16)
                ))
                cell(ActionButton(
                    ApplyCopyAction(project, progressBar, inputSection, chatCodingService),
                    Presentation().apply {
                        icon = GptToolsIcon.ApplyCopyIcon
                        text = "Apply copy content"
                        description = "Apply copy content" 
                    },
                    "",
                    JBUI.size(16)
                ))
            }
            row {
                cell(fileListPanel)
            }
        }

        setContent(panelContent)

    }

    // 重新加载当前对话
    fun reloadConversation() {
        myList.removeAll()
        myList.add(WelcomePanel())
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
        myList.add(WelcomePanel())
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

    fun showSuggestion(msg: String) {
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
    
    override fun dispose() {
        // 清除所有消息视图
        myList.removeAll()
        
        // 停止所有正在进行的操作
        chatCodingService.stop()
        
        // 取消所有订阅
        if (disposable is Disposable) {
            Disposer.dispose(disposable)
        }
        
        // 清除引用
        editingMessageView = null
        
        // 移除所有监听器
        withFilesCheckbox.removeActionListener { }
        generateDiffCheckbox.removeActionListener { }
        
        // 清除文件列表
        fileListPanel.removeAll()
    }

    fun addMessageBoth(role: ChatRole, message: String) {
        val chatMessage = chatCodingService.appendLocalMessage(role, message)
        addMessage(message, role == ChatRole.user, render = true, chatMessage = chatMessage)
    }
}

// Add the CopyPromptAction class
private class CopyPromptAction(
    private val project: Project,
    private val chatCodingService: ChatCodingService
) : AnAction("Copy prompt", "Copy prompt", GptToolsIcon.ToPromptIcon) {

    override fun actionPerformed(e: AnActionEvent) {
        val contentPanel = LLMChatToolWindowFactory.getPanel(project)
        val text = contentPanel?.inputSection?.text
        if (text.isNullOrBlank()) {
            return
        }
        // Update message view to inform the user
        ApplicationManager.getApplication().invokeAndWait() {
            contentPanel.addMessageBoth(ChatRole.user, text)
        }
        val chatHistory = chatCodingService.exportChatHistory(false)
        ClipboardUtils.copyToClipboard(chatHistory)
    }
}

private class ApplyCopyAction(
    private val project: Project,
    private val progressBar: JProgressBar,
    private val inputSection: AutoDevInputSection,
    private val chatCodingService: ChatCodingService
) : AnAction("Apply copy content", "Apply copy content", GptToolsIcon.ApplyCopyIcon) {

    override fun actionPerformed(e: AnActionEvent) {
        val contentPanel = LLMChatToolWindowFactory.getPanel(project)
        
        // 创建一个包含多行文本框的面板
        val textArea = JTextArea(10, 50) // 10行, 50列
        textArea.lineWrap = true // 自动换行
        textArea.wrapStyleWord = true // 按单词换行
        
        // 添加滚动条
        val scrollPane = JBScrollPane(textArea)
        
        // 创建对话框
        val dialog = DialogBuilder(project)
        dialog.setCenterPanel(scrollPane)
        dialog.setTitle("Apply Copy Content")
        dialog.addOkAction()
        dialog.addCancelAction()
        
        // 显示对话框
        val result = dialog.show()
        
        // 如果用户点击确定且输入不为空
        if (result == DialogWrapper.OK_EXIT_CODE) {
            val message = textArea.text
            if (message.isNotBlank()) {
                ApplicationManager.getApplication().executeOnPooledThread {
                    progressBar.isVisible = true
                    progressBar.isIndeterminate = true

                    ApplicationManager.getApplication().invokeAndWait {
                        // 添加用户消息
                        contentPanel?.addMessageBoth(ChatRole.assistant, FileUtil.wrapBorder(message))
                    }

                    // 调用 GenerateDiffAgent.apply
                    val projectStructure = DirectoryUtil.getDirectoryContents(project)
                    GenerateDiffAgent.apply(
                        project,
                        LLMSettingsState.toLlmConfig(inputSection.getSelectedSetting()),
                        projectStructure,
                        message,
                        chatCodingService.getCurrentSession(),
                        contentPanel!!
                    )
                    
                    progressBar.isIndeterminate = false
                    progressBar.isVisible = false
                }
            }
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

