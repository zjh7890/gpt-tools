// core/src/main/kotlin/com/github/zjh7890/gpttools/toolWindow/treePanel/FileTreeListPanel.kt

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
import java.awt.BorderLayout
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

class FileTreeListPanel(private val project: Project) : JPanel() {
    private val root = DefaultMutableTreeNode("")
    private val rootClassNode = DefaultMutableTreeNode("Root Classes")
    val tree = Tree(root)
    private val addedClasses = mutableSetOf<PsiClass>()

    // 包含一个 DependenciesTreePanel
    private val dependenciesTreePanel = DependenciesTreePanel(project)

    init {
        layout = BorderLayout()
        root.add(rootClassNode)

        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = CheckboxTreeCellRenderer()

        // 使用 JSplitPane 分割 FileTreeListPanel 和 DependenciesTreePanel
        val splitPane =
            javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT, JScrollPane(tree), dependenciesTreePanel)
        splitPane.dividerLocation = 300 // 设置初始分割位置，可根据需要调整

        add(splitPane, BorderLayout.CENTER)

        tree.expandPath(TreePath(arrayOf(root, rootClassNode)))

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
                    val psiClass = addedClasses.find { it.name == className }
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

    fun runAnalysis(onComplete: () -> Unit = {}) {
        val selectedClasses = getSelectedClasses()
        if (selectedClasses.isEmpty()) {
            (tree.model as DefaultTreeModel).reload(root)
            expandDefaultNodes()
            onComplete()
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val classDependencyGraph = mutableMapOf<PsiClass, ClassDependencyInfo>()

            ApplicationManager.getApplication().runReadAction {
                for (rootClass in selectedClasses) {
                    analyzeClassDependencies(rootClass, classDependencyGraph)
                }
            }

            // 更新 DependenciesTreePanel
            ApplicationManager.getApplication().invokeLater {
                dependenciesTreePanel.updateDependencies(classDependencyGraph)
                (tree.model as DefaultTreeModel).reload(root)
                expandDefaultNodes()
                onComplete()
            }
        }
    }

