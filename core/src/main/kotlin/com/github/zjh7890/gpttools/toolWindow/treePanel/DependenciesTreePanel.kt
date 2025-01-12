// core/src/main/kotlin/com/github/zjh7890/gpttools/toolWindow/treePanel/DependenciesTreePanel.kt

package com.github.zjh7890.gpttools.toolWindow.treePanel

import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.ui.treeStructure.Tree
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class DependenciesTreePanel(private val project: Project) : JPanel() {
    private val root = DefaultMutableTreeNode("Dependencies")
    val tree = Tree(root)
    private val addedDependencies = mutableSetOf<PsiClass>()

    init {
        layout = java.awt.BorderLayout()

        tree.isRootVisible = true
        tree.showsRootHandles = true
        tree.cellRenderer = CheckboxTreeCellRenderer()

        add(JScrollPane(tree), java.awt.BorderLayout.CENTER)

        tree.expandPath(TreePath(root.path))

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
                    val className = node?.userObject.toString()
                    val psiClass = addedDependencies.find { it.name == className }
                    if (psiClass != null) {
                        val virtualFile = psiClass.containingFile?.virtualFile
                        if (virtualFile != null) {
                            FileEditorManager.getInstance(project).openFile(virtualFile, true)
                        }
                    }
                    e.consume()
                }
            }
        })
    }

    fun updateDependencies(classGraph: Map<PsiClass, ClassDependencyInfo>) {
        root.removeAllChildren()

        val moduleMap = mutableMapOf<String, DefaultMutableTreeNode>()
        val mavenDependencies = mutableMapOf<String, MutableList<PsiClass>>()
        val externalsNode = DefaultMutableTreeNode("Externals")

        // 获取项目名称
        val projectName = project.name
        val projectNode = DefaultMutableTreeNode(projectName)

        // 1. 组织项目内类依赖
        classGraph.keys.forEach { cls ->
            if (!isExternalDependency(cls)) {
                val moduleName = getModuleName(cls)
                val moduleNode = moduleMap.getOrPut(moduleName) {
                    DefaultMutableTreeNode(moduleName)
                }

                val packageName = cls.qualifiedName?.substringBeforeLast(".")
                    ?.replace('.', '/')
                    ?.let { it.substringAfter("src/main/java/") }
                    ?.replace('/', '.') ?: "(default package)"

                val packageNode = findOrCreatePackageNode(moduleNode, packageName)
                packageNode.add(DefaultMutableTreeNode(cls.name))
            } else {
                // 处理外部依赖
                val mavenInfo = extractMavenInfo(cls)
                if (mavenInfo != null) {
                    val key = mavenInfo.toString()
                    mavenDependencies.getOrPut(key) { mutableListOf() }.add(cls)
                } else {
                    // 无法解析为 Maven 依赖的外部依赖
                    val packageName = cls.qualifiedName?.substringBeforeLast(".")
                        ?.replace('.', '/')
                        ?.let { it.substringAfter("src/main/java/") }
                        ?.replace('/', '.') ?: "(default package)"
                    val packageNode = findOrCreatePackageNode(externalsNode, packageName)
                    packageNode.add(DefaultMutableTreeNode(cls.name))
                }
            }
        }

        // 将模块节点添加到 projectName 节点
        moduleMap.entries.sortedBy { it.key }.forEach { (_, node) ->
            if (node.childCount > 0) {
                projectNode.add(node)
            }
        }

        // 添加 projectName 节点到 Dependencies 节点
        root.add(projectNode)

        // 2. 组织 Maven 依赖
        if (mavenDependencies.isNotEmpty()) {
            val mavenNode = DefaultMutableTreeNode("Maven Dependencies")
            mavenDependencies.entries.sortedBy { it.key }.forEach { (mavenInfo, classes) ->
                val mavenInfoNode = DefaultMutableTreeNode(mavenInfo)

                // 按包路径组织类
                val packageMap = classes.groupBy { cls ->
                    cls.qualifiedName?.substringBeforeLast(".")
                        ?.ifEmpty { "(default package)" }
                        ?: "(default package)"
                }

                packageMap.entries.sortedBy { it.key }.forEach { (packageName, packageClasses) ->
                    val packageNode = if (packageName == "(default package)") {
                        mavenInfoNode
                    } else {
                        val node = DefaultMutableTreeNode(packageName)
                        mavenInfoNode.add(node)
                        node
                    }

                    packageClasses.sortedBy { it.name }.forEach { cls ->
                        packageNode.add(DefaultMutableTreeNode(cls.name))
                    }
                }

                mavenNode.add(mavenInfoNode)
            }
            root.add(mavenNode)
        }

        // 3. 添加外部依赖节点
        if (externalsNode.childCount > 0) {
            root.add(externalsNode)
        }

        // 刷新树模型并展开节点
        (tree.model as DefaultTreeModel).reload(root)
        expandDefaultNodes()
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
        val regex = ".*/repository/([^/]+)/([^/]+)/([^/]+)/([^/]+!)/.*".toRegex()
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

    private fun expandDefaultNodes() {
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

    private class CheckboxTreeNode(val text: String) : DefaultMutableTreeNode(text) {
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
                (it as? CheckboxTreeNode)?._isChecked == true
            }
            if (parent._isChecked != newState) {
                parent._isChecked = newState  // 直接设置内部字段，避免触发 setter
                parent.updateParentState()  // 继续向上更新父节点状态
            }
        }
    }

    private class CheckboxTreeCellRenderer : DefaultTreeCellRenderer() {
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
            val component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)

            if (value is CheckboxTreeNode) {
                checkbox.isSelected = value.isChecked
                checkbox.text = value.text
                checkbox.isOpaque = false
                return checkbox
            }

            return component
        }
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
