// core/src/main/kotlin/com/github/zjh7890/gpttools/toolWindow/treePanel/DependenciesTreePanel.kt

package com.github.zjh7890.gpttools.toolWindow.treePanel

import com.github.zjh7890.gpttools.services.*
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
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class DependenciesTreePanel(val project: Project) : JPanel() {

    // 注意：这里把 root 也改为 TriStateTreeNode
    val root = TriStateTreeNode()
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
        tree.cellRenderer = TriStateCheckboxTreeCellRenderer()  // 渲染带 checkbox 的

        val actionGroup = DefaultActionGroup().apply {
            add(RemoveSelectedNodesAction(this@DependenciesTreePanel))
            // 其他 Action ...
        }

        val actionManager = ActionManager.getInstance()
        val toolbar = actionManager.createActionToolbar(
            "ChatFileTreeToolbar",
            actionGroup,
            true
        )

        add(toolbar.component, BorderLayout.NORTH)
        val scrollPane = JScrollPane(tree)

        this.scrollPane = scrollPane
        this.emptyPanel = emptyPanel

        add(scrollPane, BorderLayout.CENTER)

        tree.expandPath(TreePath(root.path))
        tree.border = BorderFactory.createEmptyBorder()

        // 点击事件：1) 勾选/取消勾选 2) 双击打开
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val path = tree.getPathForLocation(e.x, e.y)
                if (path != null) {
                    val node = path.lastPathComponent as? TriStateTreeNode
                    if (node != null) {
                        val row = tree.getRowForLocation(e.x, e.y)
                        val bounds = tree.getRowBounds(row)
                        // 如果点击在 checkbox 的前面区域，则切换勾选状态
                        if (e.x < bounds.x + 20) {
                            node.toggleState()
                            tree.repaint()
                            e.consume()
                            return
                        }
                    }
                }

                // 如果是双击，则执行打开文件 / 跳转到方法的逻辑
                if (e.clickCount == 2) {
                    val node = tree.selectionPath?.lastPathComponent as? TriStateTreeNode
                    val userObject = node?.userObject
                    when (userObject) {
                        is ProjectClass -> {
                            val psiClass = userObject.psiClass
                            val virtualFile = psiClass.containingFile?.virtualFile
                            if (virtualFile != null) {
                                FileEditorManager.getInstance(project).openFile(virtualFile, true)
                            }
                        }
                        is ProjectMethod -> {
                            val psiMethod = userObject.psiMethod
                            val virtualFile = psiMethod.containingFile?.virtualFile
                            if (virtualFile != null) {
                                val editors = FileEditorManager.getInstance(project).openFile(virtualFile, true)
                                if (editors.isNotEmpty()) {
                                    // 若需要定位到方法，可以自行判断 editor 是否是文本编辑器
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

        // 显示树面板
        remove(emptyPanel)
        add(scrollPane, BorderLayout.CENTER)
        root.removeAllChildren()

        // 设置 root 的 userObject 和 state
        root.userObject = appFileTree
        root.state = appFileTree.state

        addedDependencies.clear()

        // ------------------ 构建所有层级节点 ------------------
        appFileTree.projectFileTrees.forEach { projectFileTree ->
            // 1) project 节点
            val projectNode = TriStateTreeNode().apply {
                userObject = projectFileTree
                state = projectFileTree.state
            }

            // ------------- (A) Local Packages -------------
            projectFileTree.localPackages.forEach { packageDependency ->
                val packageNode = TriStateTreeNode().apply {
                    userObject = packageDependency
                    state = packageDependency.state
                }

                packageDependency.files.forEach { file ->
                    val classes = file.getCurrentClasses()

                    // 如果只有一个类且与文件名重名（去掉.kt/.java后缀），直接展示类节点
                    if (classes.size == 1 && classes[0].psiClass.name == file.virtualFile.nameWithoutExtension) {
                        val pClass = classes[0]
                        val classNode = TriStateTreeNode().apply {
                            userObject = pClass
                            state = pClass.state
                        }
                        addedDependencies.add(pClass.psiClass)

                        // 方法节点
                        pClass.getCurrentMethods().forEach { pm ->
                            val methodNode = TriStateTreeNode().apply {
                                userObject = pm
                                state = pm.state
                            }
                            classNode.add(methodNode)
                        }
                        packageNode.add(classNode)
                    } else {
                        // 原有的处理逻辑：创建文件节点，然后添加类节点
                        val fileNode = TriStateTreeNode().apply {
                            userObject = file
                            state = file.state
                        }

                        // 类节点
                        classes.forEach { pClass ->
                            val classNode = TriStateTreeNode().apply {
                                userObject = pClass
                                state = pClass.state
                            }
                            addedDependencies.add(pClass.psiClass)

                            // 方法节点
                            pClass.getCurrentMethods().forEach { pm ->
                                val methodNode = TriStateTreeNode().apply {
                                    userObject = pm
                                    state = pm.state
                                }
                                classNode.add(methodNode)
                            }
                            fileNode.add(classNode)
                        }

                        packageNode.add(fileNode)
                    }
                }
                projectNode.add(packageNode)
            }

            // ------------- (B) Maven Dependencies -------------
            if (projectFileTree.mavenDependencies.isNotEmpty()) {
                val mavenRootNode = TriStateTreeNode().apply {
                    userObject = "Maven Dependencies"
                }

                projectFileTree.mavenDependencies.forEach { mavenDep ->
                    val mavenDepNode = TriStateTreeNode().apply {
                        userObject = mavenDep
                        state = mavenDep.state
                    }

                    // 使用扁平路径构建结构
                    mavenDep.packages.forEach { packageDependency ->
                        val packageNode = TriStateTreeNode().apply {
                            userObject = packageDependency
                            state = packageDependency.state
                        }

                        packageDependency.files.forEach { file ->
                            val classes = file.getCurrentClasses()

                            // 如果只有一个类且与文件名重名（去掉.class后缀），直接展示类节点
                            if (classes.size == 1 && classes[0].psiClass.name == file.virtualFile.nameWithoutExtension) {
                                val pClass = classes[0]
                                val classNode = TriStateTreeNode().apply {
                                    userObject = pClass
                                    state = pClass.state
                                }
                                addedDependencies.add(pClass.psiClass)

                                // 方法节点
                                pClass.getCurrentMethods().forEach { pm ->
                                    val methodNode = TriStateTreeNode().apply {
                                        userObject = pm
                                        state = pm.state
                                    }
                                    classNode.add(methodNode)
                                }
                                packageNode.add(classNode)
                            } else {
                                // 原有的处理逻辑：创建文件节点，然后添加类节点
                                val fileNode = TriStateTreeNode().apply {
                                    userObject = file
                                    state = file.state
                                }

                                // 类节点
                                classes.forEach { pClass ->
                                    val classNode = TriStateTreeNode().apply {
                                        userObject = pClass
                                        state = pClass.state
                                    }
                                    addedDependencies.add(pClass.psiClass)

                                    // 方法节点
                                    pClass.getCurrentMethods().forEach { pm ->
                                        val methodNode = TriStateTreeNode().apply {
                                            userObject = pm
                                            state = pm.state
                                        }
                                        classNode.add(methodNode)
                                    }
                                    fileNode.add(classNode)
                                }

                                packageNode.add(fileNode)
                            }
                        }

                        mavenDepNode.add(packageNode)
                    }

                    mavenRootNode.add(mavenDepNode)
                }

                projectNode.add(mavenRootNode)
            }

            root.add(projectNode)
        }

        // 刷新树并展开
        (tree.model as DefaultTreeModel).reload(root)
        expandDefaultNodes()
        revalidate()
        repaint()
    }

    fun expandDefaultNodes() {
        tree.expandPath(TreePath(root.path))
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
            if (appFileTree.projectFileTrees.isEmpty()) {
                return "- Related Project Map\n  (no dependencies)\n"
            }
            val sb = StringBuilder()
            sb.append("- Related Project Map\n")
            appFileTree.projectFileTrees.forEach { projectFileTree ->
                sb.append("  - Project: ${projectFileTree.project.name}\n")

                // 1) Local Packages
                projectFileTree.localPackages.forEach { packageDependency ->
                    sb.append("    - Package: ${packageDependency.packageName}\n")
                    packageDependency.files.forEach { file ->
                        val usedClasses = file.classes
                        usedClasses.forEach { projectClass ->
                            val className = projectClass.psiClass.name
                            sb.append("      - Class: $className\n")
                            projectClass.getCurrentMethods().forEach { projectMethod ->
                                val methodName = projectMethod.psiMethod.name
                                sb.append("        - Method: $methodName\n")
                            }
                        }
                    }
                }

                // 2) Maven Dependencies
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

// 1. 定义三态状态枚举
enum class CheckState {
    SELECTED, UNSELECTED, INDETERMINATE
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

// 2. 定义 TriStateTreeNode，支持三态并同步到用户对象
class TriStateTreeNode(userObject: Any? = null) : DefaultMutableTreeNode(userObject) {
    var state: CheckState = CheckState.SELECTED

    fun updateState(value: CheckState) {
        if (state != value) {
            state = value
            syncToUserObject()
            updateChildrenState(value)
            updateParentState()
        }
    }

    // 原来的切换逻辑（旧版可能用 isChecked 进行布尔反转）
    fun toggleState() {
        val newState = when (state) {
            CheckState.INDETERMINATE -> CheckState.SELECTED
            CheckState.SELECTED -> CheckState.UNSELECTED
            CheckState.UNSELECTED -> CheckState.SELECTED
        }
        updateState(newState)
    }

    // 同步状态到 userObject，原来是设置 selected 属性
    private fun syncToUserObject() {
        when (val obj = userObject) {
            is AppFileTree -> obj.state = state
            is ProjectFileTree -> obj.state = state
            is ModuleDependency -> obj.state = state
            is MavenDependency -> obj.state = state
            is PackageDependency -> obj.state = state
            is ProjectFile -> obj.state = state
            is ProjectClass -> obj.state = state
            is ProjectMethod -> obj.state = state
        }
    }

    // 向下同步：若当前节点不是 INDETERMINATE，则所有子节点跟随当前状态
    private fun updateChildrenState(value: CheckState) {
        if (state == CheckState.INDETERMINATE) return
        for (i in 0 until childCount) {
            val child = getChildAt(i) as? TriStateTreeNode ?: continue
            child.state = value
            child.updateChildrenState(value)
        }
    }

    // 向上传递：根据所有兄弟节点状态确定父节点状态
    private fun updateParentState() {
        val parentNode = parent as? TriStateTreeNode ?: return
        var selectedCount = 0
        var unselectedCount = 0
        for (i in 0 until parentNode.childCount) {
            val child = parentNode.getChildAt(i) as? TriStateTreeNode ?: continue
            when (child.state) {
                CheckState.SELECTED -> selectedCount++
                CheckState.UNSELECTED -> unselectedCount++
                CheckState.INDETERMINATE -> { }
            }
        }
        parentNode.state = when {
            selectedCount == parentNode.childCount -> CheckState.SELECTED
            unselectedCount == parentNode.childCount -> CheckState.UNSELECTED
            else -> CheckState.INDETERMINATE
        }
        parentNode.updateParentState()
    }

    override fun toString(): String {
        return userObject?.toString() ?: ""
    }
}

class TriStateCheckBox : JCheckBox() {
    var triState: CheckState = CheckState.UNSELECTED
        set(value) {
            field = value
            repaint()
        }

    override fun paintComponent(g: Graphics) {
        // 绘制 JCheckBox 的默认效果
        super.paintComponent(g)
        // 当状态为 INDETERMINATE 时，在复选框内部绘制一条横条
        if (triState == CheckState.INDETERMINATE) {
            val iconSize = 16
            val x = 2
            val y = (height - 3) / 2
            g.color = Color.BLACK
            g.fillRect(x, y, iconSize - 4, 3)
        }
    }
}

/**
 * 自定义渲染器，既实现三态复选框显示，又保留原来根据 userObject 类型设置图标和文本的逻辑。
 */
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
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        val defaultComponent = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        // 如果节点是我们自定义的 TriStateTreeNode，则进行处理
        if (value is TriStateTreeNode) {
            // 根据当前节点的状态更新复选框
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

            // 原有逻辑：根据 userObject 类型设置默认组件的 icon 和 text
            when (val userObj = value.userObject) {
                is ProjectClass -> {
                    icon = if (userObj.isAtomicClass)
                        com.intellij.icons.AllIcons.Nodes.Record
                    else
                        com.intellij.icons.AllIcons.Nodes.Class
                    text = userObj.psiClass.name
                }
                is ProjectMethod -> {
                    icon = com.intellij.icons.AllIcons.Nodes.Method
                    text = userObj.psiMethod.name
                }
                is ProjectFile -> {
                    icon = com.intellij.icons.AllIcons.FileTypes.Any_type
                    text = userObj.virtualFile.name
                }
                is PackageDependency -> {
                    icon = com.intellij.icons.AllIcons.Nodes.Package
                    text = userObj.packageName
                }
                is ModuleDependency -> {
                    icon = com.intellij.icons.AllIcons.Nodes.Module
                    text = userObj.moduleName
                }
                is MavenDependency -> {
                    icon = com.intellij.icons.AllIcons.Nodes.PpLib
                    text = "${userObj.groupId}:${userObj.artifactId}:${userObj.version}"
                }
                is ProjectFileTree -> {
                    icon = com.intellij.icons.AllIcons.Nodes.Project
                    text = userObj.project.name
                }
                is AppFileTree -> {
                    icon = com.intellij.icons.AllIcons.Nodes.Folder
                    text = "Dependencies"
                }
                else -> {
                    text = value.userObject.toString()
                }
            }

            // 用 panel 拼装：左侧放复选框，右侧放默认组件（显示图标和文本）
            panel.removeAll()
            panel.add(triStateCheckBox, BorderLayout.WEST)
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
    com.intellij.icons.AllIcons.General.Remove
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = dependenciesPanel.project
        val tree = dependenciesPanel.tree

        val selectionPaths = tree.selectionPaths ?: return
        val selectedObjects = mutableListOf<Any>()

        for (path in selectionPaths) {
            val node = path.lastPathComponent as? TriStateTreeNode ?: continue
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
