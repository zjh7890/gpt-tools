package com.github.zjh7890.gpttools.toolWindow.llmChat

import com.github.zjh7890.gpttools.services.ProjectFileTree
import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.JOptionPane
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class ChatFileTreeListPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val treeModel: DefaultTreeModel
    val tree: Tree
    private val root: DefaultMutableTreeNode

    init {
        root = DefaultMutableTreeNode("Files")
        treeModel = DefaultTreeModel(root)
        tree = Tree(treeModel).apply {
            isRootVisible = false
            showsRootHandles = true
            cellRenderer = FileTreeCellRenderer()
        }
        add(JBScrollPane(tree), BorderLayout.CENTER)
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

    fun updateFileTree(projectFileTrees: List<ProjectFileTree>) {
        root.removeAllChildren()

        projectFileTrees.forEach { projectTree ->
            val projectNode = DefaultMutableTreeNode(projectTree.projectName)
            projectTree.files.forEach { file ->
                addFileToProjectNode(projectNode, file)
            }
            root.add(projectNode)
        }

        treeModel.reload()
        TreeUtil.expandAll(tree)
    }

    private fun addFileToProjectNode(projectNode: DefaultMutableTreeNode, file: VirtualFile) {
        // 获取文件相对于项目根目录的相对路径
        val baseDir = project.basePath ?: ""
        val relativePath = VfsUtilCore.getRelativePath(file, project.baseDir)
        if (relativePath == null) {
            // 如果无法确定相对路径，直接添加到项目节点下
            projectNode.add(DefaultMutableTreeNode(file))
            return
        }

        val pathComponents = relativePath.split('/')

        var currentNode = projectNode
        for (i in pathComponents.indices) {
            val component = pathComponents[i]
            val isLast = i == pathComponents.size - 1
            val existingChild = (0 until currentNode.childCount)
                .mapNotNull { currentNode.getChildAt(it) as? DefaultMutableTreeNode }
                .firstOrNull { childNode ->
                    val obj = childNode.userObject
                    when (obj) {
                        is String -> obj == component
                        is VirtualFile -> obj.name == component
                        else -> false
                    }
                }

            if (existingChild != null) {
                currentNode = existingChild
            } else {
                val newNode = if (isLast && !file.isDirectory) {
                    DefaultMutableTreeNode(file)
                } else {
                    DefaultMutableTreeNode(component)
                }
                currentNode.add(newNode)
                currentNode = newNode
            }
        }
    }

    /**
     * 移除选中的节点
     */
    fun removeSelectedNodes() {
        val selectedPaths = tree.selectionPaths
        if (selectedPaths == null || selectedPaths.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先选择要移除的节点。", "移除节点", JOptionPane.WARNING_MESSAGE)
            return
        }

        selectedPaths.forEach { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@forEach
            if (node.parent == root) {
                // 不允许移除项目根节点
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
            JOptionPane.showMessageDialog(this, "请先选择要复制的节点。", "复制文件", JOptionPane.WARNING_MESSAGE)
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
            JOptionPane.showMessageDialog(this, "已复制 ${filePaths.size} 个文件路径到剪贴板。", "复制成功", JOptionPane.INFORMATION_MESSAGE)
        } else {
            JOptionPane.showMessageDialog(this, "选中的节点下没有文件。", "复制文件", JOptionPane.INFORMATION_MESSAGE)
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
            JOptionPane.showMessageDialog(this, "请先选择要展开的节点。", "展开节点", JOptionPane.WARNING_MESSAGE)
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
            JOptionPane.showMessageDialog(this, "请先选择要折叠的节点。", "折叠节点", JOptionPane.WARNING_MESSAGE)
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
}

private class FileTreeCellRenderer : DefaultTreeCellRenderer() {
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

        when (userObject) {
            is String -> {
                // 判断是否为项目节点（父节点为 root）
                if (node.parent == root) {
                    // 项目节点
                    icon = AllIcons.Nodes.Module
                } else {
                    // 目录节点
                    icon = if (expanded) AllIcons.Nodes.Folder else AllIcons.Nodes.ExtractedFolder
                }
                text = userObject
            }
            is VirtualFile -> {
                // 文件节点
                icon = AllIcons.FileTypes.Any_type
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

    companion object {
        private val root = DefaultMutableTreeNode("Files")
    }
}
