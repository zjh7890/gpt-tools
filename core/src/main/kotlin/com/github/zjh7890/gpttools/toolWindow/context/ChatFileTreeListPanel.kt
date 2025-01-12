// core/src/main/kotlin/com/github/zjh7890/gpttools/toolWindow/context/ChatFileTreeListPanel.kt

package com.github.zjh7890.gpttools.toolWindow.context

import com.github.zjh7890.gpttools.services.ChatSession
import com.github.zjh7890.gpttools.toolWindow.treePanel.ClassDependencyInfo
import com.github.zjh7890.gpttools.toolWindow.treePanel.DependenciesTreePanel
import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class ChatFileTreeListPanel(private val project: Project) : JPanel() {
    var currentSession: ChatSession? = null
    // 实例化 DependenciesTreePanel
    val dependenciesTreePanel = DependenciesTreePanel(project)

    init {
        layout = BorderLayout()

        dependenciesTreePanel.preferredSize = Dimension(dependenciesTreePanel.preferredSize.width, JBUI.scale(250))
        dependenciesTreePanel.maximumSize = Dimension(dependenciesTreePanel.preferredSize.width, JBUI.scale(250))  // 限制最大尺寸
        dependenciesTreePanel.minimumSize = Dimension(dependenciesTreePanel.preferredSize.width, JBUI.scale(250))
        dependenciesTreePanel.tree.isRootVisible = false

        // 添加到主面板
        add(dependenciesTreePanel, BorderLayout.CENTER)
    }

    /**
     * 更新文件树，采用类似 Dependencies 节点的层级结构展示（project -> module -> package -> file）
     */
    fun updateFileTree(session: ChatSession) {
        currentSession = session

        session.projectFileTrees.forEach { projectTree ->
            // 创建 projectName 节点
            val projectNode = DefaultMutableTreeNode(projectTree.projectName)

            // 按模块分组文件
            val moduleToFilesMap = projectTree.files.groupBy { file ->
                getModuleName(file)
            }

            // 创建模块节点，添加到 projectName 节点下
            moduleToFilesMap.forEach { (moduleName, files) ->
                val moduleNode = DefaultMutableTreeNode(moduleName)
                projectNode.add(moduleNode)

                // 按包分组文件
                val packageToFilesMap = files.groupBy { file ->
                    getPackageName(file)
                }

                // 创建包节点，添加到模块节点下
                packageToFilesMap.forEach { (packageName, packageFiles) ->
                    val packageNode = DefaultMutableTreeNode(packageName)
                    moduleNode.add(packageNode)

                    // 添加文件节点到包节点下
                    packageFiles.forEach { file ->
                        val fileNode = DefaultMutableTreeNode(file)
                        packageNode.add(fileNode)
                    }
                }
            }
        }
    }

    /**
     * 获取文件所属的模块名称
     */
    private fun getModuleName(file: VirtualFile): String {
        // 假设模块名称可以从文件路径中提取，例如 /project/module/src/...
        val path = file.path
        val segments = path.split("/")
        val srcIndex = segments.indexOf("src")
        return if (srcIndex > 0 && segments.size > srcIndex) segments[srcIndex - 1] else "Unknown Module"
    }

    /**
     * 获取文件的包名
     */
    private fun getPackageName(file: VirtualFile): String {
        val projectPath = project.basePath ?: return "(default package)"
        val filePath = file.path

        // 支持 src/main/java 或 src/main/kotlin
        val srcJava = "src/main/java/"
        val srcKotlin = "src/main/kotlin/"
        val indexJava = filePath.indexOf(srcJava)
        val indexKotlin = filePath.indexOf(srcKotlin)

        val startIndex = when {
            indexJava != -1 -> indexJava + srcJava.length
            indexKotlin != -1 -> indexKotlin + srcKotlin.length
            else -> return "(default package)"
        }

        val relativePath = if (startIndex < filePath.length) {
            filePath.substring(startIndex, filePath.lastIndexOf('/'))
        } else {
            ""
        }

        return if (relativePath.isNotEmpty()) {
            relativePath.replace('/', '.')
        } else {
            "(default package)"
        }
    }

    /**
     * 获取选中的类
     */
    private fun getSelectedClasses(): List<PsiClass> {
        // 实现根据树中选中的节点获取对应的 PsiClass
        // 这里需要根据您的具体实现填充
        // 示例返回空列表
        return emptyList()
    }
}
