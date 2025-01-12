package com.github.zjh7890.gpttools.toolWindow.context

import com.github.zjh7890.gpttools.services.ChatSession
import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import com.intellij.openapi.ui.Messages
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class ChatFileTreeListPanel(private val project: Project) {
    private val treeModel: DefaultTreeModel
    val tree: Tree
    private val root: DefaultMutableTreeNode
    var currentSession: ChatSession? = null
    var scrollPanel: JBScrollPane

    init {
        // 初始化根节点为 "Dependencies"
        root = DefaultMutableTreeNode("Dependencies")
        treeModel = DefaultTreeModel(root)
        tree = Tree(treeModel).apply {
            isRootVisible = false
            showsRootHandles = true
            cellRenderer = DependencyTreeCellRenderer()
        }
        scrollPanel = JBScrollPane(tree)
        scrollPanel.preferredSize = Dimension(scrollPanel.preferredSize.width, JBUI.scale(250))
        scrollPanel.maximumSize = Dimension(scrollPanel.preferredSize.width, JBUI.scale(250))  // 限制最大尺寸
        scrollPanel.minimumSize = Dimension(scrollPanel.preferredSize.width, JBUI.scale(250))  // 限制最大尺寸
        setupListeners()
    }

    private fun setupListeners() {
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
                    val userObject = node?.userObject
                    if (userObject is VirtualFile && !userObject.isDirectory) {
                        FileEditorManager.getInstance(project).openFile(userObject, true)
                    }
                }
            }
        })
    }

    /**
     * 更新文件树，采用类似 Dependencies 节点的层级结构展示
     */
    fun updateFileTree(session: ChatSession) {
        currentSession = session
        root.removeAllChildren()

        session.projectFileTrees.forEach { projectTree ->
            // 创建 projectName 节点
            val projectNode = DefaultMutableTreeNode(projectTree.projectName)
            root.add(projectNode)

            // 按模块分组文件
            val moduleToFilesMap = projectTree.files.groupBy { file ->
                getModuleName(file)
            }

            // 创建模块节点，添加到 projectName 节点下
            moduleToFilesMap.forEach { (moduleName, files) ->
                val moduleNode = DefaultMutableTreeNode(moduleName)
                files.forEach { file ->
                    val fileNode = DefaultMutableTreeNode(file)
                    moduleNode.add(fileNode)
                }
                projectNode.add(moduleNode)
            }
        }

        treeModel.reload()
        TreeUtil.expandAll(tree)
    }

    /**
     * 获取文件所属的模块名称
     */
    private fun getModuleName(file: VirtualFile): String {
        val currentSession = currentSession ?: return "Unknown Module"
        // 假设模块名称可以从文件路径中提取，例如 /project/module/src/...
        val path = file.path
        val segments = path.split("/")
        val moduleIndex = segments.indexOf("src")
        return if (moduleIndex > 0) segments[moduleIndex - 1] else "Unknown Module"
    }

    /**
     * 移除选中的节点
     */
    fun removeSelectedNodes() {
        val selectedPaths = tree.selectionPaths
        if (selectedPaths == null || selectedPaths.isEmpty()) {
            // 可以选择显示提示信息
            return
        }

        selectedPaths.forEach { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@forEach
            if (node.parent == root) {
                // 不允许移除包节点
                Messages.showErrorDialog(
                    project,
                    "无法移除包节点。请仅移除文件节点。",
                    "移除节点"
                )
                return@forEach
            }
            val parent = node.parent as? DefaultMutableTreeNode ?: return@forEach
            parent.remove(node)
        }

        treeModel.reload()
    }

    /**
     * 将选中的节点及其子节点中的所有文件路径复制到剪贴板
     */
    fun copyAllFiles() {
        val selectedPaths = tree.selectionPaths
        if (selectedPaths == null || selectedPaths.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "请先选择要复制的节点。",
                "复制文件"
            )
            return
        }

        val filePaths = mutableListOf<String>()

        selectedPaths.forEach { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@forEach
            collectFilePaths(node, filePaths)
        }

        if (filePaths.isNotEmpty()) {
            val clipboardContent = filePaths.joinToString("\n")
            ClipboardUtils.copyToClipboard(clipboardContent)
            Messages.showInfoMessage(
                project,
                "已复制 ${filePaths.size} 个文件路径到剪贴板。",
                "复制成功"
            )
        } else {
            Messages.showInfoMessage(
                project,
                "选中的节点下没有文件。",
                "复制文件"
            )
        }
    }

    /**
     * 递归收集节点下所有文件的路径
     */
    private fun collectFilePaths(node: DefaultMutableTreeNode, filePaths: MutableList<String>) {
        val userObject = node.userObject
        if (userObject is VirtualFile && !userObject.isDirectory) {
            filePaths.add(userObject.path)
        }

        for (i in 0 until node.childCount) {
            val childNode = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            collectFilePaths(childNode, filePaths)
        }
    }

    /**
     * 递归展开选中的节点
     */
    fun expandSelectedNodes() {
        val selectedPaths = tree.selectionPaths
        if (selectedPaths == null || selectedPaths.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "请先选择要展开的节点。",
                "展开节点"
            )
            return
        }

        selectedPaths.forEach { path ->
            tree.expandPath(path)
            expandAllChildren(path)
        }
    }

    /**
     * 递归折叠选中的节点
     */
    fun collapseSelectedNodes() {
        val selectedPaths = tree.selectionPaths
        if (selectedPaths == null || selectedPaths.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "请先选择要折叠的节点。",
                "折叠节点"
            )
            return
        }

        selectedPaths.forEach { path ->
            tree.collapsePath(path)
        }
    }

    /**
     * 递归展开节点下的所有子节点
     */
    private fun expandAllChildren(path: TreePath) {
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        for (i in 0 until node.childCount) {
            val childNode = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val childPath = path.pathByAddingChild(childNode)
            tree.expandPath(childPath)
            expandAllChildren(childPath)
        }
    }

    /**
     * 获取选中的文件
     */
    fun getSelectedFiles(): List<VirtualFile> {
        val selectedPaths = tree.selectionPaths ?: return emptyList()
        val files = mutableListOf<VirtualFile>()
        selectedPaths.forEach { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@forEach
            val userObject = node.userObject
            if (userObject is VirtualFile && !userObject.isDirectory) {
                files.add(userObject)
            }
        }
        return files
    }
}

/**
 * 自定义树节点渲染器，以显示不同类型的节点图标和文本
 */
private class DependencyTreeCellRenderer : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)

        val node = value as? DefaultMutableTreeNode
        val userObject = node?.userObject

        when {
            node?.parent == tree.model.root -> {
                // projectName 节点
                icon = AllIcons.Nodes.Module
                text = userObject.toString()
            }
            node?.parent?.parent == tree.model.root -> {
                // module 节点
                icon = AllIcons.Nodes.Folder
                text = userObject.toString()
            }
            userObject is VirtualFile -> {
                if (userObject.isDirectory) {
                    icon = if (expanded) AllIcons.Nodes.Folder else AllIcons.Nodes.ExtractedFolder
                } else {
                    icon = AllIcons.FileTypes.Any_type
                }
                text = userObject.name
            }
            else -> {
                // 其他情况，使用默认图标和文本
                icon = AllIcons.FileTypes.Any_type
                text = userObject?.toString() ?: ""
            }
        }

        return this
    }
}
