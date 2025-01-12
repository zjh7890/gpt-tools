// core/src/main/kotlin/com/github/zjh7890/gpttools/toolWindow/context/ChatFileTreeListPanel.kt

package com.github.zjh7890.gpttools.toolWindow.context

import com.github.zjh7890.gpttools.services.ChatSession
import com.github.zjh7890.gpttools.toolWindow.treePanel.CheckboxTreeNode
import com.github.zjh7890.gpttools.toolWindow.treePanel.DependenciesTreePanel
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.tree.DefaultTreeModel

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

        // 创建一个映射来存储每个项目的类节点
        val projectClassNodes = mutableMapOf<String, MutableMap<String, CheckboxTreeNode>>()

        session.projectTrees.forEach { projectTree ->
            // 创建项目节点
            val projectNode = CheckboxTreeNode(projectTree.projectName)
            root.add(projectNode)

            // 为当前项目创建类节点映射
            val classNodes = projectClassNodes.getOrPut(projectTree.projectName) { mutableMapOf() }

            // 遍历文件
            projectTree.files.forEach { file ->
                // 遍历类
                file.classes.forEach { projectClass ->
                    // 获取或创建类节点
                    val classNode = classNodes.getOrPut(projectClass.className) {
                        val node = CheckboxTreeNode(projectClass.className)
                        projectNode.add(node)
                        node
                    }

                    // 遍历方法
                    projectClass.methods.forEach { method ->
                        // 检查该方法是否已经存在
                        if (!classNode.children().asSequence().any {
                                (it as? CheckboxTreeNode)?.text == method.methodName
                            }) {
                            val methodNode = CheckboxTreeNode(method.methodName)
                            classNode.add(methodNode)
                        }
                    }
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
