package com.github.zjh7890.gpttools.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

// 1. 定义三态状态枚举
enum class CheckState {
    SELECTED, UNSELECTED, INDETERMINATE
}

// 2. 定义支持三态的树节点
class TriStateTreeNode(userObject: Any? = null) : javax.swing.tree.DefaultMutableTreeNode(userObject) {
    var state: CheckState = CheckState.SELECTED
        set(value) {
            if (field != value) {
                field = value
                updateChildrenState()
                updateParentState()
            }
        }

    private fun updateChildrenState() {
        if (state == CheckState.INDETERMINATE) return  // 不向下传播 indeterminate 状态
        for (i in 0 until childCount) {
            val child = getChildAt(i) as? TriStateTreeNode ?: continue
            child.state = state
        }
    }

    private fun updateParentState() {
        val parentNode = parent as? TriStateTreeNode ?: return
        var selectedCount = 0
        var unselectedCount = 0
        for (i in 0 until parentNode.childCount) {
            when ((parentNode.getChildAt(i) as? TriStateTreeNode)?.state) {
                CheckState.SELECTED -> selectedCount++
                CheckState.UNSELECTED -> unselectedCount++
                CheckState.INDETERMINATE -> { /*忽略*/ }
                else -> {}
            }
        }
        parentNode.state = when {
            selectedCount == parentNode.childCount -> CheckState.SELECTED
            unselectedCount == parentNode.childCount -> CheckState.UNSELECTED
            else -> CheckState.INDETERMINATE
        }
    }

    // 点击时切换状态：如果当前为 SELECTED 或 INDETERMINATE，则变为 UNSELECTED；否则变为 SELECTED
    fun toggleState() {
        state = when (state) {
            CheckState.SELECTED, CheckState.INDETERMINATE -> CheckState.UNSELECTED
            CheckState.UNSELECTED -> CheckState.SELECTED
        }
    }

    override fun toString(): String {
        return userObject?.toString() ?: ""
    }
}

// 3. 自定义 TriStateCheckBox，重写 paintComponent 绘制 indeterminate 状态
class TriStateCheckBox : JCheckBox() {
    var triState: CheckState = CheckState.UNSELECTED
        set(value) {
            field = value
            repaint()
        }

    override fun paintComponent(g: Graphics) {
        // 先调用超类绘制默认的复选框（包括边框和背景）
        super.paintComponent(g)
        // 如果状态为 INDETERMINATE，则在复选框内部绘制一个横条标记
        if (triState == CheckState.INDETERMINATE) {
            // 假设复选框图标区域宽度约 16 像素
            val iconSize = 16
            // 计算横条绘制区域（这里简单在左侧区域内绘制）
            val x = 2
            val y = (height - 3) / 2
            g.color = Color.BLACK
            g.fillRect(x, y, iconSize - 4, 3)
        }
    }
}

// 4. 自定义 CellRenderer，使用 TriStateCheckBox 替代普通的 JCheckBox
class TriStateCheckboxTreeCellRenderer : DefaultTreeCellRenderer() {
    private val triStateCheckBox = TriStateCheckBox()
    private val panel = JPanel(BorderLayout())

    init {
        panel.isOpaque = false
        triStateCheckBox.isOpaque = false
    }

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        val defaultComponent = super.getTreeCellRendererComponent(
            tree, value, selected, expanded, leaf, row, hasFocus
        )
        if (value is TriStateTreeNode) {
            when (value.state) {
                CheckState.SELECTED -> {
                    triStateCheckBox.isSelected = true
                    triStateCheckBox.triState = CheckState.SELECTED
                }
                CheckState.UNSELECTED -> {
                    triStateCheckBox.isSelected = false
                    triStateCheckBox.triState = CheckState.UNSELECTED
                }
                CheckState.INDETERMINATE -> {
                    triStateCheckBox.isSelected = false
                    triStateCheckBox.triState = CheckState.INDETERMINATE
                }
            }
            panel.removeAll()
            panel.add(triStateCheckBox, BorderLayout.WEST)
            panel.add(defaultComponent, BorderLayout.CENTER)
            return panel
        }
        return defaultComponent
    }
}

// 5. 辅助方法：构建一个测试用的三态树
fun createTriStateTree(): JTree {
    val root = TriStateTreeNode("根节点")
    val child1 = TriStateTreeNode("子节点 1")
    val child2 = TriStateTreeNode("子节点 2")
    val child3 = TriStateTreeNode("子节点 3")
    root.add(child1)
    root.add(child2)
    root.add(child3)
    // 为子节点 2 添加两个孙节点
    val subChild1 = TriStateTreeNode("孙节点 1")
    val subChild2 = TriStateTreeNode("孙节点 2")
    child2.add(subChild1)
    child2.add(subChild2)
    // 默认设置整个树为选中状态
    root.state = CheckState.SELECTED

    val treeModel = DefaultTreeModel(root)
    val tree = JTree(treeModel)
    tree.isRootVisible = true
    tree.cellRenderer = TriStateCheckboxTreeCellRenderer()

    // 添加鼠标监听，点击左侧区域时切换节点状态
    tree.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            val path: TreePath = tree.getPathForLocation(e.x, e.y) ?: return
            val node = path.lastPathComponent as? TriStateTreeNode ?: return
            val row = tree.getRowForLocation(e.x, e.y)
            val bounds = tree.getRowBounds(row)
            // 假设复选框区域在节点文本左侧 20 像素内
            if (e.x < bounds.x + 20) {
                node.toggleState()
                tree.repaint()
                e.consume()
            }
        }
    })
    return tree
}

// 6. 定义一个对话框，展示三态复选框树
class TriStateTreeDialog : DialogWrapper(true) {
    init {
        init()
        title = "三态复选框树演示"
    }

    override fun createCenterPanel(): JComponent? {
        val panel = JPanel(BorderLayout())
        panel.add(JScrollPane(createTriStateTree()), BorderLayout.CENTER)
        panel.preferredSize = Dimension(300, 400)
        return panel
    }
}

// 7. Tools Action：调用时弹出对话框展示三态复选框树
class ShowTriStateTreeAction : AnAction("Show Tri-State Tree") {
    override fun actionPerformed(e: AnActionEvent) {
        TriStateTreeDialog().showAndGet()
    }
}
