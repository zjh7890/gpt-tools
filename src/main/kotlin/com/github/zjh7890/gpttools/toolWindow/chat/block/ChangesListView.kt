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
        
        // 设置 JList 的对齐方式
        changesList.alignmentX = Component.LEFT_ALIGNMENT
        changesList.layoutOrientation = JList.VERTICAL
        changesList.visibleRowCount = -1
        
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
                    // 添加左边距
                    add(Box.createHorizontalStrut(5))
                    
                    val mergeStatus = if (value?.isMerged == true) "✅" else "❗"
                    val statusLabel = JLabel(mergeStatus).apply {
                        // 设置状态图标的对齐方式
                        alignmentX = Component.LEFT_ALIGNMENT 
                    }
                    add(statusLabel)
                    
                    // 添加状态图标和文件名之间的间距
                    add(Box.createHorizontalStrut(5))

                    val filenameColor = when (value?.changeType) {
                        "CREATE" -> Color.decode("#067D17")
                        "MODIFY" -> Color.decode("#0033B3") 
                        "DELETE" -> Color.decode("#6C707E")
                        else -> list?.foreground
                    }

                    val labelValue: String = if (StringUtils.isNotEmpty(value?.dirPath)) {
                        "${value?.filename} (${value?.dirPath})"
                    } else {
                        "${value?.filename}"
                    }
                    
                    val label = JLabel(labelValue).apply {
                        foreground = filenameColor
                        // 设置文件名的对齐方式
                        alignmentX = Component.LEFT_ALIGNMENT
                    }
                    add(label)
                    
                    // 添加右边距
                    add(Box.createHorizontalStrut(5))
                    
                    // 添加弹性空间,将内容推到左侧
                    add(Box.createHorizontalGlue())
                }
                
                panel.isOpaque = true
                panel.maximumSize = Dimension(list?.width ?: 300, panel.preferredSize.height)
                if (isSelected) {
                    panel.background = Color.decode("#d8e4fc")
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

        // Set panel and list alignment
        this.alignmentX = Component.LEFT_ALIGNMENT
        this.layout = BoxLayout(this, BoxLayout.Y_AXIS)
        this.add(changesList)
    }

    fun getChangesList(): JList<CodeChangeFile> {
        return changesList
    }
}