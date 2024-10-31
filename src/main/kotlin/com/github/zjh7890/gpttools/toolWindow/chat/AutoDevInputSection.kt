package com.github.zjh7890.gpttools.toolWindow.chat

import com.github.zjh7890.gpttools.settings.llmSetting.LLMSetting
import com.github.zjh7890.gpttools.settings.llmSetting.LLMSettingsState
import com.github.zjh7890.gpttools.toolWindow.chat.block.AutoDevCoolBorder
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlin.math.max
import kotlin.math.min
import javax.swing.*

/**
 *
 */
class AutoDevInputSection(private val project: Project, val disposable: Disposable?) : BorderLayoutPanel(), LLMSettingsState.SettingsChangeListener {
    private val input: AutoDevInput
    private val documentListener: DocumentListener
    private val sendButtonPresentation: Presentation
    private val stopButtonPresentation: Presentation
    private val sendButton: ActionButton
    private val stopButton: ActionButton
    private val buttonPanel = JPanel(CardLayout())


    private val logger = logger<AutoDevInputSection>()

    val editorListeners = EventDispatcher.create(AutoDevInputListener::class.java)
    var messageView : MessageView? = null

    private val configComboBox: ComboBox<LLMSetting>

    var text: String
        get() {
            return input.text
        }
        set(text) {
            input.recreateDocument()
            input.text = text
        }

    init {
        val sendButtonPresentation = Presentation("send")
        sendButtonPresentation.setIcon(AllIcons.General.User)
        this.sendButtonPresentation = sendButtonPresentation

        val stopButtonPresentation = Presentation("Stop")
        stopButtonPresentation.setIcon(AllIcons.Run.Stop)
        this.stopButtonPresentation = stopButtonPresentation
        input = AutoDevInput(project, listOf(), disposable, this)

        sendButton = ActionButton(
            DumbAwareAction.create {
                object : DumbAwareAction("") {
                    override fun actionPerformed(e: AnActionEvent) {
                        if (input.text.isBlank()) {
                            return
                        }
                        showStopButton()
                        editorListeners.multicaster.onSubmit(this@AutoDevInputSection, AutoDevInputTrigger.Button)
                    }
                }.actionPerformed(it)
            },
            this.sendButtonPresentation,
            "",
            Dimension(20, 20)
        )

        stopButton = ActionButton(
            DumbAwareAction.create {
                object : DumbAwareAction("") {
                    override fun actionPerformed(e: AnActionEvent) {
                        editorListeners.multicaster.onStop(this@AutoDevInputSection)
                    }
                }.actionPerformed(it)
            },
            this.stopButtonPresentation,
            "",
            Dimension(20, 20)
        )


        documentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val i = input.preferredSize?.height
                if (i != input.height) {
                    revalidate()
                }
            }
        }

        input.addDocumentListener(documentListener)
        input.recreateDocument()

        input.border = JBEmptyBorder(4)

        addToCenter(input)
        val layoutPanel = BorderLayoutPanel()
        val horizontalGlue = Box.createHorizontalGlue()
        horizontalGlue.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                IdeFocusManager.getInstance(project).requestFocus(input, true)
                input.caretModel.moveToOffset(input.text.length - 1)
            }
        })
        layoutPanel.setOpaque(false)

        // 获取 ShireSettingsState 实例
        val LLMSettingsState = LLMSettingsState.getInstance()

        // 将自身添加为设置变化的监听器
        LLMSettingsState.addSettingsChangeListener(this)


        // 获取 ShireSettings 列表
        val settingsList = LLMSettingsState.settings

// 查找默认的配置项（isDefault 为 true）
        val defaultSetting = settingsList.find { it.isDefault } ?: settingsList.firstOrNull()

// 创建 ComboBox 的模型
        val comboBoxModel = MutableCollectionComboBoxModel(settingsList, defaultSetting)

// 初始化 ComboBox
        configComboBox = ComboBox(comboBoxModel).apply {
            renderer = object : SimpleListCellRenderer<LLMSetting>() {
                override fun customize(
                    list: JList<out LLMSetting>,
                    value: LLMSetting?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean
                ) {
                    text = "${value?.modelName} - ${value?.provider?.name}" ?: "Unknown"
                }
            }
        }


        buttonPanel.add(sendButton, "Send")
        buttonPanel.add(stopButton, "Stop")

        layoutPanel.addToLeft(configComboBox)
        layoutPanel.addToCenter(horizontalGlue)
        layoutPanel.addToRight(buttonPanel)
        addToBottom(layoutPanel)

        addListener(object : AutoDevInputListener {
            override fun editorAdded(editor: EditorEx) {
                this@AutoDevInputSection.initEditor()
            }
        })
    }

    fun getSelectedSetting(): LLMSetting? {
        return configComboBox.selectedItem as? LLMSetting
    }

    fun showStopButton() {
        (buttonPanel.layout as? CardLayout)?.show(buttonPanel, "Stop")
        stopButton.isEnabled = true
    }

    fun showSendButton() {
        (buttonPanel.layout as? CardLayout)?.show(buttonPanel, "Send")
        buttonPanel.isEnabled = true
    }


    fun initEditor() {
        val editorEx = this.input.editor as? EditorEx ?: return

        setBorder(AutoDevCoolBorder(editorEx, this))
        UIUtil.setOpaqueRecursively(this, false)
        this.revalidate()
    }

    override fun getPreferredSize(): Dimension {
        val result = super.getPreferredSize()
        result.height = max(min(result.height, maxHeight), minimumSize.height)
        return result
    }

    fun setContent(trimMargin: String) {
        val focusManager = IdeFocusManager.getInstance(project)
        focusManager.requestFocus(input, true)
        this.input.recreateDocument()
        this.input.text = trimMargin
    }

    override fun getBackground(): Color? {
        // it seems that the input field is not ready when this method is called
        if (this.input == null) return super.getBackground()

        val editor = input.editor ?: return super.getBackground()
        return editor.colorsScheme.defaultBackground
    }

    override fun setBackground(bg: Color?) {}

    fun addListener(listener: AutoDevInputListener) {
        editorListeners.addListener(listener)
    }

    fun moveCursorToStart() {
        input.caretModel.moveToOffset(0)
    }

    fun setEditingMessage(messageView: MessageView?) {
        text = messageView?.chatMessage?.content ?: ""
        this.messageView = messageView
    }

    private val maxHeight: Int
        get() {
            val decorator = UIUtil.getParentOfType(InternalDecorator::class.java, this)
            val contentManager = decorator?.contentManager ?: return JBUI.scale(200)
            return contentManager.component.height / 2
        }

    val focusableComponent: JComponent get() = input

    override fun onSettingsChanged() {
        SwingUtilities.invokeLater {
            val settingsList = LLMSettingsState.getInstance().settings

            // 获取当前选中的模型
            val currentSelection = configComboBox.selectedItem as? LLMSetting

            // 检查当前选中的模型是否仍然存在于新的设置列表中
            val newSelection = if (currentSelection != null && settingsList.contains(currentSelection)) {
                currentSelection // 保持当前选中项
            } else {
                // 如果当前选中项被删除，选择默认项
                settingsList.find { it.isDefault } ?: settingsList.firstOrNull()
            }

            // 创建新的 ComboBoxModel，并设置选定项
            val comboBoxModel = MutableCollectionComboBoxModel(settingsList, newSelection)
            configComboBox.model = comboBoxModel
        }
    }
}
