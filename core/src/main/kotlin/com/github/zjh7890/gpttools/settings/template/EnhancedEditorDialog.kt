package com.github.zjh7890.gpttools.settings.template

import com.github.zjh7890.gpttools.services.Action
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Splitter
import java.awt.BorderLayout
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JPanel

class EnhancedEditorDialog(private var template: PromptTemplate?) : DialogWrapper(null) {
    private var editor: Editor? = null

    init {
        title = "Edit Template"
        preferredSize.setSize(800, 600)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val splitter = Splitter(true, 0.8f)

        editor = EditorFactory.getInstance().createEditor(
            EditorFactory.getInstance().createDocument(template?.value ?: ""), null, PlainTextFileType.INSTANCE, false
        )

        splitter.firstComponent = editor?.component

        val controlPanel = JPanel() // You can add control buttons if needed.

        splitter.secondComponent = controlPanel
        panel.add(splitter, BorderLayout.CENTER)
        return panel
    }

    /**
     * 屏蔽 esc 退出逻辑，防止用户误退出导致模板没保存
     */
    @Override
    override fun createCancelAction() : ActionListener? {
        return null
    }

    override fun doOKAction() {
        super.doOKAction()
        template?.value = editor?.document?.text ?: ""
    }

    override fun dispose() {
        super.dispose()
        editor?.let { EditorFactory.getInstance().releaseEditor(it) }
    }
}