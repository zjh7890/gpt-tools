// core/src/main/kotlin/com/github/zjh7890/gpttools/toolWindow/treePanel/DependenciesTreePanel.kt

package com.github.zjh7890.gpttools.toolWindow.treePanel

import com.github.zjh7890.gpttools.services.AppFileTree
import com.github.zjh7890.gpttools.services.ProjectClass
import com.github.zjh7890.gpttools.services.ProjectMethod
import com.github.zjh7890.gpttools.services.SessionManager
import com.github.zjh7890.gpttools.utils.PsiUtils
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class DependenciesTreePanel(val project: Project) : JPanel() {
    val root = DefaultMutableTreeNode("Dependencies")
    val tree = Tree(root)
    private val addedDependencies = mutableSetOf<PsiClass>()
    private var scrollPane: JScrollPane
    private var emptyPanel: JPanel

    init {
        layout = BorderLayout()
        border = BorderFactory.createEmptyBorder()

        // 创建空文件面板
        val emptyPanel = JPanel(BorderLayout())
        val emptyLabel = JLabel("No files, right click file to add", SwingConstants.CENTER).apply {
            font = font.deriveFont(14f)
            foreground = Color.GRAY
        }
        emptyPanel.add(emptyLabel, BorderLayout.CENTER)

        // 初始化树相关组件
        tree.isRootVisible = true
        tree.showsRootHandles = true
        tree.cellRenderer = CheckboxTreeCellRenderer()

        val actionGroup = DefaultActionGroup().apply {
            add(RemoveSelectedNodesAction(this@DependenciesTreePanel))
            // 其他 Action ...
        }

        // 创建 toolbar
        val actionManager = ActionManager.getInstance()
        val toolbar = actionManager.createActionToolbar(
            "ChatFileTreeToolbar",
            actionGroup,
            true
        )

        // 添加 toolbar 到面板顶部
        add(toolbar.component, BorderLayout.NORTH)
        val scrollPane = JScrollPane(tree)

        // 默认显示树面板
        add(scrollPane, BorderLayout.CENTER)

        // 保存引用以便后续切换
        this.scrollPane = scrollPane
        this.emptyPanel = emptyPanel

        tree.expandPath(TreePath(root.path))
        tree.border = BorderFactory.createEmptyBorder()
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val path = tree.getPathForLocation(e.x, e.y)
                if (path != null) {
                    val node = path.lastPathComponent as? CheckboxTreeNode
                    if (node != null) {
                        val row = tree.getRowForLocation(e.x, e.y)
                        val bounds = tree.getRowBounds(row)
                        if (e.x < bounds.x + 20) {
                            node.isChecked = !node.isChecked
                            tree.repaint()
                            e.consume()
                            return
                        }
                    }
                }

                if (e.clickCount == 2) {
                    val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
                    val userObject = node?.userObject

                    when (userObject) {
                        // 处理 ProjectClass 类型
                        is ProjectClass -> {
                            val psiClass = userObject.psiClass
                            val virtualFile = psiClass.containingFile?.virtualFile
                            if (virtualFile != null) {
                                FileEditorManager.getInstance(project).openFile(virtualFile, true)
                            }
                        }
                        // 处理 ProjectMethod 类型
                        is ProjectMethod -> {
                            val psiMethod = userObject.psiMethod
                            val virtualFile = psiMethod.containingFile?.virtualFile
                            if (virtualFile != null) {
                                val editor = FileEditorManager.getInstance(project).openFile(virtualFile, true)[0]
                                // 转换为 Editor 类型
                                if (editor is com.intellij.openapi.editor.Editor) {
                                    editor.caretModel.moveToOffset(psiMethod.textOffset)
                                }
                            }
                        }
                    }
                    e.consume()
                }
            }
        })
    }

    fun updateDependencies(appFileTree: AppFileTree) {
        // 1. 若没有任何 projectFileTrees，视为没有依赖
        if (appFileTree.projectFileTrees.isEmpty()) {
            remove(scrollPane)
            add(emptyPanel, BorderLayout.CENTER)
            revalidate()
            repaint()
            return
        }

        // 有依赖：确保显示树面板
        remove(emptyPanel)
        add(scrollPane, BorderLayout.CENTER)
        root.removeAllChildren()

        // 每次更新前清空记录
        addedDependencies.clear()

        // 遍历每个 ProjectFileTree，创建一个 projectNode
        appFileTree.projectFileTrees.forEach { projectFileTree ->
            val projectNode = DefaultMutableTreeNode(projectFileTree.project.name)

            // ---------- 1) 处理 modules ----------
            projectFileTree.modules.forEach { moduleDependency ->
                val moduleNode = DefaultMutableTreeNode(moduleDependency.moduleName)

                // 遍历 packages
                moduleDependency.packages.forEach { packageDependency ->
                    val packageNode = DefaultMutableTreeNode(packageDependency.packageName)
                    packageDependency.files.forEach { file ->
                        // 对应一个文件里的一些类
                        val usedClasses = file.classes

                        usedClasses.forEach { pClass ->
                            val classNode = CheckboxTreeNode("").apply {
                                userObject = pClass
                            }
                            // 记录
                            addedDependencies.add(pClass.psiClass)

                            // 方法
                            pClass.getCurrentMethods().forEach { pm ->
                                val methodNode = CheckboxTreeNode("").apply {
                                    userObject = pm
                                }
                                classNode.add(methodNode)
                            }
                            packageNode.add(classNode)
                        }
                    }
                    // 把 packageNode 加进 moduleNode
                    if (packageNode.childCount > 0) {
                        moduleNode.add(packageNode)
                    }
                }

                // 若该 moduleNode 有内容就加进 projectNode
                if (moduleNode.childCount > 0) {
                    projectNode.add(moduleNode)
                }
            }

            // ---------- 2) 处理 mavenDependencies ----------
            val mavenRootNode = DefaultMutableTreeNode("Maven Dependencies")

            projectFileTree.mavenDependencies.forEach { mavenDep ->
                val mavenDepNode = DefaultMutableTreeNode("${mavenDep.groupId}:${mavenDep.artifactId}:${mavenDep.version}")

                mavenDep.packages.forEach { packageDependency ->
                    val packageNode = DefaultMutableTreeNode(packageDependency.packageName)
                    packageDependency.files.forEach { file ->
                        val usedClasses = file.classes

                        usedClasses.forEach { pClass ->
                            val classNode = CheckboxTreeNode("").apply {
                                userObject = pClass
                            }
                            addedDependencies.add(pClass.psiClass)

                            pClass.getCurrentMethods().forEach { pm ->
                                val methodNode = CheckboxTreeNode("").apply {
                                    userObject = pm
                                }
                                classNode.add(methodNode)
                            }
                            packageNode.add(classNode)
                        }
                    }
                    if (packageNode.childCount > 0) {
                        mavenDepNode.add(packageNode)
                    }
                }

                if (mavenDepNode.childCount > 0) {
                    mavenRootNode.add(mavenDepNode)
                }
            }

            // 若 mavenRootNode 有内容就加进 projectNode
            if (mavenRootNode.childCount > 0) {
                projectNode.add(mavenRootNode)
            }

            // 最后，把该 projectNode 放到根节点
            root.add(projectNode)
        }

        // 刷新并展开
        (tree.model as DefaultTreeModel).reload(root)
        expandDefaultNodes()
        revalidate()
        repaint()
    }

    fun expandDefaultNodes() {
        // 展开根节点
        tree.expandPath(TreePath(root.path))

        // 展开 Dependencies 下的所有节点
        for (i in 0 until root.childCount) {
            val node = root.getChildAt(i) as DefaultMutableTreeNode
            val path = TreePath(node.path)
            tree.expandPath(path)
            expandNodeRecursively(node)
        }
    }

    private fun expandNodeRecursively(node: DefaultMutableTreeNode) {
        if (node.childCount > 0) {
            val path = getPathToNode(node)
            tree.expandPath(TreePath(path.toTypedArray()))

            for (i in 0 until node.childCount) {
                expandNodeRecursively(node.getChildAt(i) as DefaultMutableTreeNode)
            }
        }
    }

    private fun getPathToNode(node: DefaultMutableTreeNode): List<DefaultMutableTreeNode> {
        val path = mutableListOf<DefaultMutableTreeNode>()
        var current: DefaultMutableTreeNode? = node

        while (current != null) {
            path.add(0, current)
            current = current.parent as? DefaultMutableTreeNode
        }

        return path
    }

    companion object {
        fun toMarkdownString(appFileTree: AppFileTree): String {
            // 若无依赖可做特殊处理，这里直接返回空。
            if (appFileTree.projectFileTrees.isEmpty()) {
                return "- Dependencies\n  (no dependencies)\n"
            }

            val sb = StringBuilder()
            sb.append("- Dependencies\n")

            appFileTree.projectFileTrees.forEach { projectFileTree ->
                sb.append("  - Project: ${projectFileTree.project.name}\n")

                // ------------------- 1) Modules -------------------
                projectFileTree.modules.forEach { moduleDependency ->
                    sb.append("    - Module: ${moduleDependency.moduleName}\n")
                    moduleDependency.packages.forEach { packageDependency ->
                        sb.append("      - Package: ${packageDependency.packageName}\n")

                        packageDependency.files.forEach { file ->
                            val usedClasses = file.classes
                            usedClasses.forEach { projectClass ->
                                val className = projectClass.psiClass.name
                                sb.append("        - Class: $className\n")

                                // 方法
                                projectClass.getCurrentMethods().forEach { projectMethod ->
                                    val methodName = projectMethod.psiMethod.name
                                    sb.append("          - Method: $methodName\n")
                                }
                            }
                        }
                    }
                }

                // ------------------- 2) Maven Dependencies -------------------
                // 如果有 mavenDependencies，就统一放在 "Maven Dependencies" 下
                if (projectFileTree.mavenDependencies.isNotEmpty()) {
                    sb.append("    - Maven Dependencies\n")
                    projectFileTree.mavenDependencies.forEach { mavenDep ->
                        val gav = "${mavenDep.groupId}:${mavenDep.artifactId}:${mavenDep.version}"
                        sb.append("      - $gav\n")

                        mavenDep.packages.forEach { packageDependency ->
                            sb.append("        - Package: ${packageDependency.packageName}\n")

                            packageDependency.files.forEach { file ->
                                val usedClasses = file.classes
                                usedClasses.forEach { projectClass ->
                                    val className = projectClass.psiClass.name
                                    sb.append("          - Class: $className\n")

                                    projectClass.getCurrentMethods().forEach { projectMethod ->
                                        val methodName = projectMethod.psiMethod.name
                                        sb.append("            - Method: $methodName\n")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return sb.toString()
        }
    }
}

data class MavenDependencyId(
    val groupId: String,
    val artifactId: String,
    val version: String
) {
    override fun toString(): String = "$groupId:$artifactId:$version"
}

class ClassDependencyInfo(var isAtomicClass: Boolean = false) {
    val usedMethods = mutableSetOf<PsiMethod>()
    val usedFields = mutableSetOf<PsiField>()

    fun markMethodUsed(method: PsiMethod) {
        usedMethods.add(method)
    }

    fun markFieldUsed(field: PsiField) {
        usedFields.add(field)
    }
}

class CheckboxTreeNode(val text: String) : DefaultMutableTreeNode(text) {
    private var _isChecked = false
    var isChecked: Boolean
        get() = _isChecked
        set(value) {
            if (_isChecked != value) {
                _isChecked = value
                // 更新所有子节点状态
                updateChildrenState()
                // 更新父节点状态
                updateParentState()
            }
        }

    private fun updateChildrenState() {
        children().asSequence().forEach { child ->
            if (child is CheckboxTreeNode) {
                child._isChecked = _isChecked  // 直接设置内部字段，避免触发 setter
            }
        }
    }

    private fun updateParentState() {
        val parent = parent as? CheckboxTreeNode ?: return
        val newState = parent.children().asSequence().all {
            (it as? CheckboxTreeNode)?.isChecked == true
        }
        if (parent._isChecked != newState) {
            parent._isChecked = newState  // 直接设置内部字段，避免触发 setter
            parent.updateParentState()  // 继续向上更新父节点状态
        }
    }
}

class CheckboxTreeCellRenderer : DefaultTreeCellRenderer() {
    private val checkbox = JCheckBox()
    private val panel = JPanel(BorderLayout())

    init {
        panel.isOpaque = false
        checkbox.isOpaque = false
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
        val defaultComponent = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)

        if (value is CheckboxTreeNode) {
            // 设置 checkbox 状态
            checkbox.isSelected = value.isChecked

            val userObj = value.userObject
            if (userObj is ProjectClass) {
                // 判断是否是数据类
                if (userObj.isAtomicClass) {
                    this.icon = com.intellij.icons.AllIcons.Nodes.Record
                } else {
                    this.icon = com.intellij.icons.AllIcons.Nodes.Class
                }
                this.text = userObj.psiClass.name
            }
            else if (userObj is ProjectMethod) {
                this.icon = com.intellij.icons.AllIcons.Nodes.Method
                this.text = userObj.psiMethod.name
            }

            // 使用面板组合 checkbox 和默认渲染组件
            panel.removeAll()
            panel.add(checkbox, BorderLayout.WEST)
            panel.add(defaultComponent, BorderLayout.CENTER)
            return panel
        }

        return defaultComponent
    }
}

class RemoveSelectedNodesAction(
    private val dependenciesPanel: DependenciesTreePanel
) : AnAction(
    "Remove Selected", 
    "Remove the selected nodes from session", 
    com.intellij.icons.AllIcons.General.Remove  // 添加删除图标
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = dependenciesPanel.project
        val tree = dependenciesPanel.tree

        // 获取所有选中的 TreePath
        val selectionPaths = tree.selectionPaths ?: return

        // 准备一个列表装“待移除对象”
        val selectedObjects = mutableListOf<Any>()

        // 遍历选中的每一个节点
        for (path in selectionPaths) {
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: continue
            val userObject = node.userObject
            // 根据前面设计，如果 userObject 是 ProjectMethod、ProjectClass、ProjectFile、VirtualFile 等，收集起来
            when (userObject) {
                is ProjectMethod -> selectedObjects.add(userObject)
                is ProjectClass -> selectedObjects.add(userObject)
                // 如果你在构造树节点时是用 ProjectFile 作为 userObject，也可以这样做:
                // is ProjectFile -> selectedObjects.add(userObject.virtualFile)
                // 或者如果直接存了 VirtualFile：
                // is VirtualFile -> selectedObjects.add(userObject)
            }
        }

        // 调用 SessionManager 移除
        val sessionManager = SessionManager.getInstance(project)
        sessionManager.removeSelectedNodesFromCurrentSession(selectedObjects)
    }
}
