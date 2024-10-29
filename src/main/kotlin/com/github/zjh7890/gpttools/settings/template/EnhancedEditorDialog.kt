package com.github.zjh7890.gpttools.settings.template

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Splitter
import javax.swing.*
import java.awt.BorderLayout

class EnhancedEditorDialog(val project: Project, private var template: PromptTemplate?) : DialogWrapper(project) {
    private var editor: Editor? = null

    init {
        title = "Edit Template"
        preferredSize.setSize(800, 600)
        init()
    }

    override fun createCenterPanel(): JComponent? {
        val panel = JPanel(BorderLayout())
        val splitter = Splitter(true, 0.8f)

        editor = EditorFactory.getInstance().createEditor(
            EditorFactory.getInstance().createDocument(template?.value ?: ""), project, PlainTextFileType.INSTANCE, false
        )

        splitter.firstComponent = editor?.component

        val controlPanel = JPanel() // You can add control buttons if needed.

        splitter.secondComponent = controlPanel
        panel.add(splitter, BorderLayout.CENTER)
        return panel
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