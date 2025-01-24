// core/src/main/kotlin/com/github/zjh7890/gpttools/toolWindow/treePanel/DependenciesTreePanel.kt

package com.github.zjh7890.gpttools.toolWindow.treePanel

import com.github.zjh7890.gpttools.services.AppFileTree
import com.github.zjh7890.gpttools.services.ProjectClass
import com.github.zjh7890.gpttools.services.ProjectMethod
import com.github.zjh7890.gpttools.utils.PsiUtils
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
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

class DependenciesTreePanel(private val project: Project) : JPanel() {
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

        // 原先代码中使用的临时缓存或辅助结构
        val moduleMap = mutableMapOf<String, DefaultMutableTreeNode>()
        val mavenDependencies = mutableMapOf<String, MutableList<PsiClass>>()

        // 获取当前 DependenciesTreePanel 对应的 Project 名
        val projectName = project.name
        // 构建最上层的节点
        val projectNode = DefaultMutableTreeNode(projectName)

        // 每次更新前清空记录
        addedDependencies.clear()

        // 2. 遍历 appFileTree 中的每个 ProjectFileTree
        appFileTree.projectFileTrees.forEach { projectFileTree ->
            // 2.1 找到对应的 Project（可跨项目）
            val targetProject = com.intellij.openapi.project.ProjectManager.getInstance()
                .openProjects
                .find { it.name == projectFileTree.projectName }
                ?: return@forEach

            // 2.2 遍历文件
            projectFileTree.files.forEach { projectFile ->
                // 使用 getCurrentClasses() 取回当前有效的 ProjectClass 列表
                val projectClasses = projectFile.getCurrentClasses()

                // 遍历当前文件中的所有“ProjectClass”
                projectClasses.forEach { pClass ->
                    val psiClass = pClass.psiClass

                    // 先判定是否是外部依赖
                    if (!isExternalDependency(psiClass)) {
                        // 处理项目内的依赖
                        val moduleName = getModuleName(psiClass)
                        val moduleNode = moduleMap.getOrPut(moduleName) {
                            DefaultMutableTreeNode(moduleName)
                        }

                        // 解析包名
                        val packageName = psiClass.qualifiedName
                            ?.substringBeforeLast(".")
                            ?.replace('.', '/')
                            ?.substringAfter("src/main/java/")
                            ?.replace('/', '.')
                            ?: "(default package)"

                        val packageNode = findOrCreatePackageNode(moduleNode, packageName)

                        // 创建类节点
                        val classNode = CheckboxTreeNode("").apply {
                            userObject = pClass
                        }
                        packageNode.add(classNode)

                        // 将 psiClass 加入记录
                        addedDependencies.add(psiClass)

                        // 使用 pClass.getCurrentMethods() 来获取本类需要展示的方法
                        val selectedMethods = pClass.getCurrentMethods()

                        // 添加方法级别的子节点
                        selectedMethods.forEach { method ->
                            val methodNode = CheckboxTreeNode("").apply {
                                userObject = method
                            }
                            classNode.add(methodNode)
                        }

                    } else {
                        // 处理外部依赖
                        val mavenInfo = extractMavenInfo(psiClass)
                        if (mavenInfo != null) {
                            // Maven 依赖
                            val key = mavenInfo.toString()
                            mavenDependencies.getOrPut(key) { mutableListOf() }.add(psiClass)
                        }
                    }
                }
            }
        }

        // 3. 将模块节点添加到 projectNode
        moduleMap.entries.sortedBy { it.key }.forEach { (_, node) ->
            if (node.childCount > 0) {
                projectNode.add(node)
            }
        }

        // 4. 将 projectNode 加到 root
        root.add(projectNode)

        // 5. 处理 Maven 依赖
        if (mavenDependencies.isNotEmpty()) {
            val mavenNode = DefaultMutableTreeNode("Maven Dependencies")
            mavenDependencies.entries.sortedBy { it.key }.forEach { (mavenInfo, psiClasses) ->
                val mavenInfoNode = DefaultMutableTreeNode(mavenInfo)

                // 按包路径组织类
                val packageMap = psiClasses.groupBy { cls ->
                    cls.qualifiedName
                        ?.substringBeforeLast(".")
                        ?.ifEmpty { "(default package)" }
                        ?: "(default package)"
                }

                packageMap.entries.sortedBy { it.key }.forEach { (packageName, pkgClsList) ->
                    val packageNode = if (packageName == "(default package)") {
                        mavenInfoNode
                    } else {
                        val pn = DefaultMutableTreeNode(packageName)
                        mavenInfoNode.add(pn)
                        pn
                    }
                    pkgClsList.sortedBy { it.name }.forEach { cls ->
                        packageNode.add(DefaultMutableTreeNode(cls.name))
                    }
                }
                mavenNode.add(mavenInfoNode)
            }
            root.add(mavenNode)
        }

