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
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class ChatFileTreeListPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val treeModel: DefaultTreeModel
    val tree: Tree
    private val root: DefaultMutableTreeNode
    var currentSession: ChatSession? = null

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

    /**
     * 更新文件树，采用扁平化包结构展示
     */
    fun updateFileTree(session: ChatSession) {
        currentSession = session
        root.removeAllChildren()

        session.projectFileTrees.forEach { projectTree ->
            // 创建项目名称节点
            val projectNode = DefaultMutableTreeNode(projectTree.projectName)
            root.add(projectNode)

            // 按全路径分组文件
            val packageToFilesMap = projectTree.files.groupBy { file ->
                getFullPackageName(file)
            }

            // 创建包节点，添加到项目节点下
            packageToFilesMap.forEach { (packageName, files) ->
                val packageNode = DefaultMutableTreeNode(packageName)
                files.forEach { file ->
                    packageNode.add(DefaultMutableTreeNode(file))
                }
                projectNode.add(packageNode)
            }
        }

        treeModel.reload()
        TreeUtil.expandAll(tree)
    }

    /**
     * 获取文件的全包名
     */
    private fun getFullPackageName(file: VirtualFile): String {
        val currentSession = currentSession ?: return ""
        // 查找文件所属的项目树
        val projectTree = currentSession.projectFileTrees.find { tree ->
            tree.files.contains(file)
        } ?: return ""

        // 通过项目名找到对应的 Project 实例
        val targetProject = ProjectManager.getInstance().openProjects.find {
            it.name == projectTree.projectName
        } ?: return ""

        // 使用项目的根路径计算相对路径
        val packagePath = file.parent?.let {
            VfsUtilCore.getRelativePath(it, targetProject.baseDir, '/')
        } ?: ""

        return packagePath.replace('/', '.')
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
                // 不允许移除包节点
                JOptionPane.showMessageDialog(
                    this,
                    "无法移除包节点。请仅移除文件节点。",
                    "移除节点",
                    JOptionPane.ERROR_MESSAGE
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
            JOptionPane.showMessageDialog(
                this,
                "已复制 ${filePaths.size} 个文件路径到剪贴板。",
                "复制成功",
                JOptionPane.INFORMATION_MESSAGE
            )
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


/**
 * 自定义树节点渲染器，以显示不同类型的节点图标和文本
 */
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
                // 判断是否为包节点（父节点为 root）
                if (node.parent == root) {
                    // 包节点
                    icon = AllIcons.Nodes.Package
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
