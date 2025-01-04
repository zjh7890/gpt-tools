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

class FileTreeListPanel(private val project: Project) : JPanel() {
    private val root = DefaultMutableTreeNode("")
    private val rootClassNode = DefaultMutableTreeNode("Root Classes")
    private val dependenciesNode = DefaultMutableTreeNode("Dependencies")
    val tree = Tree(root)
    private val addedClasses = mutableSetOf<PsiClass>()

    init {
        layout = java.awt.BorderLayout()
        root.add(rootClassNode)
        root.add(dependenciesNode)

        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = CheckboxTreeCellRenderer()

        add(JScrollPane(tree), java.awt.BorderLayout.CENTER)

        tree.expandPath(TreePath(arrayOf(root, rootClassNode)))
        tree.expandPath(TreePath(arrayOf(root, dependenciesNode)))

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

    fun runAnalysis(project: Project, onComplete: () -> Unit = {}) {
        dependenciesNode.removeAllChildren()

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

            ApplicationManager.getApplication().invokeLater {
                organizeDependenciesFromGraph(classDependencyGraph)
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
        // 如果该方法已经被分析过,直接返回
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

        // 如果该字段已经被分析过,直接返回
        if (classInfo.usedFields.contains(field)) {
            return
        }

        classInfo.markFieldUsed(field)

        // 检查字段类型是否为数据类
        val fieldTypeClass = resolveClassFromType(field.type)
        if (fieldTypeClass != null && isDataClass(fieldTypeClass)) {
            // 如果字段类型是数据类，需要递归分析该数据类的依赖
            analyzeDataClass(fieldTypeClass, classGraph)
        }

        // 分析字段初始化器中的依赖
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

    private fun organizeDependenciesFromGraph(classGraph: Map<PsiClass, ClassDependencyInfo>) {
        dependenciesNode.removeAllChildren()

        val moduleMap = mutableMapOf<String, DefaultMutableTreeNode>()
        val mavenDependencies = mutableMapOf<String, MutableList<PsiClass>>()
        val externalsNode = DefaultMutableTreeNode("Externals")

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

        // 添加项目内模块依赖到 Dependencies 节点
        moduleMap.entries.sortedBy { it.key }.forEach { (_, node) ->
            if (node.childCount > 0) {
                dependenciesNode.add(node)
            }
        }

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
            dependenciesNode.add(mavenNode)
        }

        // 3. 添加外部依赖节点
        if (externalsNode.childCount > 0) {
            dependenciesNode.add(externalsNode)
        }

        // 刷新树模型并展开节点
        (tree.model as DefaultTreeModel).reload(root)
        expandDefaultNodes()
    }

    private fun getMavenDependencies(): Map<String, List<PsiClass>> {
        val mavenDependencies = mutableMapOf<String, MutableList<PsiClass>>()
        addedClasses.forEach { cls ->
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
        return addedClasses.filter { isExternalDependency(it) && extractMavenInfo(it) == null }
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


    private fun PsiClass.isTopLevelClass(): Boolean {
        return this.parent is PsiFile
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
        return (name.startsWith("get") && method.parameterList.isEmpty && method.returnType != PsiTypes.voidType()) ||
                (name.startsWith("set") && method.parameterList.parametersCount == 1 && method.returnType == PsiTypes.voidType())
    }

    private fun PsiMethod.isStandardClassMethod(): Boolean {
        return when (this.name) {
            "equals", "hashCode", "toString", "canEqual" -> true
            else -> false
        }
    }

    private class ClassDependencyInfo(var isDataClass: Boolean = false) {
        val usedMethods = mutableSetOf<PsiMethod>()
        val usedFields = mutableSetOf<PsiField>()

        fun markMethodUsed(method: PsiMethod) {
            usedMethods.add(method)
        }

        fun markFieldUsed(field: PsiField) {
            usedFields.add(field)
        }
    }

    fun addClass(psiClass: PsiClass, selected: Boolean) {
        if (!isClassInAddedClasses(psiClass)) {
            val expandedPaths = getExpandedPaths()
            val node = CheckboxTreeNode(psiClass.name ?: "Unnamed Class")
            node.isChecked = selected // 默认不选中

            if (!isDataClass(psiClass)) {
                psiClass.methods.forEach { method ->
                    if (!ifGetterOrSetter(method) && !method.isStandardClassMethod()) {
                        val methodNode = CheckboxTreeNode(method.name ?: "Unnamed Method")
                        methodNode.isChecked = selected // 默认不选中
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
        tree.expandPath(TreePath(arrayOf(root, dependenciesNode)))

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

        // 展开 Dependencies 节点
        for (i in 0 until dependenciesNode.childCount) {
            val node = dependenciesNode.getChildAt(i) as DefaultMutableTreeNode
            if (node.userObject.toString() != "Maven Dependencies" &&
                node.userObject.toString() != "Externals") {
                expandNodeRecursively(node)
            } else if (node.userObject.toString() == "Maven Dependencies") {
                tree.expandPath(TreePath(arrayOf(root, dependenciesNode, node)))
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
                    selectedNode != rootClassNode && // 保持此逻辑
                    selectedNode != dependenciesNode) {
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

    fun copyAllFiles(project: Project) {
        val classesInfo = addedClasses.map { FileUtil.readFileInfoForLLM(it.containingFile.virtualFile, project) }.joinToString("\n\n")
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
        }

        return component
    }
}

private data class MavenDependency(
    val groupId: String,
    val artifactId: String,
    val version: String
) {
    override fun toString(): String = "$groupId:$artifactId:$version"
}
