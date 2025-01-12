// core/src/main/kotlin/com/github/zjh7890/gpttools/toolWindow/context/ChatFileTreeListPanel.kt

package com.github.zjh7890.gpttools.toolWindow.context

import com.github.zjh7890.gpttools.services.ChatSession
import com.github.zjh7890.gpttools.toolWindow.treePanel.ClassDependencyInfo
import com.github.zjh7890.gpttools.toolWindow.treePanel.DependenciesTreePanel
import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

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
     * 更新文件树，采用类似 Dependencies 节点的层级结构展示（project -> module -> package -> file）
     */
    fun updateFileTree(session: ChatSession) {
        currentSession = session
        root.removeAllChildren()

        session.projectFileTrees.forEach { projectTree ->
            // 创建 projectName 节点
            val projectNode = DefaultMutableTreeNode(projectTree.projectName)
            root.add(projectNode)

            // 按模块分组文件
            val moduleToFilesMap = projectTree.files.groupBy { file ->
                getModuleName(file)
            }

            // 创建模块节点，添加到 projectName 节点下
            moduleToFilesMap.forEach { (moduleName, files) ->
                val moduleNode = DefaultMutableTreeNode(moduleName)
                projectNode.add(moduleNode)

                // 按包分组文件
                val packageToFilesMap = files.groupBy { file ->
                    getPackageName(file)
                }

                // 创建包节点，添加到模块节点下
                packageToFilesMap.forEach { (packageName, packageFiles) ->
                    val packageNode = DefaultMutableTreeNode(packageName)
                    moduleNode.add(packageNode)

                    // 添加文件节点到包节点下
                    packageFiles.forEach { file ->
                        val fileNode = DefaultMutableTreeNode(file)
                        packageNode.add(fileNode)
                    }
                }
            }
        }
    }

    /**
     * 获取文件所属的模块名称
     */
    private fun getModuleName(file: VirtualFile): String {
        // 假设模块名称可以从文件路径中提取，例如 /project/module/src/...
        val path = file.path
        val segments = path.split("/")
        val srcIndex = segments.indexOf("src")
        return if (srcIndex > 0 && segments.size > srcIndex) segments[srcIndex - 1] else "Unknown Module"
    }

    /**
     * 获取文件的包名
     */
    private fun getPackageName(file: VirtualFile): String {
        val projectPath = project.basePath ?: return "(default package)"
        val filePath = file.path

        // 支持 src/main/java 或 src/main/kotlin
        val srcJava = "src/main/java/"
        val srcKotlin = "src/main/kotlin/"
        val indexJava = filePath.indexOf(srcJava)
        val indexKotlin = filePath.indexOf(srcKotlin)

        val startIndex = when {
            indexJava != -1 -> indexJava + srcJava.length
            indexKotlin != -1 -> indexKotlin + srcKotlin.length
            else -> return "(default package)"
        }

        val relativePath = if (startIndex < filePath.length) {
            filePath.substring(startIndex, filePath.lastIndexOf('/'))
        } else {
            ""
        }

        return if (relativePath.isNotEmpty()) {
            relativePath.replace('/', '.')
        } else {
            "(default package)"
        }
    }

    /**
     * 运行依赖分析并更新 DependenciesTreePanel
     */
    private fun runAnalysis() {
        val selectedClasses = getSelectedClasses()
        if (selectedClasses.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "未选择任何类进行分析。",
                "分析结果"
            )
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
            var classNode: DefaultMutableTreeNode? = null
            for (i in 0 until root.childCount) {
                val node = root.getChildAt(i) as? DefaultMutableTreeNode
                if (node?.userObject is String && node.userObject == psiClass.name) {
                    classNode = node
                    break
                }
            }

            if (classNode != null) {
                // 如果整个类被选中，分析所有方法
                // 此处需要根据您的实际逻辑判断类是否被选中
                val isClassSelected = true // 示例，实际应根据树中节点状态判断

                if (isClassSelected) {
                    val methodsToAnalyze = psiClass.methods.toList()
                    for (m in methodsToAnalyze) {
                        analyzeMethodDependencies(m, psiClass, classGraph)
                    }
                } else {
                    // 否则只分析被选中的方法
                    // 此处需要根据您的实际逻辑获取被选中的方法
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

    /**
     * 获取选中的类
     */
    private fun getSelectedClasses(): List<PsiClass> {
        // 实现根据树中选中的节点获取对应的 PsiClass
        // 这里需要根据您的具体实现填充
        // 示例返回空列表
        return emptyList()
    }
}
