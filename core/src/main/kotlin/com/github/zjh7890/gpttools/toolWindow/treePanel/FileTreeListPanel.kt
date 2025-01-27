// core/src/main/kotlin/com/github/zjh7890/gpttools/toolWindow/treePanel/FileTreeListPanel.kt

package com.github.zjh7890.gpttools.toolWindow.treePanel

import com.github.zjh7890.gpttools.services.*
import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.PsiUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.tree.DefaultMutableTreeNode
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
                dependenciesTreePanel.updateDependencies(AppFileTree.buildAppFileTreeFromClassGraph(classDependencyGraph))
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
        val dataClassFlag = PsiUtils.isAtomicClass(psiClass)

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

    fun addClass(psiClass: PsiClass, selected: Boolean) {
        if (!isClassInAddedClasses(psiClass)) {
            val expandedPaths = getExpandedPaths()
            val node = CheckboxTreeNode(psiClass.name ?: "Unnamed Class")
            node.isChecked = selected // 根据需要设置初始选中状态

            if (!PsiUtils.isAtomicClass(psiClass)) {
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

    companion object {
        fun analyzeMethodDependencies(
            method: PsiMethod,
            currentClass: PsiClass,
            classGraph: MutableMap<PsiClass, ClassDependencyInfo>
        ) {
            val classInfo = classGraph.getOrPut(currentClass) { ClassDependencyInfo() }
            if (classInfo.usedMethods.contains(method)) {
                return
            }
            classInfo.markMethodUsed(method)
            findFieldOrMethodUsedElements(method, classGraph)
        }

        fun findFieldOrMethodUsedElements(element: PsiElement, classGraph: MutableMap<PsiClass, ClassDependencyInfo>) {
            PsiTreeUtil.processElements(element) { e ->
                when (e) {
                    is PsiMethodCallExpression -> {
                        val psiMethod = e.resolveMethod()
                        val depClass = psiMethod?.containingClass ?: return@processElements true
                        // 检查类文件是否是项目文件
                        val virtualFile = depClass.containingFile?.virtualFile
                        if (virtualFile != null &&
                            PsiUtils.ifProjectFile(depClass.project, virtualFile) &&
                            !PsiUtils.isAtomicClass(depClass)) {  // 添加检查
                            analyzeMethodDependencies(psiMethod, depClass, classGraph)
                        }
                    }
                    is PsiReferenceExpression -> {
                        val resolved = e.resolve()
                        if (resolved is PsiField) {
                            val depClass = resolved.containingClass
                            // 检查类文件是否是项目文件
                            val virtualFile = depClass?.containingFile?.virtualFile
                            if (virtualFile != null &&
                                PsiUtils.ifProjectFile(depClass.project, virtualFile) &&
                                !PsiUtils.isAtomicClass(depClass)) {  // 添加检查
                                analyzeFieldDependencies(resolved, depClass, classGraph)
                            }
                        }
                    }
                    is PsiTypeElement -> {
                        // 处理显式类型声明
                        val typeClass = resolveClassFromType(e.type)
                        if (typeClass != null) {
                            // 检查类文件是否是项目文件
                            val virtualFile = typeClass.containingFile?.virtualFile
                            if (virtualFile != null &&
                                PsiUtils.ifProjectFile(typeClass.project, virtualFile) &&
                                PsiUtils.isAtomicClass(typeClass)) {
                                analyzeDataClass(typeClass, classGraph)
                            }
                        }
                    }
                }
                true
            }
        }

        fun analyzeFieldDependencies(
            field: PsiField,
            containingClass: PsiClass,
            classGraph: MutableMap<PsiClass, ClassDependencyInfo>
        ) {
            val classInfo = classGraph.getOrPut(containingClass) { ClassDependencyInfo() }
            if (classInfo.usedFields.contains(field)) {
                return
            }
            classInfo.markFieldUsed(field)
            findFieldOrMethodUsedElements(field, classGraph);
        }

        fun resolveClassFromType(type: PsiType): PsiClass? {
            return PsiUtil.resolveClassInType(type)
        }

        fun ifGetterOrSetter(method: PsiMethod): Boolean {
            val name = method.name
            return (
                    // getter 判断：以 get 开头或 is 开头（boolean 类型）
                    ((name.startsWith("get") || name.startsWith("is")) &&
                            method.parameterList.parametersCount == 0 &&
                            method.returnType != PsiTypes.voidType()) ||
                            // setter 判断
                            (name.startsWith("set") &&
                                    method.parameterList.parametersCount == 1 &&
                                    method.returnType == PsiTypes.voidType())
                    )
        }

        fun PsiMethod.isStandardClassMethod(): Boolean {
            return when (this.name) {
                "equals", "hashCode", "toString", "canEqual" -> true
                else -> false
            }
        }

        fun analyzeDataClass(
            psiClass: PsiClass,
            classGraph: MutableMap<PsiClass, ClassDependencyInfo>
        ) {
            // 如果已经分析过这个类，直接返回
            if (classGraph[psiClass] != null) {
                return
            }

            classGraph.getOrPut(psiClass) { ClassDependencyInfo(isAtomicClass = true) }
            // 处理整个类中的所有元素
            PsiTreeUtil.processElements(psiClass) { element ->
                when (element) {
                    is PsiTypeElement -> {
                        val typeClass = resolveClassFromType(element.type)
                        if (typeClass != null) {
                            val virtualFile = typeClass.containingFile?.virtualFile
                            if (virtualFile != null && PsiUtils.ifProjectFile(typeClass.project, virtualFile)) {
                                if (PsiUtils.isAtomicClass(typeClass)) {
                                    analyzeDataClass(typeClass, classGraph)
                                }
                            }
                        }
                    }
                }
                true
            }
        }
    }
}