    private fun analyzeClassDependencies(
        psiClass: PsiClass,
        classGraph: MutableMap<PsiClass, ClassDependencyInfo>
    ) {
        val dataClassFlag = isDataClass(psiClass)

        if (dataClassFlag) {
            analyzeDataClass(psiClass, classGraph)
        } else {
            // 找到对应的类节点
            var classNode: CheckboxTreeNode? = null
            for (i in 0 until rootClassNode.childCount) {
                val node = rootClassNode.getChildAt(i) as? CheckboxTreeNode
                if (node?.text == psiClass.name) {
                    classNode = node
                    break
                }
            }

            if (classNode != null) {
                // 如果整个类被选中，分析所有方法
                if (classNode.isChecked) {
                    val methodsToAnalyze = psiClass.methods.toList()
                    for (m in methodsToAnalyze) {
                        analyzeMethodDependencies(m, psiClass, classGraph)
                    }
                } else {
                    // 否则只分析被选中的方法
                    for (i in 0 until classNode.childCount) {
                        val methodNode = classNode.getChildAt(i) as? CheckboxTreeNode
                        if (methodNode?.isChecked == true) {
                            val methodName = methodNode.text
                            val method = psiClass.methods.find { it.name == methodName }
                            if (method != null) {
                                analyzeMethodDependencies(method, psiClass, classGraph)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun analyzeDataClass(
        psiClass: PsiClass,
        classGraph: MutableMap<PsiClass, ClassDependencyInfo>
    ) {
        var classInfo = classGraph[psiClass]
        if (classInfo == null) {
            val dataClassDeps = extractDataClassDependencies(psiClass)
            for (depClass in dataClassDeps) {
                classGraph[depClass] = ClassDependencyInfo(isDataClass = true)
            }
        }
    }

    private fun analyzeMethodDependencies(
        method: PsiMethod,
        currentClass: PsiClass,
        classGraph: MutableMap<PsiClass, ClassDependencyInfo>
    ) {
        var classInfo = classGraph[currentClass] ?: ClassDependencyInfo()

        if (classInfo.usedMethods.contains(method)) {
            return
        }
        classInfo.markMethodUsed(method)
        val usedElements = findFieldOrMethodUsedElements(method)
        for (element in usedElements) {
            when (element) {
                is PsiMethod -> {
                    val depClass = element.containingClass ?: continue
                    analyzeMethodDependencies(element, depClass, classGraph)
                }

                is PsiField -> {
                    val depClass = element.containingClass
                    analyzeFieldDependencies(element, depClass, classGraph)
                }
            }
        }

        classGraph[currentClass] = classInfo
    }

    private fun analyzeFieldDependencies(
        field: PsiField,
        containingClass: PsiClass?,
        classGraph: MutableMap<PsiClass, ClassDependencyInfo>
    ) {
        if (containingClass == null) return

        var classInfo = classGraph.getOrPut(containingClass) { ClassDependencyInfo() }

        if (classInfo.usedFields.contains(field)) {
            return
        }

        classInfo.markFieldUsed(field)

        val fieldTypeClass = resolveClassFromType(field.type)
        if (fieldTypeClass != null && isDataClass(fieldTypeClass)) {
            analyzeDataClass(fieldTypeClass, classGraph)
        }

        field.initializer?.let { initializer ->
            val usedElements = findFieldOrMethodUsedElements(initializer)
            for (element in usedElements) {
                when (element) {
                    is PsiMethod -> {
                        val depClass = element.containingClass ?: return@let
                        analyzeMethodDependencies(element, depClass, classGraph)
                    }

                    is PsiField -> {
                        val depClass = element.containingClass
                        analyzeFieldDependencies(element, depClass, classGraph)
                    }
                }
            }
        }
    }

    private fun extractDataClassDependencies(psiClass: PsiClass): List<PsiClass> {
        val deps = mutableListOf<PsiClass>()
        for (field in psiClass.fields) {
            val fieldTypeClass = resolveClassFromType(field.type)
            if (fieldTypeClass != null && isDataClass(fieldTypeClass)) {
                deps.add(fieldTypeClass)
            }
        }
        return deps
    }

    private fun findFieldOrMethodUsedElements(element: PsiElement): List<PsiElement> {
        val used = mutableListOf<PsiElement>()
        PsiTreeUtil.processElements(element) { e ->
            when (e) {
                is PsiMethodCallExpression -> {
                    e.resolveMethod()?.let { used.add(it) }
                }

                is PsiReferenceExpression -> {
                    val resolved = e.resolve()
                    if (resolved is PsiField) {
                        used.add(resolved)
                    }
                }
            }
            true
        }
        return used
    }

    private fun resolveClassFromType(type: PsiType): PsiClass? {
        return PsiUtil.resolveClassInType(type)
    }

    private fun isDataClass(element: PsiElement?): Boolean {
        if (element !is PsiClass) return false

        val methods = element.methods
        val fields = element.fields

        if (fields.isEmpty()) return false

        val allMethodsAreGettersSettersOrStandard = methods.all {
            ifGetterOrSetter(it) || it.isStandardClassMethod() || it.isConstructor
        }

        return allMethodsAreGettersSettersOrStandard
    }

    private fun ifGetterOrSetter(method: PsiMethod): Boolean {
        val name = method.name
        return (name.startsWith("get") && method.parameterList.parametersCount == 0 && method.returnType != PsiTypes.voidType()) ||
                (name.startsWith("set") && method.parameterList.parametersCount == 1 && method.returnType == PsiTypes.voidType())
    }

    private fun PsiMethod.isStandardClassMethod(): Boolean {
        return when (this.name) {
            "equals", "hashCode", "toString", "canEqual" -> true
            else -> false
        }
    }

    fun addClass(psiClass: PsiClass, selected: Boolean) {
        if (!isClassInAddedClasses(psiClass)) {
            val expandedPaths = getExpandedPaths()
            val node = CheckboxTreeNode(psiClass.name ?: "Unnamed Class")
            node.isChecked = selected // 根据需要设置初始选中状态

            if (!isDataClass(psiClass)) {
                psiClass.methods.forEach { method ->
                    if (!ifGetterOrSetter(method) && !method.isStandardClassMethod()) {
                        val methodNode = CheckboxTreeNode(method.name ?: "Unnamed Method")
                        methodNode.isChecked = selected // 根据需要设置初始选中状态
                        node.add(methodNode)
                    }
                }
            }

            rootClassNode.add(node)
            addedClasses.add(psiClass)
            (tree.model as DefaultTreeModel).reload(root)
            restoreExpandedPaths(expandedPaths)

            // 展开到方法级别
            val classPath = TreePath(arrayOf(root, rootClassNode, node))
            tree.expandPath(classPath)
        }
    }

    fun addMethod(psiClass: PsiClass, method: PsiMethod) {
        // 先添加类及其所有方法
        addClass(psiClass, false)

        // 找到对应的类节点
        var classNode: CheckboxTreeNode? = null
        for (i in 0 until rootClassNode.childCount) {
            val node = rootClassNode.getChildAt(i) as? CheckboxTreeNode
            if (node?.text == psiClass.name) {
                classNode = node
                break
            }
        }

        if (classNode != null) {
            // 找到对应的方法节点并设置选中状态
            for (i in 0 until classNode.childCount) {
                val node = classNode.getChildAt(i) as? CheckboxTreeNode
                if (node?.text == method.name) {
                    node.isChecked = true
                    break
                }
            }

            // 展开到方法级别
            val classPath = TreePath(arrayOf(root, rootClassNode, classNode))
            tree.expandPath(classPath)
        }
    }

    private fun isClassInAddedClasses(psiClass: PsiClass): Boolean {
        return addedClasses.contains(psiClass)
    }

    private fun expandDefaultNodes() {
        // 展开根节点
        tree.expandPath(TreePath(arrayOf(root, rootClassNode)))

        // 展开 Root Classes 下的所有节点到方法级别
        for (i in 0 until rootClassNode.childCount) {
            val classNode = rootClassNode.getChildAt(i)
            val classPath = TreePath(arrayOf(root, rootClassNode, classNode))
            tree.expandPath(classPath)

            // 展开类节点下的所有方法节点
            for (j in 0 until (classNode as DefaultMutableTreeNode).childCount) {
                val methodNode = classNode.getChildAt(j)
                val methodPath = TreePath(arrayOf(root, rootClassNode, classNode, methodNode))
                tree.expandPath(methodPath)
            }
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

    fun removeSelectedNodes() {
        val expandedPaths = getExpandedPaths()
        val selectedPaths = tree.selectionPaths
        if (selectedPaths != null) {
            val model = tree.model as DefaultTreeModel
            selectedPaths.forEach { path ->
                val selectedNode = path.lastPathComponent as? DefaultMutableTreeNode
                if (selectedNode != null && selectedNode != root &&
                    selectedNode != rootClassNode
                ) {
                    removeNodeAndChildren(selectedNode)
                }
            }
            model.reload(root)
            restoreExpandedPaths(expandedPaths)
        }
    }

    private fun removeNodeAndChildren(node: DefaultMutableTreeNode) {
        val nodesToRemove = LinkedList<DefaultMutableTreeNode>()
        nodesToRemove.add(node)

        while (nodesToRemove.isNotEmpty()) {
            val currentNode = nodesToRemove.poll()
            for (i in 0 until currentNode.childCount) {
                nodesToRemove.add(currentNode.getChildAt(i) as DefaultMutableTreeNode)
            }
            // 如果是类节点，移除对应的 PsiClass
            if (currentNode.parent == rootClassNode) {
                val className = currentNode.userObject.toString()
                addedClasses.removeIf { it.name == className }
            }
            currentNode.removeFromParent()
        }
    }

    fun copyAllFiles() {
        val classesInfo = addedClasses.map { FileUtil.readFileInfoForLLM(it.containingFile.virtualFile, project) }
            .joinToString("\n\n")
        val sb: StringBuilder = StringBuilder()
        sb.append("下面是提供的信息：\n" + FileUtil.wrapBorder(classesInfo))
        ClipboardUtils.copyToClipboard(sb.toString())
    }

    fun expandSelectedNodes() {
        val selectedPaths = tree.selectionPaths ?: return
        selectedPaths.forEach { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@forEach
            expandNodeRecursively(node)
        }
    }

    fun collapseSelectedNodes() {
        val selectedPaths = tree.selectionPaths ?: return
        selectedPaths.forEach { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@forEach
            collapseNodeRecursively(node)
        }
    }

    private fun collapseNodeRecursively(node: DefaultMutableTreeNode) {
        if (node.childCount > 0) {
            val path = getPathToNode(node)
            tree.collapsePath(TreePath(path.toTypedArray()))

            for (i in 0 until node.childCount) {
                collapseNodeRecursively(node.getChildAt(i) as DefaultMutableTreeNode)
            }
        }
    }

    fun getSelectedClasses(): List<PsiClass> {
        val selectedClasses = mutableSetOf<PsiClass>() // 使用 Set 避免重复

        for (i in 0 until rootClassNode.childCount) {
            val classNode = rootClassNode.getChildAt(i) as? CheckboxTreeNode ?: continue
            val psiClass = addedClasses.find { it.name == classNode.text } ?: continue

            // 如果类节点被选中，直接添加类
            if (classNode.isChecked) {
                selectedClasses.add(psiClass)
                continue
            }

            // 检查是否有方法被选中
            var hasSelectedMethod = false
            for (j in 0 until classNode.childCount) {
                val methodNode = classNode.getChildAt(j) as? CheckboxTreeNode
                if (methodNode?.isChecked == true) {
                    hasSelectedMethod = true
                    break
                }
            }

            // 如果有方法被选中，添加对应的类
            if (hasSelectedMethod) {
                selectedClasses.add(psiClass)
            }
        }

        return selectedClasses.toList()
    }
}
