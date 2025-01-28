// core/src/main/kotlin/com/github/zjh7890/gpttools/toolWindow/context/ChatFileTreeListPanel.kt

package com.github.zjh7890.gpttools.toolWindow.context

import com.github.zjh7890.gpttools.services.ChatSession
import com.github.zjh7890.gpttools.toolWindow.treePanel.DependenciesTreePanel
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel

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
        // 调用 updateDependencies
        dependenciesTreePanel.updateDependencies(session.appFileTree)
    }
}
