package com.github.zjh7890.gpttools.toolWindow.chat.block

import CodeChangeFile
import com.intellij.openapi.project.Project
import org.apache.commons.lang3.StringUtils
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.*

class ChangesListView(
    private val changes: List<CodeChangeFile>,
    private val project: Project
) : JPanel() {

    private val changesList = JList<CodeChangeFile>()

    init {
        initialize()
    }

    private fun initialize() {
        changesList.setListData(changes.toTypedArray())
        
        // 添加鼠标监听器处理双击事件
        changesList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {  // 检查是否是双击
                    val selectedFile = changesList.selectedValue
                    selectedFile?.let {
                        val projectBasePath = project.basePath
                        // 构建完整的文件路径
                        val filePath = if (StringUtils.isNotEmpty(it.dirPath)) {
                            "$projectBasePath/${it.dirPath}/${it.filename}"
                        } else {
                            "$projectBasePath/${it.filename}"
                        }
                        
                        // 使用 VirtualFileManager 查找并打开文件
                        com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                            .findFileByPath(filePath)?.let { virtualFile ->
                                com.intellij.openapi.fileEditor.FileEditorManager
                                    .getInstance(project)
                                    .openFile(virtualFile, true)
                            }
                    }
                }
            }
        })

        changesList.cellRenderer = object : ListCellRenderer<CodeChangeFile> {
            override fun getListCellRendererComponent(
                list: JList<out CodeChangeFile>?,
                value: CodeChangeFile?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val panel = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    val mergeStatus = if (value?.isMerged == true) "✅" else "❗"
                    val statusLabel = JLabel(mergeStatus)
                    add(statusLabel)

                    val filenameColor = when (value?.changeType) {
                        "CREATE" -> Color.decode("#067D17")
                        "MODIFY" -> Color.decode("#0033B3")
                        "DELETE" -> Color.decode("#6C707E")
                        else -> list?.foreground
                    }

                    val labelValue: String
                    if (StringUtils.isNotEmpty(value?.dirPath)) {
                        labelValue = "${value?.filename} (${value?.dirPath})"
                    } else {
                        labelValue = "${value?.filename}"
                    }
                    val label = JLabel(labelValue).apply {
                        foreground = filenameColor
                    }
                    add(label)
                }
                panel.isOpaque = true
                if (isSelected) {
                    panel.background = Color.decode("#d8e4fc")  // 使用新的选中颜色
                    panel.foreground = list?.selectionForeground
                } else {
                    panel.background = list?.background
                    panel.foreground = list?.foreground
                }
                return panel
            }
        }

        // Dynamic sizing without JScrollPane
        val itemCount = changesList.model.size

        if (itemCount > 0) {
            // Get the height of one cell
            val cellRenderer = changesList.cellRenderer
            val sampleValue = changesList.model.getElementAt(0)
            val cellComponent = cellRenderer.getListCellRendererComponent(changesList, sampleValue, 0, false, false)
            val cellHeight = cellComponent.preferredSize.height

            // Calculate total height
            val listHeight = itemCount * cellHeight

            // Set preferred size
//            changesList.preferredSize = Dimension(this.width, listHeight)
        } else {
            // Empty list
//            changesList.preferredSize = Dimension(this.width, 0)
        }

        // Set layout and add changesList to this panel
        this.layout = BoxLayout(this, BoxLayout.Y_AXIS)
        this.add(changesList)
    }

    fun getChangesList(): JList<CodeChangeFile> {
        return changesList
    }
}