        // 7. 刷新并展开
        (tree.model as DefaultTreeModel).reload(root)
        expandDefaultNodes()
        revalidate()
        repaint()
    }


    private fun getMavenDependencies(): Map<String, List<PsiClass>> {
        val mavenDependencies = mutableMapOf<String, MutableList<PsiClass>>()
        addedDependencies.forEach { cls ->
            if (isExternalDependency(cls)) {
                val mavenInfo = extractMavenInfo(cls)
                if (mavenInfo != null) {
                    val key = mavenInfo.toString()
                    mavenDependencies.getOrPut(key) { mutableListOf() }.add(cls)
                }
            }
        }
        return mavenDependencies
    }

    private fun getExternalDependencies(): List<PsiClass> {
        return addedDependencies.filter { isExternalDependency(it) && extractMavenInfo(it) == null }
    }

    private fun extractMavenInfo(cls: PsiClass): MavenDependency? {
        val virtualFile = cls.containingFile?.virtualFile ?: return null
        val path = virtualFile.path
        // 匹配形如 /.m2/repository/com/yupaopao/platform/config-client/0.10.21/config-client-0.10.21.jar 的路径
        val regex = ".*/repository/(.+)/([^/]+)/([^/]+)/([^/]+)\\.jar!/.*".toRegex()
        val matchResult = regex.find(path) ?: return null

        return try {
            val (groupIdPath, artifactId, version, _) = matchResult.destructured
            MavenDependency(
                groupId = groupIdPath.replace('/', '.'),
                artifactId = artifactId,
                version = version
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun getModuleName(cls: PsiClass): String {
        val virtualFile = cls.containingFile?.virtualFile ?: return "Unknown Module"
        val path = virtualFile.path
        val projectPath = project.basePath ?: return "Unknown Module"
        val relativePath = path.removePrefix(projectPath).removePrefix("/")
        return relativePath.split("/").firstOrNull() ?: "Unknown Module"
    }

    private fun isExternalDependency(cls: PsiClass): Boolean {
        val virtualFile = cls.containingFile?.virtualFile ?: return true
        val projectPath = project.basePath ?: return true
        return !virtualFile.path.startsWith(projectPath)
    }

    private fun findOrCreatePackageNode(parentNode: DefaultMutableTreeNode, packageName: String): DefaultMutableTreeNode {
        // 查找现有节点
        for (i in 0 until parentNode.childCount) {
            val child = parentNode.getChildAt(i) as DefaultMutableTreeNode
            val childPackage = child.userObject.toString()
            if (childPackage == packageName) {
                return child
            }
            // 如果当前包名应该在这个位置插入
            if (childPackage > packageName) {
                val newNode = DefaultMutableTreeNode(packageName)
                parentNode.insert(newNode, i)
                return newNode
            }
        }
        // 如果没找到合适的位置，添加到末尾
        val newNode = DefaultMutableTreeNode(packageName)
        parentNode.add(newNode)
        return newNode
    }

    private fun isClassInAddedDependencies(psiClass: PsiClass): Boolean {
        return addedDependencies.contains(psiClass)
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

    private fun getPath(node: DefaultMutableTreeNode): Array<DefaultMutableTreeNode> {
        val path = mutableListOf<DefaultMutableTreeNode>()
        var current: DefaultMutableTreeNode? = node

        while (current != null) {
            path.add(0, current)
            current = current.parent as? DefaultMutableTreeNode
        }

        return path.toTypedArray()
    }
}

data class MavenDependency(
    val groupId: String,
    val artifactId: String,
    val version: String
) {
    override fun toString(): String = "$groupId:$artifactId:$version"
}

class ClassDependencyInfo(var isDataClass: Boolean = false) {
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
            // 检查是否是 Root Classes 下的节点或其子节点
            var parent = value.parent as? DefaultMutableTreeNode
            while (parent != null) {
                if (parent.userObject == "Root Classes") {
                    checkbox.isSelected = value.isChecked
                    checkbox.text = value.text
                    checkbox.isOpaque = false
                    return checkbox
                }
                parent = parent.parent as? DefaultMutableTreeNode
            }

            // 如果不是 Root Classes 节点，检查是否是类节点
            val userObj = value.userObject
            if (userObj is ProjectClass) {
                val psiClass = userObj.psiClass
                // 判断是否是数据类
                if (PsiUtils.isDataClass(psiClass)) {
                    // 如果是数据类，使用特殊图标
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
        }

        return defaultComponent
    }
}

