import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.toolWindow.chat.block.*
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.github.zjh7890.gpttools.toolWindow.chat.fullHeight
import com.github.zjh7890.gpttools.toolWindow.chat.fullWidth
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.panel
import org.apache.commons.text.similarity.LevenshteinDistance
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.IOException
import javax.swing.*

class CodeChangeBlockView(private val codeChangeBlock: CodeChange,
                          private val project: Project
) : MessageBlockView {
    private val changesList = JList<CodeChangeFile>()
    private val panel = SimpleToolWindowPanel(true, true)
    private lateinit var changeData : CodeChangeFile

    override fun getBlock(): MessageBlock {
        return codeChangeBlock
    }

    override fun getComponent(): Component? {
        return panel
    }

    override fun initialize() {
        changeData = parseCodeChanges(codeChangeBlock.getTextContent())

        val originalFile = LocalFileSystem.getInstance().findFileByPath(project.basePath + "/" + changeData.path)
        if (originalFile != null) {
            changeData.changeType = "UPDATE"
        } else {
            changeData.changeType = "CREATE"
        }
//        changesList.setListData(changes.toTypedArray())

//        changesList.cellRenderer = object : ListCellRenderer<CodeChangeFile> {
//            override fun getListCellRendererComponent(
//                list: JList<out CodeChangeFile>?,
//                value: CodeChangeFile?,
//                index: Int,
//                isSelected: Boolean,
//                cellHasFocus: Boolean
//            ): Component {
//                val panel = JPanel().apply {
//                    layout = BoxLayout(this, BoxLayout.X_AXIS)
//                    val mergeStatus = if (value?.isMerged == true) "✅" else "❗"
//                    val statusLabel = JLabel(mergeStatus)
//                    add(statusLabel)
//
//                    val filenameColor = when (value?.changeType) {
//                        "CREATE" -> Color.decode("#067D17")
//                        "UPDATE" -> Color.decode("#0033B3")
//                        "DELETE" -> Color.decode("#6C707E")
//                        else -> list?.foreground
//                    }
//
//                    val label = JLabel("${value?.filename} (${value?.dirPath})").apply {
//                        foreground = filenameColor
//                    }
//                    add(label)
//                }
//                panel.isOpaque = true
//                if (isSelected) {
//                    panel.background = list?.selectionBackground
//                    panel.foreground = list?.selectionForeground
//                } else {
//                    panel.background = list?.background
//                    panel.foreground = list?.foreground
//                }
//                return panel
//            }
//        }

        // 创建主面板
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // 文件名，根据 changeType 设置颜色
        val filenameLabel = JTextField(changeData.filename).apply {
            isEditable = false  // 设置为不可编辑
            border = null       // 去掉边框，使其看起来像标签
            foreground = when (changeData.changeType) {
                "CREATE" -> Color.decode("#067D17")
                "UPDATE" -> Color.decode("#0033B3")
                "DELETE" -> Color.decode("#6C707E")
                else -> Color.BLACK
            }
            setOpaque(false)    // 背景透明
        }

        filenameLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                super.mouseClicked(e)
                // 尝试找到并打开文件
                val fileToOpen = LocalFileSystem.getInstance().findFileByPath(project.basePath + "/" + changeData.path)
                if (fileToOpen != null && fileToOpen.exists()) {
                    // 文件存在，打开文件
                    FileEditorManager.getInstance(project).openFile(fileToOpen, true)
                } else {
                    // 文件不存在，显示提示
                    JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder("File not found: ${changeData.filename}", MessageType.INFO, null)
                        .setFadeoutTime(3000)
                        .createBalloon()
                        .show(
                            RelativePoint.getSouthEastOf(WindowManager.getInstance().getStatusBar(project).component!!),
                            Balloon.Position.atRight)
                }
            }
        })

        // 路径，使用 HTML 以支持自动换行
        val pathLabel = JTextArea(changeData.dirPath).apply {
            isEditable = false  // 设置为不可编辑
            border = null       // 去掉边框，使其看起来像标签
            lineWrap = true
            foreground = Color.GRAY
            setOpaque(false)    // 背景透明
        }

        setupToolbar()

        val blockView = CodeBlockView(
            CodeBlock(SimpleMessage(changeData.fileContent, changeData.fileContent, ChatRole.User)),
            project
        ) {}


        panel.add(filenameLabel)
        panel.add(pathLabel)
        panel.add(blockView.getComponent())
        // 展示
    }

    private fun parseCodeChanges(textContent: String): CodeChangeFile {
        val changes = mutableListOf<CodeChangeFile>()

        // Regex to extract individual file change blocks
        val fileChangeRegex = """repo-relative-path-for-gpt-tools: (.*?)\ntype:(\s)*(CREATE|UPDATE|DELETE)(\s)*\n-----\n(.*?)\n-----""".toRegex(
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        // Iterate over each match to extract details
        fileChangeRegex.findAll(textContent).forEach { match ->
            val filePath = match.groups[1]?.value ?: "Unknown path"
            val changeType = match.groups[3]?.value ?: "Unknown"
            val fileContent = match.groups[5]?.value?.trim() ?: ""

            // Extract directory path and file name
            val pathParts = filePath.split("/").let {
                it.last() to it.dropLast(1).joinToString("/")
            }

            // Create a new CodeChangeFile object and add it to the list
            changes.add(CodeChangeFile(
                path = filePath,
                dirPath = pathParts.second,
                filename = pathParts.first,
                fileContent = fileContent,
                isMerged = false,
                changeType = changeType
            ))
        }

        return changes.get(0)
    }

    private fun setupToolbar() {
        val actionGroup = DefaultActionGroup().apply {
            add(ShowChangeViewAction(project, changeData))
        }

        val toolbar: ActionToolbar = ActionManager.getInstance().createActionToolbar("CodeChangesToolbar", actionGroup, true)
        // 设置目标组件，通常是包含工具栏的主面板或者任何其他适当的组件
        toolbar.targetComponent = panel
        panel.setToolbar(toolbar.component)
    }
}

