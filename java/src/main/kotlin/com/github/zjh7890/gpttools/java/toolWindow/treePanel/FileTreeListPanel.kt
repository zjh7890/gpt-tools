package com.github.zjh7890.gpttools.java.toolWindow.treePanel

import com.github.zjh7890.gpttools.java.util.PsiUtils.getDependencies
import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.treeStructure.Tree
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class FileTreeListPanel(private val project: Project) : JPanel() {
    private val root = DefaultMutableTreeNode("")
    private val rootFilesNode = DefaultMutableTreeNode("Root Files")
    private val dependenciesNode = DefaultMutableTreeNode("Dependencies")
    val tree = Tree(root)
    private val addedFiles = mutableSetOf<VirtualFile>()

    init {
        layout = java.awt.BorderLayout()
        root.add(rootFilesNode)
        root.add(dependenciesNode)
        
        // 设置根节点不可见
        tree.isRootVisible = false
        // 显示根句柄
        tree.showsRootHandles = true
        
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
                    e.consume()
                }
            }
        })
    }

    fun addFile(file: VirtualFile) {
        if (addedFiles.add(file)) {
            val expandedPaths = getExpandedPaths()
            val node = DefaultMutableTreeNode(file.name)
            rootFilesNode.add(node)
            (tree.model as DefaultTreeModel).reload(root)
            restoreExpandedPaths(expandedPaths)
        }
    }

    fun addFileRecursively(file: VirtualFile, project: Project) {
        // 首先将该文件加入 rootFiles
        addFile(file)

        // 分析依赖并统一放入 dependencies 节点
        val expandedPaths = getExpandedPaths()
        val allDependencies = mutableSetOf<VirtualFile>()

        ApplicationManager.getApplication().runReadAction {
            val queue = LinkedList<VirtualFile>()
            queue.add(file)

            while (queue.isNotEmpty()) {
                val currentFile = queue.poll()
                val dependencies = getDependencies(currentFile, project).filter { it.isValid }

                dependencies.forEach { dependency ->
                    if (allDependencies.add(dependency) && !addedFiles.contains(dependency)) {
                        queue.add(dependency)
                    }
                }
            }
        }

        // 将依赖文件按包路径组织展示
        organizeDependencies(allDependencies)
        allDependencies.forEach { addedFiles.add(it) }

        (tree.model as DefaultTreeModel).reload(root)
        restoreExpandedPaths(expandedPaths)
    }

    fun rerunAnalysis(project: Project) {
        // 清空dependenciesNode下的内容
        dependenciesNode.removeAllChildren()
        // 根据rootFilesNode下的文件重新分析依赖
        val rootFileNames = (0 until rootFilesNode.childCount).map {
            (rootFilesNode.getChildAt(it) as DefaultMutableTreeNode).userObject as String
        }

        val expandedPaths = getExpandedPaths()
        val allDependencies = mutableSetOf<VirtualFile>()

        ApplicationManager.getApplication().runReadAction {
            for (rootFileName in rootFileNames) {
                val rootFile = addedFiles.find { it.name == rootFileName } ?: continue
                val queue = LinkedList<VirtualFile>()
                queue.add(rootFile)

                while (queue.isNotEmpty()) {
                    val currentFile = queue.poll()
                    val dependencies = getDependencies(currentFile, project).filter { it.isValid }
                    dependencies.forEach { dep ->
                        if (allDependencies.add(dep) && !rootFileNames.contains(dep.name)) {
                            queue.add(dep)
                        }
                    }
                }
            }
        }

        // 将依赖文件按包路径组织展示
        organizeDependencies(allDependencies)
        allDependencies.forEach { addedFiles.add(it) }

        (tree.model as DefaultTreeModel).reload(root)
        restoreExpandedPaths(expandedPaths)
    }

    private fun getExpandedPaths(): Set<TreePath> {
        val expandedPaths = mutableSetOf<TreePath>()
        ApplicationManager.getApplication().invokeAndWait {
            for (i in 0 until tree.rowCount) {
                val path = tree.getPathForRow(i)
                if (tree.isExpanded(path)) {
                    expandedPaths.add(path)
                }
            }
        }
        return expandedPaths
    }

    private fun restoreExpandedPaths(expandedPaths: Set<TreePath>) {
        for (path in expandedPaths) {
            tree.expandPath(path)
        }
    }

    private fun organizeDependencies(dependencies: Set<VirtualFile>) {
        // 清空现有依赖节点
        dependenciesNode.removeAllChildren()
        
        // 按包路径分组
        val packageMap = mutableMapOf<String, MutableList<VirtualFile>>()
        
        dependencies.forEach { dep ->
            val packagePath = dep.parent?.path ?: ""
            packageMap.getOrPut(packagePath) { mutableListOf() }.add(dep)
        }
        
        // 为每个包创建节点
        packageMap.forEach { (packagePath, files) ->
            // 将完整路径转成包名
            val packageName = packagePath.substringAfter("src/main/java/")
                .replace('/', '.')
                .ifEmpty { "(default package)" }
            
            val packageNode = DefaultMutableTreeNode(packageName)
            files.sortedBy { it.name }.forEach { file ->
                packageNode.add(DefaultMutableTreeNode(file.name))
            }
            dependenciesNode.add(packageNode)
        }
    }

    fun removeSelectedNodes() {
        val expandedPaths = getExpandedPaths()
        val selectedPaths = tree.selectionPaths
        if (selectedPaths != null) {
            val model = tree.model as DefaultTreeModel
            selectedPaths.forEach { path ->
                val selectedNode = path.lastPathComponent as? DefaultMutableTreeNode
                if (selectedNode != null && selectedNode != root && selectedNode != rootFilesNode && selectedNode != dependenciesNode) {
                    removeNodeAndChildren(selectedNode)
                }
            }
            model.reload(root)
            restoreExpandedPaths(expandedPaths)
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

    fun copyAllFiles(project: Project) {
        val files = addedFiles.map { FileUtil.readFileInfoForLLM(it, project) }.joinToString("\n\n")
        val sb: StringBuilder = StringBuilder()
        sb.append("下面是提供的信息：\n" + FileUtil.wrapBorder(files))
        ClipboardUtils.copyToClipboard(sb.toString())
    }
}