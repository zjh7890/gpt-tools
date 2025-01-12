package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.settings.other.OtherSettingsState
import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.github.zjh7890.gpttools.utils.PsiUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

class ElementsDepsInSingleFileAction : AnAction() {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val settings = OtherSettingsState.getInstance()
    }

    override fun actionPerformed(e: AnActionEvent) {
        // 获取当前编辑的文件
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        // 获取当前光标所在的元素
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val caretOffset = editor.caretModel.offset
        val elementAtCaret = psiFile.findElementAt(caretOffset) ?: return

        // 获取 project
        val project = e.project ?: return

        // 根据光标所在位置的元素类型，收集相关的 PsiElement
        val elements = mutableListOf<PsiElement>()
        when {
            PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java) != null -> {
                val method = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java) ?: return
                elements.add(method)
            }
            PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass::class.java) != null -> {
                val psiClass = PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass::class.java) ?: return
                elements.add(psiClass)
            }
            PsiTreeUtil.getParentOfType(elementAtCaret, PsiField::class.java) != null -> {
                val psiField = PsiTreeUtil.getParentOfType(elementAtCaret, PsiField::class.java) ?: return
                elements.add(psiField)
            }
            else -> {
                // 处理其他类型的元素或提示用户
                return
            }
        }

        val message = depsInSingleFile(elements, project) ?: return
        ClipboardUtils.copyToClipboard(message)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    companion object {
        /**
         * 生成与指定元素相关的简化文件内容，包括相关类、方法、字段和简化后的导入语句
         */
        fun depsInSingleFile(
            elements: List<PsiElement>,
            project: Project
        ): String? {
            val dependency = findDependencies(elements, project)
            val containingFile = elements.first().containingFile
            val simplifiedElement = simplifyFileByDependency(containingFile, dependency, project)
            return simplifiedElement.text
        }

        /**
         * 递归探索 PsiMethod 的依赖关系
         */
        private fun exploreMethodDependencies(
            method: PsiMethod,
            psiDependency: PsiDependency,
            project: Project,
            curNode: NodeInfo?,
            containingFile: PsiFile
        ) {
            // 避免重复处理
            if (method in psiDependency.psiElementList) return

            // 添加方法到依赖列表
            psiDependency.psiElementList.add(method)

            // 确保在同一文件中
            if (method.containingFile != containingFile) return

            // 添加文件到文件列表
            if (containingFile !in psiDependency.psiFileList) {
                psiDependency.psiFileList.add(containingFile)
            }

            // 添加包含该方法的类到类列表
            val containingClass = PsiTreeUtil.getParentOfType(method, PsiClass::class.java)
            if (containingClass != null && containingClass !in psiDependency.psiClassList) {
                psiDependency.psiClassList.add(containingClass)
            }

            val processedMethodCalls: MutableList<PsiMethodCallExpression> = mutableListOf()

            // 查找方法中的引用
            PsiTreeUtil.findChildrenOfType(method, PsiJavaCodeReferenceElement::class.java).forEach {
                val resolvedElement = it.resolve()
                if (resolvedElement is PsiMethod || resolvedElement is PsiField || isDataClass(resolvedElement)) {
                    // 添加依赖信息
                    val dependencies = psiDependency.elementDependsList.getOrDefault(method, mutableListOf())
                    dependencies.add(ElementDependInfo(resolvedElement!!, it))
                    psiDependency.elementDependsList[method] = dependencies

                    // 更新 incomingList
                    val incomingList = psiDependency.elementIncomingList.getOrDefault(resolvedElement, mutableListOf())
                    incomingList.add(ElementDependInfo(method, it))
                    psiDependency.elementIncomingList[resolvedElement] = incomingList

                    if (curNode != null) {
                        val methodCallExpression = PsiTreeUtil.getParentOfType(it, PsiMethodCallExpression::class.java)
                        if (methodCallExpression != null && !processedMethodCalls.contains(methodCallExpression)) {
                            // 标记为已处理
                            processedMethodCalls.add(methodCallExpression)

                            // 判断方法调用类型
                            when {
                                isDubboReferenceMethodCall(methodCallExpression) -> {
                                    curNode.rpcCalls.add(methodCallExpression)
                                    curNode.calls.add(methodCallExpression)
                                    curNode.hasRpc = true
                                }
                                isMybatisMethodCall(it, project) -> {
                                    curNode.mybatisCalls.add(methodCallExpression)
                                    curNode.calls.add(methodCallExpression)
                                    curNode.hasMybatis = true
                                }
                                isKafkaMethodCall(methodCallExpression) -> {
                                    curNode.kafkaCalls.add(methodCallExpression)
                                    curNode.calls.add(methodCallExpression)
                                    curNode.hasKafka = true
                                }
                                isRedisMethodCall(methodCallExpression) -> {
                                    curNode.redisCalls.add(methodCallExpression)
                                    curNode.calls.add(methodCallExpression)
                                    curNode.hasRedis = true
                                }
                                isAriesMethodCall(methodCallExpression) -> {
                                    curNode.ariesCalls.add(methodCallExpression)
                                    curNode.calls.add(methodCallExpression)
                                    curNode.hasAries = true
                                }
                                isLogMethodCall(methodCallExpression) -> {
                                    curNode.logCalls.add(methodCallExpression)
                                    curNode.calls.add(methodCallExpression)
                                    curNode.hasLog = true
                                }
                            }
                        }
                    }

                    // 递归探索依赖
                    if (isElementInProject(resolvedElement, project) && resolvedElement.containingFile == containingFile) {
                        var childNode: NodeInfo? = null
                        when (resolvedElement) {
                            is PsiMethod -> childNode = NodeInfo(resolvedElement)
                            is PsiClass -> childNode = NodeInfo(resolvedElement)
                            is PsiField -> childNode = NodeInfo(resolvedElement)
                        }
                        exploreDependenciesRecursive(resolvedElement, psiDependency, project, childNode, containingFile)

                        if (curNode != null && childNode != null) {
                            if (childNode.hasDependencies()) {
                                curNode.childrenNodes.add(childNode)
                                // 合并子节点的标志
                                curNode.childHasRpc = curNode.childHasRpc || childNode.childHasRpc || childNode.hasRpc
                                curNode.childHasMybatis = curNode.childHasMybatis || childNode.childHasMybatis || childNode.hasMybatis
                                curNode.childHasKafka = curNode.childHasKafka || childNode.childHasKafka || childNode.hasKafka
                                curNode.childHasRedis = curNode.childHasRedis || childNode.childHasRedis || childNode.hasRedis
                                curNode.childHasAries = curNode.childHasAries || childNode.childHasAries || childNode.hasAries
                                curNode.childHasLog = curNode.childHasLog || childNode.childHasLog || childNode.hasLog
                            }
                        }
                    }
                }
            }
        }

        /**
         * 递归探索 PsiClass 的依赖关系
         */
        private fun exploreClassDependencies(
            psiClass: PsiClass,
            psiDependency: PsiDependency,
            project: Project,
            curNode: NodeInfo?,
            containingFile: PsiFile
        ) {
            // 避免重复处理
            if (psiClass in psiDependency.psiElementList) return

            // 添加类到依赖列表
            psiDependency.psiElementList.add(psiClass)

            // 确保在同一文件中
            if (psiClass.containingFile != containingFile) return

            // 添加文件到文件列表
            if (containingFile !in psiDependency.psiFileList) {
                psiDependency.psiFileList.add(containingFile)
            }

            // 添加类到类列表
            if (psiClass !in psiDependency.psiClassList) {
                psiDependency.psiClassList.add(psiClass)
            }

            // 遍历类的成员（字段、方法、内部类等）
            psiClass.children.forEach { child ->
                when (child) {
                    is PsiMethod -> {
                        exploreMethodDependencies(child, psiDependency, project, curNode, containingFile)
                    }
                    is PsiField -> {
                        exploreFieldDependencies(child, psiDependency, project, curNode, containingFile)
                    }
                    is PsiClass -> {
                        exploreClassDependencies(child, psiDependency, project, curNode, containingFile)
                    }
                    else -> {
                        // 处理其他类型的成员
                    }
                }
            }

            // 查找类中的引用
            PsiTreeUtil.findChildrenOfType(psiClass, PsiJavaCodeReferenceElement::class.java).forEach {
                val resolvedElement = it.resolve()
                if (resolvedElement is PsiMethod || resolvedElement is PsiField || resolvedElement is PsiClass) {
                    // 添加依赖信息
                    val dependencies = psiDependency.elementDependsList.getOrDefault(psiClass, mutableListOf())
                    dependencies.add(ElementDependInfo(resolvedElement, it))
                    psiDependency.elementDependsList[psiClass] = dependencies

                    // 更新 incomingList
                    val incomingList = psiDependency.elementIncomingList.getOrDefault(resolvedElement, mutableListOf())
                    incomingList.add(ElementDependInfo(psiClass, it))
                    psiDependency.elementIncomingList[resolvedElement] = incomingList

                    // 递归探索依赖
                    if (isElementInProject(resolvedElement, project) && resolvedElement.containingFile == containingFile) {
                        var childNode: NodeInfo? = null
                        when (resolvedElement) {
                            is PsiMethod -> childNode = NodeInfo(resolvedElement)
                            is PsiClass -> childNode = NodeInfo(resolvedElement)
                            is PsiField -> childNode = NodeInfo(resolvedElement)
                        }
                        exploreDependenciesRecursive(resolvedElement, psiDependency, project, childNode, containingFile)

                        if (curNode != null && childNode != null) {
                            if (childNode.hasDependencies()) {
                                curNode.childrenNodes.add(childNode)
                                // 合并子节点的标志
                                curNode.childHasRpc = curNode.childHasRpc || childNode.childHasRpc || childNode.hasRpc
                                curNode.childHasMybatis = curNode.childHasMybatis || childNode.childHasMybatis || childNode.hasMybatis
                                curNode.childHasKafka = curNode.childHasKafka || childNode.childHasKafka || childNode.hasKafka
                                curNode.childHasRedis = curNode.childHasRedis || childNode.childHasRedis || childNode.hasRedis
                                curNode.childHasAries = curNode.childHasAries || childNode.childHasAries || childNode.hasAries
                                curNode.childHasLog = curNode.childHasLog || childNode.childHasLog || childNode.hasLog
                            }
                        }
                    }
                }
            }
        }

        /**
         * 递归探索 PsiField 的依赖关系
         */
        private fun exploreFieldDependencies(
            field: PsiField,
            psiDependency: PsiDependency,
            project: Project,
            curNode: NodeInfo?,
            containingFile: PsiFile
        ) {
            // 避免重复处理
            if (field in psiDependency.psiElementList) return

            // 添加字段到依赖列表
            psiDependency.psiElementList.add(field)

            // 确保在同一文件中
            if (field.containingFile != containingFile) return

            // 添加文件到文件列表
            if (containingFile !in psiDependency.psiFileList) {
                psiDependency.psiFileList.add(containingFile)
            }

            // 添加包含该字段的类到类列表
            val containingClass = PsiTreeUtil.getParentOfType(field, PsiClass::class.java)
            if (containingClass != null && containingClass !in psiDependency.psiClassList) {
                psiDependency.psiClassList.add(containingClass)
            }

            // 解析字段类型
            val fieldType: PsiClass? = (field.type as? PsiClassType)?.resolve()
            if (fieldType != null) {
                psiDependency.psiElementList.add(fieldType)
                // 递归探索字段类型的依赖
                if (fieldType is PsiClass) {
                    exploreClassDependencies(fieldType, psiDependency, project, curNode, fieldType.containingFile)
                }
            }

            // 查找字段中的引用
            PsiTreeUtil.findChildrenOfType(field, PsiJavaCodeReferenceElement::class.java).forEach {
                val resolvedElement = it.resolve()
                if (resolvedElement is PsiMethod || resolvedElement is PsiField || resolvedElement is PsiClass) {
                    // 添加依赖信息
                    val dependencies = psiDependency.elementDependsList.getOrDefault(field, mutableListOf())
                    dependencies.add(ElementDependInfo(resolvedElement, it))
                    psiDependency.elementDependsList[field] = dependencies

                    // 更新 incomingList
                    val incomingList = psiDependency.elementIncomingList.getOrDefault(resolvedElement, mutableListOf())
                    incomingList.add(ElementDependInfo(field, it))
                    psiDependency.elementIncomingList[resolvedElement] = incomingList

                    // 递归探索依赖
                    if (isElementInProject(resolvedElement, project) && resolvedElement.containingFile == containingFile) {
                        var childNode: NodeInfo? = null
                        when (resolvedElement) {
                            is PsiMethod -> childNode = NodeInfo(resolvedElement)
                            is PsiClass -> childNode = NodeInfo(resolvedElement)
                            is PsiField -> childNode = NodeInfo(resolvedElement)
                        }
                        exploreDependenciesRecursive(resolvedElement, psiDependency, project, childNode, containingFile)

                        if (curNode != null && childNode != null) {
                            if (childNode.hasDependencies()) {
                                curNode.childrenNodes.add(childNode)
                                // 合并子节点的标志
                                curNode.childHasRpc = curNode.childHasRpc || childNode.childHasRpc || childNode.hasRpc
                                curNode.childHasMybatis = curNode.childHasMybatis || childNode.childHasMybatis || childNode.hasMybatis
                                curNode.childHasKafka = curNode.childHasKafka || childNode.childHasKafka || childNode.hasKafka
                                curNode.childHasRedis = curNode.childHasRedis || childNode.childHasRedis || childNode.hasRedis
                                curNode.childHasAries = curNode.childHasAries || childNode.childHasAries || childNode.hasAries
                                curNode.childHasLog = curNode.childHasLog || childNode.childHasLog || childNode.hasLog
                            }
                        }
                    }
                }
            }
        }

        /**
         * 通用的递归依赖探索方法
         */
        private fun exploreDependenciesRecursive(
            element: PsiElement,
            psiDependency: PsiDependency,
            project: Project,
            curNode: NodeInfo?,
            containingFile: PsiFile
        ) {
            when (element) {
                is PsiMethod -> exploreMethodDependencies(element, psiDependency, project, curNode, containingFile)
                is PsiClass -> exploreClassDependencies(element, psiDependency, project, curNode, containingFile)
                is PsiField -> exploreFieldDependencies(element, psiDependency, project, curNode, containingFile)
                else -> {
                    // 处理其他类型的 PsiElement
                }
            }
        }

        /**
         * 简化文件，根据依赖关系只保留相关的类、方法、字段和导入语句
         */
        fun simplifyFileByDependency(
            containingFile: PsiFile,
            dependency: PsiDependency,
            project: Project
        ): PsiElement {
            val copyFile = containingFile.copy() as PsiFile
            val processingClasses: MutableList<PsiClass> = mutableListOf()

            WriteCommandAction.runWriteCommandAction(project) {
                val relevantImports = mutableListOf<PsiImportStatement>()
                copyFile.children.forEach { child ->
                    when (child) {
                        is PsiClass -> {
                            deleteUnusedElements(child, dependency, processingClasses, relevantImports)
                        }
                        is PsiImportList, is PsiPackageStatement -> {
                            // 保留包声明和处理导入
                        }
                        else -> {
                            // 删除其他不相关的元素
                            child.delete()
                        }
                    }
                }

                // 清理导入，只保留相关的导入
                val importList = PsiTreeUtil.findChildOfType(copyFile, PsiImportList::class.java)
                importList?.allImportStatements?.forEach { importStatement ->
                    if (!relevantImports.any { it.isEquivalentTo(importStatement) }) {
                        importStatement.delete()
                    }
                }
            }

            return copyFile
        }

        /**
         * 删除不相关的元素，只保留依赖中的类、方法、字段
         */
        private fun deleteUnusedElements(
            it: PsiElement,
            dependency: PsiDependency,
            processingClass: MutableList<PsiClass>,
            relevantImports: MutableList<PsiImportStatement>
        ) {
            when (it) {
                is PsiMethod -> {
                    if (!dependency.psiElementList.any { element -> element.isEquivalentTo(it) }) {
                        it.delete()
                    } else {
                        addToList(it, relevantImports)
                    }
                }

                is PsiField -> {
                    if (!dependency.psiElementList.any { element -> element.isEquivalentTo(it) }) {
                        it.delete()
                    } else {
                        addToList(it, relevantImports)
                    }
                }

                is PsiClass -> {
                    if (dependency.psiElementList.any { element -> element.isEquivalentTo(it) }) {
                        addToList(it, relevantImports)
                        return
                    } else if (!processingClass.any { cls -> cls.isEquivalentTo(it) }) {
                        processingClass.add(it)
                        getClassSignatureElements(it).forEach { tmp ->
                            addToList(tmp, relevantImports)
                        }
                        it.children.forEach { tmp ->
                            deleteUnusedElements(tmp, dependency, processingClass, relevantImports)
                        }
                    }
                }
            }
        }

        /**
         * 添加相关导入到列表，避免重复
         */
        private fun addToList(
            it: PsiElement,
            relevantImports: MutableList<PsiImportStatement>
        ) {
            val imports = PsiUtils.getRelevantImportsForElement(it)
            imports.forEach { importStatement ->
                if (!relevantImports.any { existingImport -> existingImport.isEquivalentTo(importStatement) }) {
                    relevantImports.add(importStatement)
                }
            }
        }

        /**
         * 获取类的签名部分元素，用于保留类定义
         */
        private fun getClassSignatureElements(psiClass: PsiClass): List<PsiElement> {
            val signatureElements = mutableListOf<PsiElement>()

            psiClass.children.forEach { element ->
                when (element) {
                    is PsiModifierList,    // 修饰符列表，例如 public, abstract
                    is PsiIdentifier,      // 类名
                    is PsiTypeParameterList, // 类型参数（泛型）
                    is PsiReferenceList -> { // extends 和 implements 列表
                        signatureElements.add(element)
                    }
                    is PsiKeyword -> {
                        // 类关键字（如 class, interface 等）
                        signatureElements.add(element)
                    }
                }
                if (element.text == "{") {
                    // 停止在类体的起始大括号处
                    return@forEach
                }
            }

            return signatureElements
        }

        /**
         * 查找所有相关的依赖元素，包括类、方法、字段
         */
        private fun findDependencies(
            elements: List<PsiElement>,
            project: Project
        ): PsiDependency {
            val psiDependency = PsiDependency()
            elements.forEach { element ->
                when (element) {
                    is PsiMethod -> {
                        val methodTree = NodeInfo(element)
                        psiDependency.methodTree = methodTree
                        exploreMethodDependencies(element, psiDependency, project, methodTree, element.containingFile)
                    }
                    is PsiClass -> {
                        val classTree = NodeInfo(element)
                        psiDependency.classTree = classTree
                        exploreClassDependencies(element, psiDependency, project, classTree, element.containingFile)
                    }
                    is PsiField -> {
                        val fieldTree = NodeInfo(element)
                        psiDependency.fieldTree = fieldTree
                        exploreFieldDependencies(element, psiDependency, project, fieldTree, element.containingFile)
                    }
                    else -> {
                        // 处理其他可能的 PsiElement 类型
                    }
                }
            }
            return psiDependency
        }

        /**
         * 判断方法调用是否为日志方法调用
         */
        private fun isLogMethodCall(methodCall: PsiMethodCallExpression): Boolean {
            val qualifierExpression = methodCall.methodExpression.qualifierExpression

            if (qualifierExpression is PsiReferenceExpression) {
                val resolvedElement = qualifierExpression.resolve()
                if (resolvedElement is PsiField) {
                    val fieldType = resolvedElement.type.canonicalText
                    return fieldType in listOf(
                        "org.slf4j.Logger",
                        "org.apache.logging.log4j.Logger",
                        "org.apache.log4j.Logger",
                        "java.util.logging.Logger",
                        "ch.qos.logback.classic.Logger"
                    )
                }
            }

            return false
        }

        /**
         * 判断方法调用是否为 Aries 方法调用
         */
        private fun isAriesMethodCall(methodCall: PsiMethodCallExpression): Boolean {
            val qualifierExpression = methodCall.methodExpression.qualifierExpression

            if (qualifierExpression is PsiReferenceExpression) {
                val resolvedElement = qualifierExpression.resolve()
                if (resolvedElement is PsiField) {
                    val fieldType = resolvedElement.type.canonicalText
                    return fieldType == "com.yupaopao.framework.spring.boot.aries.AriesTemplate"
                }
            }

            return false
        }

        /**
         * 判断方法调用是否为 Redis 方法调用
         */
        private fun isRedisMethodCall(methodCall: PsiMethodCallExpression): Boolean {
            val qualifierExpression = methodCall.methodExpression.qualifierExpression

            if (qualifierExpression is PsiReferenceExpression) {
                val resolvedElement = qualifierExpression.resolve()
                if (resolvedElement is PsiField) {
                    val fieldType = resolvedElement.type.canonicalText
                    return fieldType == "com.yupaopao.framework.spring.boot.redis.RedisService" ||
                           fieldType.startsWith("org.springframework.data.redis.core.RedisTemplate<") ||
                           fieldType == "org.redisson.api.RedissonClient"
                }
            }

            return false
        }

        /**
         * 判断方法调用是否为 Kafka 方法调用
         */
        private fun isKafkaMethodCall(methodCall: PsiMethodCallExpression): Boolean {
            val qualifierExpression = methodCall.methodExpression.qualifierExpression

            if (qualifierExpression is PsiReferenceExpression) {
                val resolvedElement = qualifierExpression.resolve()
                if (resolvedElement is PsiField) {
                    val fieldType = resolvedElement.type.canonicalText
                    return fieldType == "com.yupaopao.framework.spring.boot.kafka.KafkaProducer"
                }
            }

            return false
        }

        /**
         * 判断方法调用是否为 MyBatis 方法调用
         */
        private fun isMybatisMethodCall(it: PsiJavaCodeReferenceElement, project: Project): Boolean {
            val resolvedElement = it.resolve()

            if (resolvedElement is PsiMethod) {
                // 在此处实现 MyBatis 方法调用的具体判断逻辑
                // 例如，检查方法所属的类是否为 MyBatis 相关类
                // 以下是一个简单的示例：
                val containingClass = PsiTreeUtil.getParentOfType(it, PsiClass::class.java)
                if (containingClass != null) {
                    val qualifiedName = containingClass.qualifiedName
                    if (qualifiedName != null && qualifiedName.startsWith("org.mybatis")) {
                        return true
                    }
                }
            }

            return false
        }

        /**
         * 判断方法调用是否为 Dubbo Reference 方法调用
         */
        fun isDubboReferenceMethodCall(methodCall: PsiMethodCallExpression): Boolean {
            val qualifierExpression = methodCall.methodExpression.qualifierExpression

            if (qualifierExpression is PsiReferenceExpression) {
                val resolvedElement = qualifierExpression.resolve()
                if (resolvedElement is PsiField) {
                    val annotations = resolvedElement.annotations
                    return annotations.any { annotation ->
                        val qualifiedName = annotation.qualifiedName
                        qualifiedName == "org.apache.dubbo.config.annotation.DubboReference" ||
                                qualifiedName == "org.apache.dubbo.config.annotation.Reference"
                    }
                }
            }

            return false
        }

        /**
         * 判断元素是否在项目中
         */
        fun isElementInProject(element: PsiElement, project: Project): Boolean {
            val psiFile: PsiFile? = element.containingFile
            val virtualFile: VirtualFile? = psiFile?.virtualFile

            return virtualFile?.let {
                val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
                projectFileIndex.isInContent(it)
            } ?: false
        }

        /**
         * 判断元素是否为数据类
         */
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

        /**
         * 判断方法是否为 Getter 或 Setter
         */
        private fun ifGetterOrSetter(method: PsiMethod): Boolean {
            val name = method.name
            return (name.startsWith("get") && method.parameterList.isEmpty && method.returnType != PsiTypes.voidType()) ||
                   (name.startsWith("set") && method.parameterList.parametersCount == 1 && method.returnType == PsiTypes.voidType())
        }

        /**
         * 判断方法是否为标准的类方法（equals, hashCode, toString, canEqual）
         */
        private fun PsiMethod.isStandardClassMethod(): Boolean {
            return this.name in listOf("equals", "hashCode", "toString", "canEqual")
        }
    }
}
