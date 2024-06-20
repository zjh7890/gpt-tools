package com.github.zjh7890.gpttools.toolWindow.chat

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
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.serialization.json.Json
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.decodeFromString

/**
 *
 */
class AutoDevInputSection(private val project: Project, val disposable: Disposable?) : BorderLayoutPanel() {
    private val input: AutoDevInput
    private val documentListener: DocumentListener
    private val sendButtonPresentation: Presentation
    private val stopButtonPresentation: Presentation
    private val sendButton: ActionButton
    private val stopButton: ActionButton
    private val buttonPanel = JPanel(CardLayout())


    private val logger = logger<AutoDevInputSection>()

    val editorListeners = EventDispatcher.create(AutoDevInputListener::class.java)
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
        stopButtonPresentation.setIcon(AllIcons.General.User)
        this.stopButtonPresentation = stopButtonPresentation

        sendButton = ActionButton(
            DumbAwareAction.create {
                object : DumbAwareAction("") {
                    override fun actionPerformed(e: AnActionEvent) {
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

        input = AutoDevInput(project, listOf(), disposable, this)

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


        buttonPanel.add(sendButton, "Send")
        buttonPanel.add(stopButton, "Stop")

        layoutPanel.addToCenter(horizontalGlue)
        layoutPanel.addToRight(buttonPanel)
        addToBottom(layoutPanel)

        addListener(object : AutoDevInputListener {
            override fun editorAdded(editor: EditorEx) {
                this@AutoDevInputSection.initEditor()
            }
        })
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

    private val maxHeight: Int
        get() {
            val decorator = UIUtil.getParentOfType(InternalDecorator::class.java, this)
            val contentManager = decorator?.contentManager ?: return JBUI.scale(200)
            return contentManager.component.height / 2
        }

    val focusableComponent: JComponent get() = input
}
