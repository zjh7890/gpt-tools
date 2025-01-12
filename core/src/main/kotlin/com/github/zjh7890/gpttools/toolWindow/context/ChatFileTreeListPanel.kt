// core/src/main/kotlin/com/github/zjh7890/gpttools/toolWindow/context/ChatFileTreeListPanel.kt

package com.github.zjh7890.gpttools.toolWindow.context

import com.github.zjh7890.gpttools.services.ChatSession
import com.github.zjh7890.gpttools.toolWindow.treePanel.CheckboxTreeNode
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
     * 更新文件树，采用项目 -> 类 -> 方法的层级结构展示
     */
    fun updateFileTree(session: ChatSession) {
        currentSession = session

        // 生成 classGraph
        session.generateClassGraph(project)
        val classGraph = session.classGraph

        // 清空现有的树节点
        val root = dependenciesTreePanel.root
        root.removeAllChildren()

        session.projectFileTrees.forEach { projectTree ->
            // 创建项目节点
            val projectNode = CheckboxTreeNode(projectTree.projectName)
            root.add(projectNode)

            // 遍历类
            projectTree.classes.forEach { projectClass ->
                // 创建类节点
                val classNode = CheckboxTreeNode(projectClass.className)
                projectNode.add(classNode)

                // 遍历方法
                projectClass.methods.forEach { method ->
                    val methodNode = CheckboxTreeNode(method.methodName)
                    classNode.add(methodNode)
                }
            }
        }

        // 刷新树模型并展开节点
        (dependenciesTreePanel.tree.model as DefaultTreeModel).reload(root)
        dependenciesTreePanel.expandDefaultNodes()

        // 调用 updateDependencies
        dependenciesTreePanel.updateDependencies(classGraph)
    }
}
