package com.github.zjh7890.gpttools.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiElement
import com.intellij.openapi.roots.ProjectRootManager
import javax.swing.tree.TreePath
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import java.util.*

class FileTreeListPanel(private val project: Project) : JPanel() {
    private val root = DefaultMutableTreeNode("Files")
    val tree = JTree(root)
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
            dependencies.filter { it.isValid && it.isInLocalFileSystem }.forEach { dependency ->
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

    private fun getDependencies(file: VirtualFile, project: Project): List<VirtualFile> {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return emptyList()
        val referencedFiles = mutableListOf<PsiFile>()

        PsiTreeUtil.findChildrenOfType(psiFile, PsiElement::class.java).forEach { element ->
            element.references.forEach { reference ->
                val resolvedFile = reference.resolve()?.containingFile
                if (resolvedFile != null && resolvedFile !== psiFile) {
                    if (isFileInProject(resolvedFile.virtualFile, project)) {
                        referencedFiles.add(resolvedFile)
                    }
                }
            }
        }

        return referencedFiles.map { it.virtualFile }.distinct()
    }

    private fun isFileInProject(file: VirtualFile, project: Project): Boolean {
        return ProjectRootManager.getInstance(project).fileIndex.isInContent(file)
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
            model.removeNodeFromParent(selectedNode)
            addedFiles.removeIf { it.name == selectedNode.userObject as String }
        }
    }
}