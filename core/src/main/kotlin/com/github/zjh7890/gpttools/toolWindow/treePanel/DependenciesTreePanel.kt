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
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class DependenciesTreePanel(val project: Project) : JPanel() {

    // 注意：这里把 root 也改为 CheckboxTreeNode
    val root = CheckboxTreeNode()
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
        tree.cellRenderer = CheckboxTreeCellRenderer()  // 渲染带 checkbox 的

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
                    val node = path.lastPathComponent as? CheckboxTreeNode
                    if (node != null) {
                        val row = tree.getRowForLocation(e.x, e.y)
                        val bounds = tree.getRowBounds(row)
                        // 如果点击在 checkbox 的前面区域，则切换勾选状态
                        if (e.x < bounds.x + 20) {
                            node.isChecked = !node.isChecked
                            tree.repaint()
                            e.consume()
                            return
                        }
                    }
                }

                // 如果是双击，则执行打开文件 / 跳转到方法的逻辑
                if (e.clickCount == 2) {
                    val node = tree.selectionPath?.lastPathComponent as? CheckboxTreeNode
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

    /**
     * 更新依赖树
     */
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

        // 设置 root 的 userObject 和 checked
        root.userObject = appFileTree
        root.isChecked = appFileTree.selected  // 与 AppFileTree 的 selected 同步

        addedDependencies.clear()

        // ------------------ 构建所有层级节点 ------------------
        appFileTree.projectFileTrees.forEach { projectFileTree ->
            // 1) project 节点
            val projectNode = CheckboxTreeNode().apply {
                userObject = projectFileTree
                isChecked = projectFileTree.selected
            }

            // ------------- (A) modules -------------
            projectFileTree.modules.forEach { moduleDependency ->
                val moduleNode = CheckboxTreeNode().apply {
                    userObject = moduleDependency
                    isChecked = moduleDependency.selected
                }

                // 遍历 packages
                moduleDependency.packages.forEach { packageDependency ->
                    val packageNode = CheckboxTreeNode().apply {
                        userObject = packageDependency
                        isChecked = packageDependency.selected
                    }

                    packageDependency.files.forEach { file ->
                        // 文件节点
                        val fileNode = CheckboxTreeNode().apply {
                            userObject = file
                            isChecked = file.selected
                        }

                        // 类节点
                        file.getCurrentClasses().forEach { pClass ->
                            val classNode = CheckboxTreeNode().apply {
                                userObject = pClass
                                isChecked = pClass.selected
                            }
                            // 记录
                            addedDependencies.add(pClass.psiClass)

                            // 方法节点
                            pClass.getCurrentMethods().forEach { pm ->
                                val methodNode = CheckboxTreeNode().apply {
                                    userObject = pm
                                    isChecked = pm.selected
                                }
                                classNode.add(methodNode)
                            }
                            fileNode.add(classNode)
                        }

                        // 如果文件节点有内容，就加进去
                        if (fileNode.childCount > 0) {
                            packageNode.add(fileNode)
                        }
                    }

                    // packageNode 有内容就加进去
                    if (packageNode.childCount > 0) {
                        moduleNode.add(packageNode)
                    }
                }

                // moduleNode 有内容就加进 projectNode
                if (moduleNode.childCount > 0) {
                    projectNode.add(moduleNode)
                }
            }

            // ------------- (B) Maven Dependencies -------------
            val mavenRootNode = CheckboxTreeNode()

            projectFileTree.mavenDependencies.forEach { mavenDep ->
                val mavenDepNode = CheckboxTreeNode().apply {
                    userObject = mavenDep
                    isChecked = mavenDep.selected
                }

                mavenDep.packages.forEach { packageDependency ->
                    val packageNode = CheckboxTreeNode().apply {
                        userObject = packageDependency
                        isChecked = packageDependency.selected
                    }

                    packageDependency.files.forEach { file ->
                        val fileNode = CheckboxTreeNode().apply {
                            userObject = file
                            isChecked = file.selected
                        }

                        file.getCurrentClasses().forEach { pClass ->
                            val classNode = CheckboxTreeNode().apply {
                                userObject = pClass
                                isChecked = pClass.selected
                            }
                            addedDependencies.add(pClass.psiClass)

                            pClass.getCurrentMethods().forEach { pm ->
                                val methodNode = CheckboxTreeNode().apply {
                                    userObject = pm
                                    isChecked = pm.selected
                                }
                                classNode.add(methodNode)
                            }
                            fileNode.add(classNode)
                        }

                        if (fileNode.childCount > 0) {
                            packageNode.add(fileNode)
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
                return "- Dependencies\n  (no dependencies)\n"
            }
            val sb = StringBuilder()
            sb.append("- Dependencies\n")
            appFileTree.projectFileTrees.forEach { projectFileTree ->
                sb.append("  - Project: ${projectFileTree.project.name}\n")
                projectFileTree.modules.forEach { moduleDependency ->
                    sb.append("    - Module: ${moduleDependency.moduleName}\n")
                    moduleDependency.packages.forEach { packageDependency ->
                        sb.append("      - Package: ${packageDependency.packageName}\n")
                        packageDependency.files.forEach { file ->
                            val usedClasses = file.classes
                            usedClasses.forEach { projectClass ->
                                val className = projectClass.psiClass.name
                                sb.append("        - Class: $className\n")
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

class CheckboxTreeNode() : DefaultMutableTreeNode() {
    var isChecked: Boolean
        get() {
            return when (val obj = userObject) {
                is AppFileTree -> obj.selected
                is ProjectFileTree -> obj.selected
                is ModuleDependency -> obj.selected
                is MavenDependency -> obj.selected
                is PackageDependency -> obj.selected
                is ProjectFile -> obj.selected
                is ProjectClass -> obj.selected
                is ProjectMethod -> obj.selected
                // 如果只是普通字符串之类的，就忽略
                else -> {
                    false
                }
            }
        }
        set(value) {
            if (isChecked != value) {
                // 同步给 userObject (如果它具有 selected 字段)
                syncSelectedToUserObject(value)
                // 更新所有子节点
                updateChildrenState()
                // 更新父节点
                updateParentState()
            }
        }

    /**
     * 将当前节点的 isChecked -> userObject.selected
     */
    private fun syncSelectedToUserObject(value: Boolean) {
        when (val obj = userObject) {
            is AppFileTree -> obj.selected = value
            is ProjectFileTree -> obj.selected = value
            is ModuleDependency -> obj.selected = value
            is MavenDependency -> obj.selected = value
            is PackageDependency -> obj.selected = value
            is ProjectFile -> obj.selected = value
            is ProjectClass -> obj.selected = value
            is ProjectMethod -> obj.selected = value
            // 如果只是普通字符串之类的，就忽略
        }
    }

    private fun updateChildrenState() {
        children().asSequence().forEach { child ->
            if (child is CheckboxTreeNode) {
                // 这里直接设置内部字段，避免继续递归调用 setter
                child.isChecked = isChecked
                // 也要同步给 userObject
                child.syncSelectedToUserObject(isChecked)
                // 递归更新它的孩子
                child.updateChildrenState()
            }
        }
    }

    private fun updateParentState() {
        val parent = parent as? CheckboxTreeNode ?: return
        // 如果所有兄弟节点都已选中，则父节点也选中，否则不选
        val allChildrenChecked = parent.children().asSequence().all {
            (it as? CheckboxTreeNode)?.isChecked == true
        }
        if (parent.isChecked != allChildrenChecked) {
            parent.isChecked = allChildrenChecked
            parent.syncSelectedToUserObject(allChildrenChecked)
            parent.updateParentState()
        }
    }
}

// -----------------------------------------------------------------------
// CheckboxTreeCellRenderer：保持不变或做简单个性化处理
// -----------------------------------------------------------------------
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
            // 将节点的 isChecked 显示到 checkbox
            checkbox.isSelected = value.isChecked

            // 根据不同的 userObject 类型设置不同的图标和文本
            when (val userObj = value.userObject) {
                is ProjectClass -> {
                    icon = if (userObj.isAtomicClass) {
                        com.intellij.icons.AllIcons.Nodes.Record
                    } else {
                        com.intellij.icons.AllIcons.Nodes.Class
                    }
                    text = userObj.psiClass.name
                }
                is ProjectMethod -> {
                    icon = com.intellij.icons.AllIcons.Nodes.Method
                    text = userObj.psiMethod.name
                }
                is ProjectFile -> {
                    icon = com.intellij.icons.AllIcons.FileTypes.Any_type
                    text = userObj.filePath
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
                    // 对于其他类型，使用默认的文本显示
                    text = value.userObject.toString()
                }
            }

            // 用 panel 拼装
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
    com.intellij.icons.AllIcons.General.Remove
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = dependenciesPanel.project
        val tree = dependenciesPanel.tree

        val selectionPaths = tree.selectionPaths ?: return
        val selectedObjects = mutableListOf<Any>()

        for (path in selectionPaths) {
            val node = path.lastPathComponent as? CheckboxTreeNode ?: continue
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
