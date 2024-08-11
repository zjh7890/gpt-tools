package com.github.zjh7890.gpttools.toolWindow.treePanel

import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.github.zjh7890.gpttools.utils.PsiUtils.getDependencies
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JPanel
import javax.swing.JScrollPane
import com.intellij.ui.treeStructure.Tree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import java.util.*

class FileTreeListPanel(private val project: Project) : JPanel() {
    private val root = DefaultMutableTreeNode("Files")
    val tree = Tree(root)
    private val addedFiles = mutableSetOf<VirtualFile>()

    init {
        layout = java.awt.BorderLayout()
        add(JScrollPane(tree), java.awt.BorderLayout.CENTER)

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
                    val fileName = node?.userObject as? String
                    val virtualFile = addedFiles.find { it.name == fileName }
                    if (virtualFile != null) {
                        FileEditorManager.getInstance(project).openFile(virtualFile, true)
                    }
                }
            }
        })
    }

    fun addFile(file: VirtualFile) {
        if (addedFiles.add(file)) {
            val expandedPaths = getExpandedPaths()
            val node = DefaultMutableTreeNode(file.name)
            root.add(node)
            (tree.model as DefaultTreeModel).reload(root)
            restoreExpandedPaths(expandedPaths)
        }
    }

    fun addFileRecursively(file: VirtualFile, project: Project) {
        val expandedPaths = getExpandedPaths()
        val rootNode = DefaultMutableTreeNode(file.name)
        val queue = LinkedList<Pair<VirtualFile, DefaultMutableTreeNode>>()
        queue.add(file to rootNode)
        addedFiles.add(file)

        while (queue.isNotEmpty()) {
            val (currentFile, currentNode) = queue.poll()
            val dependencies = getDependencies(currentFile, project)
            dependencies.filter { it.isValid }.forEach { dependency ->
                if (addedFiles.add(dependency)) {
                    val childNode = DefaultMutableTreeNode(dependency.name)
                    currentNode.add(childNode)
                    queue.add(dependency to childNode)
                }
            }
        }

        root.add(rootNode)
        (tree.model as DefaultTreeModel).reload(root)
        restoreExpandedPaths(expandedPaths)
    }

    private fun getExpandedPaths(): Set<TreePath> {
        val expandedPaths = mutableSetOf<TreePath>()
        for (i in 0 until tree.rowCount) {
            val path = tree.getPathForRow(i)
            if (tree.isExpanded(path)) {
                expandedPaths.add(path)
            }
        }
        return expandedPaths
    }

    private fun restoreExpandedPaths(expandedPaths: Set<TreePath>) {
        for (path in expandedPaths) {
            tree.expandPath(path)
        }
    }

    fun removeSelectedNode() {
        val selectedNode = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
        if (selectedNode != null && selectedNode != root) { // 防止移除根节点
            val model = tree.model as DefaultTreeModel
            removeNodeAndChildren(selectedNode)
            model.reload(root)
        }
    }

    private fun removeNodeAndChildren(node: DefaultMutableTreeNode) {
        // Create a queue to hold nodes to be removed
        val nodesToRemove = LinkedList<DefaultMutableTreeNode>()
        nodesToRemove.add(node)

        while (nodesToRemove.isNotEmpty()) {
            val currentNode = nodesToRemove.poll()
            // Add all children to the queue
            for (i in 0 until currentNode.childCount) {
                nodesToRemove.add(currentNode.getChildAt(i) as DefaultMutableTreeNode)
            }
            // Remove the current node's file reference if it exists
            addedFiles.removeIf { it.name == currentNode.userObject as String }
            // Remove the current node from its parent
            currentNode.removeFromParent()
        }
    }

    fun copyAllFiles() {
        val sb: StringBuilder = StringBuilder()
        sb.append("下面是提供的信息：\n")
        for (addedFile in addedFiles) {
            val documentManager = FileDocumentManager.getInstance()
            val document = documentManager.getDocument(addedFile)
            sb.append("```\n"  + document?.text + "\n```\n").append("\n")
        }
        ClipboardUtils.copyToClipboard(sb.toString())
    }
}