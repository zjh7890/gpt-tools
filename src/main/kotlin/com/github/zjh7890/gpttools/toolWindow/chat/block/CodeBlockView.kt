// Copyright 2000-2021 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.github.zjh7890.gpttools.toolWindow.chat.block

import CodeChangeBlockView2
import com.github.zjh7890.gpttools.utils.Code
import com.github.zjh7890.gpttools.utils.ParseUtils.parseCodeChanges
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.messages.Topic
import com.intellij.util.ui.JBUI
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent

class CodeBlockView(
    private val block: CodeBlock,
    private val project: Project,
    private val disposable: Disposable,
) : MessageBlockView {

    private var editorInfo: CodePartEditorInfo? = null
    private var codeChangeBlockView: CodeChangeBlockView2? = null

    init {
        block.addTextListener {
            if (editorInfo == null && codeChangeBlockView == null) return@addTextListener
            updateOrCreateCodeView()
        }
    }

    override fun getBlock(): CodeBlock {
        return block
    }

    override fun getComponent(): JComponent {
        return codeChangeBlockView?.getComponent() as? JComponent
            ?: editorInfo?.component
            ?: updateOrCreateCodeView()
    }

    val codeContent: String
        get() {
            return editorInfo?.code?.get() ?: ""
        }

    override fun initialize() {
        if (editorInfo == null && codeChangeBlockView == null) {
            updateOrCreateCodeView()
        }
    }

    private fun updateOrCreateCodeView(): JComponent {
        val code: Code = block.code
        val language = code.languageId

        // 检查文件类型是否为 patch
        if (language.equals("diff", ignoreCase = true)) {
            // 使用 CodeChangeBlockView2
            if (codeChangeBlockView == null) {
                val changes = parseCodeChanges(project, code.text)
                codeChangeBlockView = CodeChangeBlockView2(project, mutableListOf(changes))
            }
            return codeChangeBlockView!!.getComponent() as JComponent
        } else {
            // 使用原始的代码展示逻辑
            if (editorInfo == null) {
                val graphProperty = PropertyGraph().property(code.text)
                editorInfo = createCodeViewer(project, graphProperty, disposable, code.language, getBlock().getMessage(), getBlock())
            }
            return editorInfo!!.component
        }
    }

    companion object {

        private fun createCodeViewerEditor(
            project: Project,
            file: LightVirtualFile,
            document: Document,
            disposable: Disposable,
        ): EditorEx {
            val editor: EditorEx = ReadAction.compute<EditorEx, Throwable> {
                EditorFactory.getInstance()
                    .createViewer(document, project, EditorKind.PREVIEW) as EditorEx
            }

            disposable.whenDisposed {
                EditorFactory.getInstance().releaseEditor(editor)
            }

            editor.setFile(file)
            editor.setCaretEnabled(true)
            val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file)

            editor.highlighter = highlighter

            val markupModel: MarkupModelEx = editor.markupModel
            (markupModel as EditorMarkupModel).isErrorStripeVisible = false

            val settings = editor.settings.also {
                it.isDndEnabled = false
                it.isLineNumbersShown = false
                it.additionalLinesCount = 0
                it.isLineMarkerAreaShown = false
                it.isFoldingOutlineShown = false
                it.isRightMarginShown = false
                it.isShowIntentionBulb = false
                it.isUseSoftWraps = true
                it.isRefrainFromScrolling = true
                it.isAdditionalPageAtBottom = false
                it.isCaretRowShown = false
            }

            editor.addFocusListener(object : FocusChangeListener {
                override fun focusGained(focusEditor: Editor) {
                    settings.isCaretRowShown = true
                }

                override fun focusLost(focusEditor: Editor) {
                    settings.isCaretRowShown = false
                    editor.markupModel.removeAllHighlighters()
                }
            })

            return editor
        }

        fun createCodeViewer(
            project: Project,
            graphProperty: GraphProperty<String>,
            disposable: Disposable,
            language: Language,
            message: CompletableMessage,
            block: CodeBlock,
        ): CodePartEditorInfo {
            val forceFoldEditorByDefault = false
            val file = LightVirtualFile(AUTODEV_SNIPPET_NAME, language, graphProperty.get())
            if (file.fileType == UnknownFileType.INSTANCE) {
                file.fileType = PlainTextFileType.INSTANCE
            }

            val document: Document =
                file.findDocument() ?: throw IllegalStateException("Document not found")

            val editor: EditorEx =
                createCodeViewerEditor(project, file, document, disposable)

            val toolbarActionGroup = DefaultActionGroup().apply {
                add(AutoDevRunDevInsAction(block))
            }
            toolbarActionGroup?.let {
                val toolbar: ActionToolbarImpl =
                    object : ActionToolbarImpl(ActionPlaces.MAIN_TOOLBAR, toolbarActionGroup, true) {
                        override fun updateUI() {
                            super.updateUI()
                            editor.component.border = JBUI.Borders.empty()
                        }
                    }

                toolbar.background = editor.backgroundColor
                toolbar.isOpaque = true
                toolbar.targetComponent = editor.contentComponent
                editor.headerComponent = toolbar

                val connect = project.messageBus.connect(disposable)
                val topic: Topic<EditorColorsListener> = EditorColorsManager.TOPIC
                connect.subscribe(topic, EditorColorsListener {
                    toolbar.background = editor.backgroundColor
                })
            }

            editor.scrollPane.border = JBUI.Borders.empty()
            editor.component.border = JBUI.Borders.empty()

            val editorFragment = EditorFragment(project, editor, message)
            editorFragment.setCollapsed(forceFoldEditorByDefault)
            editorFragment.updateExpandCollapseLabel()

            return CodePartEditorInfo(graphProperty, editorFragment.getContent(), editor, file)
        }
    }
}

@RequiresReadLock
fun VirtualFile.findDocument(): Document? {
    return ReadAction.compute<Document, Throwable> {
        FileDocumentManager.getInstance().getDocument(this)
    }
}

fun Disposable.whenDisposed(listener: () -> Unit) {
    Disposer.register(this) { listener() }
}

fun Disposable.whenDisposed(
    parentDisposable: Disposable,
    listener: () -> Unit
) {
    val isDisposed = AtomicBoolean(false)

    val disposable = Disposable {
        if (isDisposed.compareAndSet(false, true)) {
            listener()
        }
    }

    Disposer.register(this, disposable)

    Disposer.register(parentDisposable, Disposable {
        if (isDisposed.compareAndSet(false, true)) {
            Disposer.dispose(disposable)
        }
    })
}