package com.github.zjh7890.gpttools.toolWindow.chat

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.actions.EnterAction
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.util.EventDispatcher
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.event.KeyEvent
import java.util.*
import javax.swing.KeyStroke


enum class AutoDevInputTrigger {
    Button,
    Key
}

interface AutoDevInputListener : EventListener {
    fun editorAdded(editor: EditorEx) {}
    fun onSubmit(component: AutoDevInputSection, trigger: AutoDevInputTrigger) {}
    fun onStop(component: AutoDevInputSection) {}
}

class AutoDevInput(
    project: Project,
    private val listeners: List<DocumentListener>,
    val disposable: Disposable?,
    val inputSection: AutoDevInputSection,
) : EditorTextField(project, FileTypes.PLAIN_TEXT), Disposable {
    private var editorListeners: EventDispatcher<AutoDevInputListener> = inputSection.editorListeners

    init {
        isOneLineMode = false
        updatePlaceholderText()
        setFontInheritedFromLAF(true)
        addSettingsProvider {
            it.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
            it.colorsScheme.lineSpacing = 1.2f
            it.settings.isUseSoftWraps = true
            it.isEmbeddedIntoDialogWrapper = true
            it.contentComponent.setOpaque(false)
        }

        DumbAwareAction.create {
            object : AnAction() {
                override fun actionPerformed(actionEvent: AnActionEvent) {
                    val editor = editor ?: return

                    // Insert a new line
                    CommandProcessor.getInstance().executeCommand(project, {
                        val eol = "\n"
                        val caretOffset = editor.caretModel.offset
                        editor.document.insertString(caretOffset, eol)
                        editor.caretModel.moveToOffset(caretOffset + eol.length)
                        EditorModificationUtil.scrollToCaret(editor)
                    }, null, null)
                }
            }
        }.registerCustomShortcutSet(
            CustomShortcutSet(
                KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), null),
                KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.META_DOWN_MASK), null)
            ), this
        )

        val connect: MessageBusConnection = project.messageBus.connect(disposable ?: this)
        val topic = AnActionListener.TOPIC
        connect.subscribe(topic, object : AnActionListener {
            override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
                if (event.dataContext.getData(CommonDataKeys.EDITOR) === editor && action is EnterAction) {
                    editorListeners.multicaster.onSubmit(inputSection, AutoDevInputTrigger.Key)
                }
            }
        })

        listeners.forEach { listener ->
            document.addDocumentListener(listener)
        }
    }

    override fun onEditorAdded(editor: Editor) {
        editorListeners.multicaster.editorAdded((editor as EditorEx))
    }

    private fun updatePlaceholderText() {
        setPlaceholder("Enter 发送， Shift + Enter 开启新行")
        repaint()
    }

    public override fun createEditor(): EditorEx {
        val editor = super.createEditor()
        editor.setVerticalScrollbarVisible(true)
        setBorder(JBUI.Borders.empty())
        editor.setShowPlaceholderWhenFocused(true)
        editor.caretModel.moveToOffset(0)
        editor.scrollPane.setBorder(border)
        editor.contentComponent.setOpaque(false)
        return editor
    }

    override fun getBackground(): Color {
        val editor = editor ?: return super.getBackground()
        return editor.colorsScheme.defaultBackground
    }

    override fun getData(dataId: String): Any? {
        if (!PlatformCoreDataKeys.FILE_EDITOR.`is`(dataId)) {
            return super.getData(dataId)
        }

        val currentEditor = editor ?: return super.getData(dataId)
        return TextEditorProvider.getInstance().getTextEditor(currentEditor)
    }

    override fun dispose() {
        listeners.forEach {
            editor?.document?.removeDocumentListener(it)
        }
    }

    fun recreateDocument() {
        inputSection.initEditor()
    }

    private fun initializeDocumentListeners(inputDocument: Document) {
        listeners.forEach { listener ->
            inputDocument.addDocumentListener(listener)
        }
    }
}