class ShowChangeViewAction(private val project: Project, private val changeData: CodeChangeFile) : AnAction("Show Diffs", "Show the differences", null) {
    override fun actionPerformed(e: AnActionEvent) {
        val selectedData = changeData
        showDiffFor(selectedData, project)
    }

    private fun showDiffFor(data: CodeChangeFile, project: Project) {
        val content1: DocumentContent
        val content2: DocumentContent
        val diffContentFactory = DiffContentFactory.getInstance()
        val originalFile: VirtualFile?
        if (data.changeType == "CREATE") {
            content1 = diffContentFactory.create("", PlainTextFileType.INSTANCE)

            val createContent = EditorFactory.getInstance().createDocument(data.fileContent)
            content2 = diffContentFactory.create(project, createContent)
            originalFile = null
        } else if (data.changeType == "UPDATE") {
            try {
                originalFile = LocalFileSystem.getInstance().findFileByPath(project.basePath + "/" + data.path)
                val document: Document
                if (originalFile != null) {
                    document = FileDocumentManager.getInstance().getDocument(originalFile)!!
                } else {
                    // 日志告警
                    Messages.showMessageDialog(
                        project,
                        "Original file not found: ${data.path}",
                        "Error",
                        Messages.getErrorIcon()
                    )
                    return;
//                    document = ""
                }

                val updated = data.fileContent
                val updatedDocument = EditorFactory.getInstance().createDocument(updated)

//                val documentManager = FileDocumentManager.getInstance().get

                content1 = diffContentFactory.create(project, document)
                content2 = diffContentFactory.create(project, updatedDocument)
            } catch (e: Exception) {
                TODO("Not yet implemented")
            }
        } else if (data.changeType == "DELETE") {
            originalFile = LocalFileSystem.getInstance().findFileByPath(project.basePath + "/" + data.path)
            if (originalFile == null) {
                Messages.showMessageDialog(
                    project,
                    "Original file not found: ${data.path}",
                    "Error",
                    Messages.getErrorIcon()
                )
            }
            content1 = diffContentFactory.create("", PlainTextFileType.INSTANCE)
            content2 = diffContentFactory.create("", PlainTextFileType.INSTANCE)
        } else {
            throw RuntimeException("unknown type")
        }
        val request = SimpleDiffRequest("Diff - ${data.filename}", content1, content2, "Original", "Updated")

        // 获取 DiffManager 实例并创建请求面板
        val diffPanel = DiffManager.getInstance().createRequestPanel(this.project, {}, null)
        diffPanel.setRequest(request)
        // 使用 DialogBuilder 来展示面板
        val dialogBuilder = DialogBuilder(this.project).apply {
            setTitle("Diff: ${data.filename}")
            setCenterPanel(diffPanel.component)
            addOkAction()
            setOkOperation {
                WriteCommandAction.runWriteCommandAction(project) {
                    when (data.changeType) {
                        "CREATE" -> {
                            createFileWithParents(project.basePath!!, data.path, content2.document.text)
                        }
                        "UPDATE" -> originalFile?.let {
//                            FileDocumentManager.getInstance().getDocument(originalFile)?.setText(content2.document.text)
                        }
                        "DELETE" -> originalFile?.delete(this)
                    }
                    dialogWrapper.close(0)
                }
                dialogWrapper.close(0)
                data.isMerged = true
            }
        }
        dialogBuilder.show()
    }

    fun createFileWithParents(projectBasePath: String, relativePath: String, fileContent: String) {
        val fullPath = "$projectBasePath/$relativePath"
        val parentPath = fullPath.substringBeforeLast('/')

        ApplicationManager.getApplication().runWriteAction {
            try {
                var parentDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(parentPath)
                if (parentDir == null) {
                    parentDir = createParentDirectories(parentPath)
                }
                parentDir?.let {
                    val newFile = it.createChildData(null, fullPath.substringAfterLast('/'))
                    newFile.setBinaryContent(fileContent.toByteArray())
                    VirtualFileManager.getInstance().syncRefresh()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class)
    private fun createParentDirectories(path: String): VirtualFile? {
        val parts = path.split("/")
        var currentPath = ""
        var currentDir: VirtualFile? = null
        for (part in parts) {
            currentPath += "/$part"
            val dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(currentPath)
            currentDir = if (dir == null) {
                currentDir?.createChildDirectory(null, part) ?: LocalFileSystem.getInstance().createChildDirectory(null, LocalFileSystem.getInstance().refreshAndFindFileByPath("/")!!, part)
            } else {
                dir
            }
        }
        return currentDir
    }
}

data class CodeChangeFile(
    val path: String,  // 文件路径
    val dirPath: String,
    val filename: String,
    val fileContent: String,
    var isMerged: Boolean,
    var changeType: String  // "CREATE", "UPDATE", "DELETE"
)
