package com.github.zjh7890.gpttools.toolWindow.llmChat

import com.github.zjh7890.gpttools.agent.GenerateDiffAgent
import com.github.zjh7890.gpttools.components.welcome.WelcomePanel
import com.github.zjh7890.gpttools.services.*
import com.github.zjh7890.gpttools.services.SessionListener
import com.github.zjh7890.gpttools.settings.common.CommonSettings
import com.github.zjh7890.gpttools.settings.common.CommonSettingsListener
import com.github.zjh7890.gpttools.settings.llmSetting.LLMSettingsState
import com.github.zjh7890.gpttools.toolWindow.chat.*
import com.github.zjh7890.gpttools.toolWindow.context.ContextFileToolWindowFactory
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.flow.Flow
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent

class ChatToolPanel(val disposable: Disposable?, val project: Project) :
    SimpleToolWindowPanel(true, true),
    NullableComponent,
    Disposable {

    private val logger = logger<ChatToolPanel>()

    var progressBar: JProgressBar
    val myTitle = JBLabel("Conversation")
    val myList = JPanel(VerticalLayout(JBUI.scale(10)))
    var inputSection: AutoDevInputSection
    private val addFileButton = ActionButton(
        object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                showFileSelectionPopup()
            }
        },
        Presentation().apply {
            icon = AllIcons.General.Add  // 使用 Plus 图标
            text = "Add File (待优化）"
            description = "Add file to session file list"
        },
        "",
        JBUI.size(16)
    )
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
    val sessionManager: SessionManager = project.getService(SessionManager::class.java)

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

                if (trigger != AutoDevInputTrigger.CopyPrompt) {
                    inputSection.showStopButton()
                }

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
                    sessionManager.truncateMessagesAfter(editingMessageView!!.chatMessage!!)

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
                        trigger == AutoDevInputTrigger.ChatThenDiff,
                        editingMessage,
                        llmConfig,
                        trigger
                    )
                } else {
                    chatCodingService.handlePromptAndResponse(
                        this@ChatToolPanel,
                        prompt,
                        trigger == AutoDevInputTrigger.ChatThenDiff,
                        null,
                        llmConfig,
                        trigger
                    )
                }
            }
        })

        // 添加监听器
        withFilesCheckbox.addActionListener {
            CommonSettings.getInstance().withFiles = withFilesCheckbox.isSelected
            sessionManager.getCurrentSession().withFiles = withFilesCheckbox.isSelected
            sessionManager.saveSessions()
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
                cell(addFileButton)
                cell(withFilesCheckbox)
                cell(withDirCheckbox)
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
                // 添加发送并复制按钮
                cell(ActionButton(
                    object : AnAction() {
                        override fun actionPerformed(e: AnActionEvent) {
                            if (inputSection.text.isBlank()) {
                                return
                            }
                            inputSection.editorListeners.multicaster.onSubmit(inputSection, AutoDevInputTrigger.CopyPrompt)
                        }
                    },
                    Presentation().apply {
                        icon = AllIcons.Actions.Redo
                        text = "Send and copy prompt"
                        description = "Send and copy prompt to clipboard"
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
        }

        setContent(panelContent)

        // 添加会话监听器以在会话列表更改时更新 UI（根据需要实现）
        sessionManager.addSessionListener(object : SessionListener {
            override fun sessionListChanged() {
                // 根据需要实现，例如刷新会话列表面板
            }
        })
    }

    /**
     * 重新加载当前对话
     */
    fun reloadConversation() {
        myList.removeAll()
        myList.add(WelcomePanel())
        val currentSession = sessionManager.getCurrentSession()
        currentSession.messages.forEach { message ->
            addMessage(message.content, message.role == ChatRole.user, render = true, chatMessage = message)
        }
        refreshFileList()  // 新增
        updateUI()
    }

    /**
     * 刷新文件列表
     */
    fun refreshFileList() {
        val session = sessionManager.getCurrentSession()
        // 获取并刷新 panel 的 file list
        val fileTreePanel = ContextFileToolWindowFactory.getPanel(project)
        fileTreePanel?.updateFileTree(session)
    }

    /**
     * 显示文件选择弹出窗口
     */
    private fun showFileSelectionPopup() {
        val textField = JTextField()
        val listModel = DefaultListModel<VirtualFile>()
        val fileList = JBList(listModel)
        fileList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        // 获取项目中的所有文件
        val allFiles = ArrayList<VirtualFile>()
        VfsUtilCore.iterateChildrenRecursively(
            project.baseDir,
            { true },
            { fileOrDir ->
                if (!fileOrDir.isDirectory) {
                    allFiles.add(fileOrDir)
                }
                true
            }
        )

        // 设置筛选功能
        textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val filterText = textField.text
                listModel.clear()
                val filteredFiles = allFiles.filter { it.name.contains(filterText, true) }
                filteredFiles.forEach { listModel.addElement(it) }
            }
        })

        // 初始化文件列表
        allFiles.forEach { listModel.addElement(it) }

        val panel = JPanel(BorderLayout())
        panel.add(textField, BorderLayout.NORTH)
        panel.add(JBScrollPane(fileList), BorderLayout.CENTER)

        val dialog = DialogBuilder(project)
            .apply {
                setTitle("Add File to Session")
                setCenterPanel(panel)
                addCancelAction() // 添加这一行来支持 ESC 关闭窗口
                setOkOperation {
                    val selectedFile = fileList.selectedValue
                    if (selectedFile != null) {
                        sessionManager.addFileToCurrentSession(selectedFile)
                        refreshFileList()
                    }
                }
            }

        // 监听双击和 Enter 键事件
        val addSelectedFile: () -> Unit = {
            val selectedFile = fileList.selectedValue
            if (selectedFile != null) {
                sessionManager.addFileToCurrentSession(selectedFile)
                refreshFileList()
            }
        }

        fileList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    addSelectedFile()
                }
            }
        })

        fileList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    addSelectedFile()
                }
            }
        })

        dialog.show()
    }

    /**
     * 聚焦到输入区域
     */
    fun focusInput() {
        val focusManager = IdeFocusManager.getInstance(chatCodingService.project)
        focusManager.doWhenFocusSettlesDown {
            focusManager.requestFocus(this.inputSection.focusableComponent, true)
        }
    }

    /**
     * 在聊天面板中添加一条消息，并更新 UI
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

    /**
     * 更新布局
     */
    private fun updateLayout() {
        val layout = myList.layout
        for (i in 0 until myList.componentCount) {
            layout.removeLayoutComponent(myList.getComponent(i))
            layout.addLayoutComponent(null, myList.getComponent(i))
        }
    }

    /**
     * 滚动到底部
     */
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
     * 使用提供的 Flow 更新 UI 中可替换的内容
     *
     * @param content The flow of strings to update the UI with.
     * @param postAction A function that is called when the "Replace Selection" button is clicked,
     *                   passing the current text to be replaced in the editor.
     */
    suspend fun updateReplaceableContent(content: Flow<String>, postAction: (text: String) -> Unit) {
        // 实现内容更新逻辑（根据需求）
    }

    /**
     * 设置输入区域的文本内容，并聚焦到输入区域
     */
    fun setInput(trimMargin: String) {
        inputSection.text = trimMargin
        this.focusInput()
    }

    /**
     * 重置聊天会话，清空当前会话并创建一个新的会话
     */
    fun newChatSession() {
        sessionManager.createNewSession()
        hiddenProgressBar()
        sessionManager.saveSessions()
        reloadConversation()
    }

    /**
     * 隐藏进度条
     */
    fun hiddenProgressBar() {
        progressBar.isVisible = false
    }

    /**
     * 移除聊天列表中的最后一条消息
     */
    fun removeLastMessage() {
        if (myList.componentCount > 0) {
            myList.remove(myList.componentCount - 1)
        }

        updateUI()
    }

    /**
     * 在聊天面板中添加一个 Web 视图（根据需求实现）
     */
    fun appendWebView(content: String, project: Project) {
        // 实现 WebView 添加逻辑
        updateUI()
    }

    /**
     * 将光标移动到输入区域的起始位置
     */
    fun moveCursorToStart() {
        inputSection.moveCursorToStart()
    }

    /**
     * 显示建议消息，用户点击后将建议内容填入输入区域
     */
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

    /**
     * 设置当前正在编辑的消息视图，并更新编辑状态
     */
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

    /**
     * 更新编辑状态标签的文本和可见性
     */
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
        myList.removeAll()
        chatCodingService.stop()
        if (disposable is Disposable) {
            Disposer.dispose(disposable)
        }
        editingMessageView = null
        withFilesCheckbox.removeActionListener { }
        generateDiffCheckbox.removeActionListener { }
        val fileTreePanel = ContextFileToolWindowFactory.getPanel(project)
        fileTreePanel?.removeAll()
    }

    /**
     * 同时在本地会话和 UI 中添加一条消息
     */
    fun addMessageBoth(role: ChatRole, message: String) {
        val chatMessage = sessionManager.appendLocalMessage(role, message)
        addMessage(message, role == ChatRole.user, render = true, chatMessage = chatMessage)
    }
}
