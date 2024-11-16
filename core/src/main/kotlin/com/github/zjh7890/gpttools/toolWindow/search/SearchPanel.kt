package com.github.zjh7890.gpttools.toolWindow.search

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class SearchPanel(private val project: Project) {
    private val mainPanel: JPanel = JPanel(BorderLayout())
    private val searchField: JTextField = JTextField()
    private val resultTree: JTree = JTree(DefaultMutableTreeNode(""))

    init {
        mainPanel.add(searchField, BorderLayout.NORTH)
        mainPanel.add(JScrollPane(resultTree), BorderLayout.CENTER)

        val root = DefaultMutableTreeNode() // No name for the root node, making it invisible in the UI
        resultTree.model = DefaultTreeModel(root)
        resultTree.isRootVisible = false // Hide the root node to remove the extra layer

        // Add a KeyListener to handle "Enter" key press
        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    val query = searchField.text
                    updateSearchResults(query) // Call updateSearchResults when Enter is pressed
                }
            }
        })

        // Add right-click context menu to the result tree
        addContextMenu(resultTree)

        resultTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val selectedNode = resultTree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
                    val userObject = selectedNode?.userObject

                    if (userObject is SearchResult) {
                        openFile(userObject.virtualFile)
                    } else if (userObject is PsiMethod) {
                        // 双击方法节点，跳转到方法位置
                        val method = userObject
                        val containingFile = method.containingFile?.virtualFile
                        if (containingFile != null) {
                            openFileAtMethod(containingFile, method)
                        }
                    }
                }
            }
        })
    }

    val content: JComponent
        get() = mainPanel

    private fun updateSearchResults(query: String) {
        // Run the search operation in a background thread
        ApplicationManager.getApplication().executeOnPooledThread {
            ApplicationManager.getApplication().runReadAction {
                val results = mutableListOf<SearchResult>()

                // Search project files
                results.addAll(searchProjectFiles(query))

                // Search JAR files ending with `-api`
                results.addAll(searchJarFiles(query))

                // Process search results to add methods (this is slow and should be done off the EDT)
                results.forEach { result ->
                    val psiClass = getPsiClassFromVirtualFile(result.virtualFile)
                    result.psiClass = psiClass
                }

                // Once search is done, update the UI on the EDT
                SwingUtilities.invokeLater {
                    updateResultTree(results)
                }
            }
        }
    }

    private fun updateResultTree(results: List<SearchResult>) {
        val root = DefaultMutableTreeNode()

        val projectNode = DefaultMutableTreeNode("Project Files")
        val jarGroups = mutableMapOf<String, DefaultMutableTreeNode>()

        results.forEach { result ->
            val classNode = DefaultMutableTreeNode(result)

            // 如果 PsiClass 已经加载，则预加载其方法到树中
            result.psiClass?.let { psiClass ->
                addMethodsToNode(classNode, psiClass)
            }

            if (result.isProjectFile) {
                // 添加到项目文件节点
                projectNode.add(classNode)
            } else {
                // 如果是 JAR 包中的文件，按 JAR 包名进行分组
                val jarName = getJarNameForFile(result.virtualFile) ?: "Unknown JAR"
                val jarNode = jarGroups.getOrPut(jarName) { DefaultMutableTreeNode(jarName) }
                jarNode.add(classNode)
            }
        }
        

        // 只添加包含结果的节点
        if (projectNode.childCount > 0) {
            root.add(projectNode)
        }

        // 添加 JAR 包分组
        jarGroups.forEach { (_, jarNode) ->
            if (jarNode.childCount > 0) {
                root.add(jarNode)
            }
        }

        resultTree.model = DefaultTreeModel(root)
        resultTree.isRootVisible = false // 隐藏根节点

        // 展开文件级别，但不展开方法
        for (i in 0 until resultTree.rowCount) {
            resultTree.expandRow(i)
        }
    }

    private fun getJarNameForFile(virtualFile: VirtualFile): String? {
        // 使用 JarFileSystem 获取 JAR 文件的根目录
        val jarFile = JarFileSystem.getInstance().getVirtualFileForJar(virtualFile)
        return jarFile?.name
    }

    private fun addMethodsToNode(classNode: DefaultMutableTreeNode, psiClass: PsiClass) {
        psiClass.methods.filter { it.hasModifierProperty(PsiModifier.PUBLIC) }.forEach { method ->
            // 构建方法显示名称（带参数）
            val methodNameWithParams = buildMethodDisplayName(method)

            // 创建自定义的树节点，显示方法名(参数)，但是存储 PsiMethod 对象
            val methodNode = object : DefaultMutableTreeNode(method) {
                override fun toString(): String {
                    return methodNameWithParams // 仅修改显示名称
                }
            }

            classNode.add(methodNode)
        }
    }

    private fun buildMethodDisplayName(method: PsiMethod): String {
        val paramList = method.parameterList.parameters.joinToString(", ") { param ->
            "${param.type.presentableText} ${param.name}"
        }
        return "${method.name}($paramList)" // 返回 auditEnd(InteractiveAuditEndRequest)
    }

    private fun getPsiClassFromVirtualFile(virtualFile: VirtualFile): PsiClass? {
        val psiFile: PsiFile? = PsiManager.getInstance(project).findFile(virtualFile)

        // Check if it's a Java file, and get the classes
        if (psiFile is PsiJavaFile) {
            return psiFile.classes.firstOrNull() // Return the first class in the file (can be adjusted for multiple classes)
        }

        // Add additional handling for other languages if needed, e.g., Kotlin
        return null
    }

    private fun searchProjectFiles(query: String): List<SearchResult> {
        val resultFiles = mutableListOf<SearchResult>()
        val projectFiles = FilenameIndex.getAllFilesByExt(project, "java", GlobalSearchScope.projectScope(project))

        projectFiles.forEach { virtualFile ->
            val psiClass = getPsiClassFromVirtualFile(virtualFile)
            if (psiClass != null && isJumpMatch(virtualFile.name, query)) {
                if (hasSpringOrCustomAnnotationsForProject(psiClass)) {
                    resultFiles.add(SearchResult(virtualFile.name, true, virtualFile, psiClass))
                }
            }
        }
        return resultFiles
    }

    private fun isJumpMatch(filename: String, query: String): Boolean {
        var queryIndex = 0
        for (char in filename.toLowerCase()) {
            if (queryIndex < query.length && char == query[queryIndex].toLowerCase()) {
                queryIndex++
            }
            if (queryIndex == query.length) {
                return true
            }
        }
        return queryIndex == query.length
    }

    // Add context menu for copying method references
    private fun addContextMenu(tree: JTree) {
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val path = tree.getPathForLocation(e.x, e.y)
                    if (path != null) {
                        val node = path.lastPathComponent as DefaultMutableTreeNode
                        val userObject = node.userObject
                        if (userObject is PsiMethod) {
                            val menu = JPopupMenu()
                            val copyRef = JMenuItem("Copy Reference")
                            copyRef.addActionListener { copyMethodReference(userObject) }
                            menu.add(copyRef)
                            menu.show(tree, e.x, e.y)
                        }
                    }
                }
            }
        })
    }

    private fun searchJarFiles(query: String): List<SearchResult> {
        val resultFiles = mutableListOf<SearchResult>()

        OrderEnumerator.orderEntries(project).librariesOnly().forEachLibrary { library ->
            library.getFiles(OrderRootType.CLASSES).forEach { classRoot ->
                val jarFile = JarFileSystem.getInstance().getVirtualFileForJar(classRoot)
                if (jarFile != null && isApiJarFile(jarFile)) {
                    searchInJar(jarFile, query, resultFiles)
                }
            }
            true
        }
        return resultFiles.filter { result ->
            val psiClass = getPsiClassFromVirtualFile(result.virtualFile)
            psiClass != null && isInterfaceOrHasSpringAnnotationsForJar(psiClass)
        }
    }

    private fun hasSpringOrCustomAnnotationsForProject(psiClass: PsiClass): Boolean {
        if (psiClass.name?.endsWith("Mapper") == true) {
            return true
        }

        val springAnnotations = listOf(
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.scheduling.annotation.Scheduled"
        )

        val customAnnotations = listOf(
            "com.yupaopao.framework.spring.boot.aries.annotation.AriesCronJobListener",
            "org.apache.dubbo.config.annotation.DubboService"
        )

        val allAnnotations = springAnnotations + customAnnotations

        // 检查类是否包含上述注解
        psiClass.annotations.forEach { annotation ->
            val qualifiedName = annotation.qualifiedName
            if (qualifiedName != null && allAnnotations.contains(qualifiedName)) {
                return true
            }
        }

        return false
    }

    private fun isInterfaceOrHasSpringAnnotationsForJar(psiClass: PsiClass): Boolean {
        val springAnnotations = listOf(
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.scheduling.annotation.Scheduled"
        )

        // 检查类是否是接口或是否带有 Spring 注解
        if (psiClass.isInterface) {
            return true
        }

        psiClass.annotations.forEach { annotation ->
            val qualifiedName = annotation.qualifiedName
            if (qualifiedName != null && springAnnotations.contains(qualifiedName)) {
                return true
            }
        }

        return false
    }

    private fun isApiJarFile(jarFile: VirtualFile): Boolean {
        // Check if the JAR file matches the pattern `*-api-<version>.jar`
        return jarFile.path.contains("yupaopao") && jarFile.name.matches(Regex(".*-api-.*.jar"))
    }

    private fun searchInJar(jarFile: VirtualFile, query: String, results: MutableList<SearchResult>) {
        // 确保是 JAR 文件的根路径
        val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jarFile) ?: return

        // 递归搜索 JAR 文件中的类和资源文件
        jarRoot.children.forEach { file ->
            searchInJarRecursive(file, query, results)
        }
    }

    private fun searchInJarRecursive(virtualFile: VirtualFile, query: String, results: MutableList<SearchResult>) {
        if (virtualFile.isDirectory) {
            // If it's a directory, continue recursively searching
            virtualFile.children.forEach { child ->
                searchInJarRecursive(child, query, results)
            }
        } else {
            // If it's a file, check if the name matches the query using jump match
            if (isJumpMatch(virtualFile.name, query)) {
                results.add(SearchResult(virtualFile.name, false, virtualFile))
            }
        }
    }

    private fun copyMethodReference(method: PsiMethod) {
        val reference = "${method.containingClass?.qualifiedName}.${method.name}"
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(reference), null)
    }

    private fun openFile(virtualFile: VirtualFile) {
        // 使用 IntelliJ 的 OpenFileDescriptor 打开文件
        ApplicationManager.getApplication().invokeLater {
            val fileEditorManager = FileEditorManager.getInstance(project)
            fileEditorManager.openFile(virtualFile, true)
        }
    }

    private fun openFileAtMethod(virtualFile: VirtualFile, method: PsiMethod) {
        ApplicationManager.getApplication().invokeLater {
            val fileEditorManager = FileEditorManager.getInstance(project)
            val descriptor = OpenFileDescriptor(project, virtualFile, method.textOffset)
            fileEditorManager.openTextEditor(descriptor, true)
        }
    }
}

data class SearchResult(
    val name: String,
    val isProjectFile: Boolean,
    val virtualFile: VirtualFile,
    var psiClass: PsiClass? = null
) {
    override fun toString(): String {
        return name // 确保树中只显示文件名
    }
}