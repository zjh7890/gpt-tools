import com.github.zjh7890.gpttools.toolWindow.chat.block.*
import com.github.zjh7890.gpttools.utils.Desc
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
import org.apache.commons.text.similarity.LevenshteinDistance
import java.awt.Component
import java.io.IOException
import javax.swing.*


class CodeChangeBlockView(private val codeChangeBlock: CodeChange,
                          private val project: Project
) : MessageBlockView {
    private val panel = SimpleToolWindowPanel(true, true)
    private lateinit var changesListView: ChangesListView

    init {
        initialize()
    }

    override fun getBlock(): MessageBlock {
        return codeChangeBlock
    }

    override fun getComponent(): Component? {
        return panel
    }

    override fun initialize() {
        val changes = parseCodeChanges(codeChangeBlock.getTextContent())
        changesListView = ChangesListView(changes, project)

        panel.setContent(changesListView)
        setupToolbar()
    }

    private fun parseCodeChanges(textContent: String): List<CodeChangeFile> {
        val changes = mutableListOf<CodeChangeFile>()
        // 外层匹配 CHANGES START/END
        val outerBlockRegex = """----- CHANGES START -----(.*?)----- CHANGES END -----""".toRegex(
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        // 内层匹配 CHANGE START/END
        val innerBlockRegex = """----- CHANGE START -----(.*?)----- CHANGE END -----""".toRegex(
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        val pathRegex = """path: (.+?)\n""".toRegex()
        val changeTypeRegex = """changeType: (.+?)\n""".toRegex()
        val changeRegex = """<<<< ORIGINAL\n(.*?)====\n(.*?)>>>> UPDATED""".toRegex(
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        outerBlockRegex.findAll(textContent).forEach { outerBlockMatch ->
            val outerBlockContent = outerBlockMatch.groups[1]?.value ?: ""
            innerBlockRegex.findAll(outerBlockContent).forEach { blockMatch ->
                val blockContent = blockMatch.groups[1]?.value ?: ""
                val fullPath = pathRegex.find(blockContent)?.groups?.get(1)?.value ?: "Unknown path"
                val pathParts = fullPath.split("/").let {
                    it.last() to it.dropLast(1).reversed().joinToString("/")
                }
                val changeType = changeTypeRegex.find(blockContent)?.groups?.get(1)?.value ?: "Unknown"

                val fileChangeItems = mutableListOf<FileChangeItem>()
                changeRegex.findAll(blockContent).forEach { changeMatch ->
                    val original = changeMatch.groups[1]?.value?.trim() ?: "No original content"
                    val updated = changeMatch.groups[2]?.value?.trim() ?: "No updated content"
                    fileChangeItems.add(FileChangeItem(original, updated))
                }
                if (fileChangeItems.isNotEmpty()) {
                    changes.add(CodeChangeFile(fullPath, pathParts.second, pathParts.first, fileChangeItems, isMerged = false, changeType = changeType))
                }
            }
        }
        return changes
    }

    private fun setupToolbar() {
        val actionGroup = DefaultActionGroup().apply {
            add(ShowChangeViewAction(project, changesListView.getChangesList()))
        }

        val toolbar: ActionToolbar = ActionManager.getInstance().createActionToolbar("CodeChangesToolbar", actionGroup, true)
        // 设置目标组件，通常是包含工具栏的主面板或者任何其他适当的组件
        toolbar.targetComponent = panel
        panel.setToolbar(toolbar.component)
    }
}

class ShowChangeViewAction(private val project: Project, private val changesList: JList<CodeChangeFile>) : AnAction("Show Diffs", "Show the differences", null) {
    override fun actionPerformed(e: AnActionEvent) {
        val selectedData = changesList.selectedValue
        if (selectedData != null) {
            showDiffFor(selectedData, project)
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    fun findFileByNameInProject(fileName: String, project: Project): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        val projectBasePath = project.basePath ?: return files
        val baseDir = LocalFileSystem.getInstance().findFileByPath(projectBasePath)

        fun searchFile(dir: VirtualFile) {
            for (file in dir.children) {
                if (file.isDirectory) {
                    searchFile(file)
                } else if (file.name == fileName) {
                    files.add(file)
                }
            }
        }

        baseDir?.let { searchFile(it) }
        return files
    }

    fun getOriginalFile(data: CodeChangeFile, project: Project): VirtualFile? {
        // 先尝试用完整路径查找
        val fullPathFile = LocalFileSystem.getInstance().findFileByPath(project.basePath + "/" + data.path)
        if (fullPathFile != null) {
            return fullPathFile
        }

        // 如果完整路径找不到，尝试只用文件名查找
        val files = findFileByNameInProject(data.filename, project)
        return when {
            files.isEmpty() -> null
            files.size == 1 -> files[0]
            else -> {
                // 如果找到多个同名文件，显示警告但不阻塞
                ApplicationManager.getApplication().invokeLater {
                    Messages.showWarningDialog(
                        project,
                        "Found multiple files named '${data.filename}'. Using the first match.",
                        "Multiple Files Found"
                    )
                }
                files[0]
            }
        }
    }

    private fun showDiffFor(data: CodeChangeFile, project: Project) {
        val content1: DocumentContent
        val content2: DocumentContent
        val diffContentFactory = DiffContentFactory.getInstance()
        val originalFile: VirtualFile?
        if (data.changeType == "CREATE") {
            content1 = diffContentFactory.create("", PlainTextFileType.INSTANCE)
            val createContent = EditorFactory.getInstance().createDocument(data.changeItems[0].updatedChunk)
            content2 = diffContentFactory.create(project, createContent)
            originalFile = null
        } else if (data.changeType == "MODIFY") {
            try {
                originalFile = getOriginalFile(data, project)
                val document: String = if (originalFile != null) {
                    FileDocumentManager.getInstance().getDocument(originalFile)?.text ?: ""
                } else {
                    Messages.showMessageDialog(
                        project,
                        "Original file not found: ${data.path}",
                        "Error",
                        Messages.getErrorIcon()
                    )
                    ""
                }

                val updated = getUpdatedFileContent(document, data.changeItems)
                val updatedDocument = EditorFactory.getInstance().createDocument(updated)

                content1 = diffContentFactory.create(document, PlainTextFileType.INSTANCE)
                content2 = diffContentFactory.create(project, updatedDocument)
            } catch (e: Exception) {
                throw RuntimeException("Failed to process MODIFY operation", e)
            }
        } else if (data.changeType == "DELETE") {
            originalFile = getOriginalFile(data, project)
            if (originalFile == null) {
                Messages.showMessageDialog(
                    project,
                    "Original file not found: ${data.path}",
                    "Error",
                    Messages.getErrorIcon()
                )
            }
            content1 = diffContentFactory.create(data.changeItems[0].originalChunk, PlainTextFileType.INSTANCE)
            content2 = diffContentFactory.create("", PlainTextFileType.INSTANCE)
        } else {
            throw RuntimeException("Unknown change type: ${data.changeType}")
        }

        // 后续代码保持不变...
        val request = SimpleDiffRequest("Diff - ${data.filename}", content1, content2, "Original", "Updated")
        val diffPanel = DiffManager.getInstance().createRequestPanel(this.project, {}, null)
        diffPanel.setRequest(request)

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
                        "MODIFY" -> originalFile?.let {
                            FileDocumentManager.getInstance().getDocument(originalFile)?.setText(content2.document.text)
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

    private fun getUpdatedFileContent(text: String, changeItems: List<FileChangeItem>): String {
        var updatedText = text
        val levenshteinDistance = LevenshteinDistance()

        changeItems.forEach { changeItem ->
            // Find the best match for the original text in the current file content using Levenshtein distance
            val bestMatch = findBestMatchOptimized(updatedText, changeItem.originalChunk, levenshteinDistance)

            if (bestMatch != null) {
                // Replace the best match with the updated content
                updatedText = updatedText.replaceFirst(bestMatch.trim(), changeItem.updatedChunk.trim())
            }
        }

        return updatedText
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

    /**
     * 优化后的 findBestMatch 函数，基于行分块并忽略空格进行匹配
     *
     * @param text 待搜索的文本
     * @param original 原始块，需要在 text 中找到最相似的块
     * @param levenshteinDistance Levenshtein 距离计算器
     * @return 最佳匹配的块，若无匹配则返回 null
     */
    fun findBestMatchOptimized(text: String, original: String, levenshteinDistance: LevenshteinDistance): String? {
        // 将文本和原始块按行分割
        val textLines = text.lines()
        val originalLines = original.lines()
        val blockSize = originalLines.size  // 使用与 original 块相同的行数进行分块

        // 预处理：去除每行的所有空格
        val normalizedTextLines = textLines.map { it.replace("\\s".toRegex(), "") }
        val normalizedOriginalLines = originalLines.map { it.replace("\\s".toRegex(), "") }
        val normalizedOriginal = normalizedOriginalLines.joinToString("\n")

        // 构建哈希映射
        val hashMap = HashMap<String, MutableList<Int>>()
        for (i in 0..(normalizedTextLines.size - blockSize)) {
            val block = normalizedTextLines.subList(i, i + blockSize).joinToString("\n")
            val hash = block.hashCode().toString()
            hashMap.computeIfAbsent(hash) { mutableListOf() }.add(i)
        }

        // 计算 original 块的哈希值
        val originalHash = normalizedOriginal.hashCode().toString()
        val candidateIndices = hashMap[originalHash] ?: emptyList()

        var minDistance = Int.MAX_VALUE
        var bestMatch: String? = null

        // 优先检查哈希完全匹配的块
        for (index in candidateIndices) {
            val candidateBlock = textLines.subList(index, index + blockSize).joinToString("\n")
            val distance = levenshteinDistance.apply(candidateBlock.replace("\\s".toRegex(), ""), original)
            if (distance < minDistance) {
                minDistance = distance
                bestMatch = candidateBlock
            }
        }

        // 如果没有找到哈希匹配的块，退回到滑动窗口并使用行分块进行优化
        if (bestMatch == null) {

            for (i in 0..(textLines.size - blockSize)) {
                val block = textLines.subList(i, i + blockSize).joinToString("\n")
                val normalizedBlock = block.replace("\\s".toRegex(), "")
                val distance = levenshteinDistance.apply(normalizedBlock, normalizedOriginal)
                if (distance < minDistance) {
                    minDistance = distance
                    bestMatch = block
                }
            }
        }

        return bestMatch
    }

    // 辅助函数：计算 Levenshtein 距离
    fun levenshteinDistance(a: String, b: String): Int {
        val n = a.length
        val m = b.length

        if (n == 0) return m
        if (m == 0) return n

        val dp = Array(n + 1) { it }
        for (j in 1..m) {
            var prev = dp[0]
            dp[0] = j
            for (i in 1..n) {
                val temp = dp[i]
                dp[i] = if (a[i - 1] == b[j - 1]) {
                    prev
                } else {
                    1 + minOf(prev, dp[i], dp[i - 1])
                }
                prev = temp
            }
        }
        return dp[n]
    }

}

data class CodeChangeFile(
    val path: String,  // 文件路径
    val dirPath: String,
    val filename: String,
    val changeItems: List<FileChangeItem>,
    var isMerged: Boolean,
    val changeType: String  // "CREATE", "MODIFY", "DELETE"
)

data class FileChangeItem(
    @Desc("原始文件内容chunk，如果该字段不为空，如果要填充该字段，一般意味着你需要先读取原始的文件内容, originalChunk 需要尽量保证在整个文件的唯一性，因为最终是使用 replace 函数修改文件内容，如果 originalChunk 在文件有多处的话，会导致错误的多余的修改") val originalChunk: String,
    @Desc("原始文件内容chunk 对应的更新内容") val updatedChunk: String,
)

data class FileChange(
    @Desc("文件路径") val path: String,
    @Desc("一个文件的多个变更，每一项是一个变更的 chunk") val changeItems: List<FileChangeItem>,
)
