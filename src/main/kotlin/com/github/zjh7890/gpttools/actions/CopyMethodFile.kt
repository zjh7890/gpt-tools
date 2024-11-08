package com.github.zjh7890.gpttools.actions

import com.baomidou.plugin.idea.mybatisx.dom.model.IdDomElement
import com.baomidou.plugin.idea.mybatisx.service.JavaService
import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.github.zjh7890.gpttools.utils.PsiUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.CommonProcessors
import com.github.zjh7890.gpttools.settings.other.OtherSettingsState



class CopyMethodSingleFile : AnAction() {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val settings = OtherSettingsState.getInstance()
        e.presentation.isVisible = settings.showCopyMethodSingleFile
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

        // 查找包含光标的 method
        val method = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java) ?: return

        val dependency = findMethodDependency(method, project)

//        val psiFields = findDubboReferenceFields(dependency)
//        val callsLinks = generateRpcCallsLinksFromTree(dependency.methodTree)
//        val message = callsLinks.joinToString("\n")

        val element = simplyFileByDependency(method.containingFile!!, dependency, project)
        val message = element.text
        ClipboardUtils.copyToClipboard(message)
    }

    fun generateRpcCallsLinksFromTree(node: NodeInfo): List<String> {
        val links = mutableListOf<String>()

        // 处理当前节点的 rpcCalls
        node.rpcCalls.forEach { rpcCall ->
            val psiMethod = rpcCall.resolveMethod() ?: return@forEach

            // 获取 PsiMethod 的包名、类名、方法名和参数类型
            val containingClass = psiMethod.containingClass ?: return@forEach
            val methodName = psiMethod.name
            val packageName = (containingClass.containingFile as? PsiJavaFile)?.packageName ?: return@forEach
            val className = containingClass.name ?: return@forEach

            // 获取方法的参数类型列表
            val parameterTypes = psiMethod.parameterList.parameters.joinToString(", ") { parameter ->
                parameter.type.presentableText
            }

            // 构建链接格式的字符串，包含参数类型
            val link = "{@link $packageName.$className#$methodName($parameterTypes)}"
            links.add(link)
        }

        // 递归处理子节点
        node.childrenNodes.forEach { childNode ->
            links.addAll(generateRpcCallsLinksFromTree(childNode))
        }

        return links
    }

    fun findDubboReferenceFields(dependency: PsiDependency): List<PsiField> {
        return dependency.psiElementList.filterIsInstance<PsiField>().filter { field ->
            field.annotations.any { annotation ->
                val qualifiedName = annotation.qualifiedName
                qualifiedName == "org.apache.dubbo.config.annotation.DubboReference" ||
                        qualifiedName == "org.apache.dubbo.config.annotation.Reference"
            }
        }
    }

    private fun simplyFileByDependency(containingFile: PsiFile, dependency: PsiDependency, project: Project): PsiElement {
        val copyFile = containingFile.copy()
        val processingClass: MutableList<PsiClass> = mutableListOf()
        WriteCommandAction.runWriteCommandAction(project) {
            val relevantImports = mutableListOf<PsiImportStatement>()
            copyFile.children.forEach {
                deleteUnusedElements(it, dependency, processingClass, relevantImports)
            }
            // 去除 copyFile 中不在 relevantImports 中的 import
            val importList = PsiTreeUtil.findChildOfType(copyFile, PsiImportList::class.java)
            importList?.allImportStatements?.forEach { importStatement ->
                if (!relevantImports.any { it.isEquivalentTo(importStatement) }) {
                    importStatement.delete()
                }
            }
        }
        return copyFile!!
    }

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

    fun getClassSignatureElements(psiClass: PsiClass): List<PsiElement> {
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

    private fun addToList(
        it: PsiElement,
        relevantImports: MutableList<PsiImportStatement>
    ) {
        // 加到 relevantImports 注意去重
        val imports = PsiUtils.getRelevantImportsForElement(it)
        // 将相关的导入语句添加到 relevantImports 列表，并去重
        imports.forEach { importStatement ->
            if (!relevantImports.any { existingImport -> existingImport.isEquivalentTo(importStatement) }) {
                relevantImports.add(importStatement)
            }
        }
    }

    private fun findMethodDependency(method: PsiMethod, project: Project): PsiDependency {
        val methodTree = NodeInfo(method)
        val psiDependency = PsiDependency(methodTree = methodTree)
        exploreMethodDependencies(method, psiDependency, project, methodTree)

        return psiDependency
    }

    private fun exploreMethodDependencies(
        element: PsiElement,
        psiDependency: PsiDependency,
        project: Project,
        curNode: NodeInfo?
    ) {
        // 如果元素已经在 psiElementList 中，跳过以避免重复处理
        if (element in psiDependency.psiElementList) {
            return
        }

        // 将 PsiElement 添加到 psiElementList 中去重
        psiDependency.psiElementList.add(element)

        // 维护 psiFileList 和 psiClassList
        val containingFile = element.containingFile
        if (containingFile != null && containingFile !in psiDependency.psiFileList) {
            psiDependency.psiFileList.add(containingFile)
        }

        // 如果 element 本身是 PsiClass，则将其添加到 psiClassList
        if (element is PsiClass) {
            if (element !in psiDependency.psiClassList) {
                psiDependency.psiClassList.add(element)
            }
        } else {
            val containingClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
            if (containingClass != null && containingClass !in psiDependency.psiClassList) {
                psiDependency.psiClassList.add(containingClass)
            }
        }

        val processedMethodCalls: MutableList<PsiMethodCallExpression> = mutableListOf()

        // 查找元素中的引用，并将其解析为 PsiElement
        PsiTreeUtil.findChildrenOfType(element, PsiJavaCodeReferenceElement::class.java).forEach {
            when (it) {
                is PsiJavaCodeReferenceElement -> {
                    val resolvedElement = it.resolve()
                    if (resolvedElement is PsiMethod || resolvedElement is PsiField || isDataClass(resolvedElement)) {
                        // 使用 getOrDefault 确保得到的是一个可变的列表
                        val dependencies = psiDependency.elementDependsList.getOrDefault(element, mutableListOf())
                        dependencies.add(ElementDependInfo(resolvedElement!!, it))
                        psiDependency.elementDependsList[element] = dependencies

                        // 更新 incomingList
                        val incomingList = psiDependency.elementIncomingList.getOrDefault(resolvedElement, mutableListOf())
                        incomingList.add(ElementDependInfo(element, it))
                        psiDependency.elementIncomingList[resolvedElement] = incomingList

                        if (curNode != null) {
                            val methodCallExpression = PsiTreeUtil.getParentOfType(it, PsiMethodCallExpression::class.java)
                            if (methodCallExpression != null && !processedMethodCalls.contains(methodCallExpression)) {
                                // 如果祖先节点存在 PsiMethodCallExpression 且未处理过

                                // 标记这个方法调用表达式为已处理
                                processedMethodCalls.add(methodCallExpression)

                                if (isDubboReferenceMethodCall(methodCallExpression)) {
                                    curNode.rpcCalls.add(methodCallExpression)
                                    curNode.calls.add(methodCallExpression)
                                    curNode.hasRpc = true
                                } else if (isMybatisMethodCall(it, project)) {
                                    curNode.mybatisCalls.add(methodCallExpression)
                                    curNode.calls.add(methodCallExpression)
                                    curNode.hasMybatis = true
                                } else if (isKafkaMethodCall(methodCallExpression)) {
                                    curNode.kafkaCalls.add(methodCallExpression)
                                    curNode.calls.add(methodCallExpression)
                                    curNode.hasKafka = true
                                } else if (isRedisMethodCall(methodCallExpression)) {
                                    curNode.redisCalls.add(methodCallExpression)
                                    curNode.calls.add(methodCallExpression)
                                    curNode.hasRedis = true
                                } else if (isAriesMethodCall(methodCallExpression)) {
                                    curNode.ariesCalls.add(methodCallExpression)
                                    curNode.calls.add(methodCallExpression)
                                    curNode.hasAries = true
                                } else if (isLogMethodCall(methodCallExpression)) {
                                    curNode.logCalls.add(methodCallExpression)
                                    curNode.calls.add(methodCallExpression)
                                    curNode.hasLog = true
                                }
                            }
                        }

                        // 使用递归调用进行深度遍历
                        if (isElementInProject(resolvedElement, project)) {

                            var childNode: NodeInfo? = null
                            if (element is PsiMethod) {
                                childNode = NodeInfo(element)
                            }
                            exploreMethodDependencies(resolvedElement, psiDependency, project, childNode)

                            if (curNode != null) {
                                if (childNode != null) {
                                    if (childNode.childHasRpc || childNode.hasRpc
                                        || childNode.childHasMybatis || childNode.hasMybatis
                                        || childNode.childHasKafka || childNode.hasKafka
                                        || childNode.childHasRedis || childNode.hasRedis
                                        || childNode.childHasAries || childNode.hasAries
                                        || childNode.childHasLog || childNode.hasLog) {
                                        // 在 methodTree 中找到 parentNode 的子节点列表
                                        // 将子节点的信息合并到当前节点
                                        curNode.childHasRpc = curNode.childHasRpc || childNode.childHasRpc || childNode.hasRpc
                                        curNode.childHasMybatis = curNode.childHasMybatis || childNode.childHasMybatis || childNode.hasMybatis
                                        curNode.childHasKafka = curNode.childHasKafka || childNode.childHasKafka || childNode.hasKafka
                                        curNode.childHasRedis = curNode.childHasRedis || childNode.childHasRedis || childNode.hasRedis
                                        curNode.childHasAries = curNode.childHasAries || childNode.childHasAries || childNode.hasAries
                                        curNode.childHasLog = curNode.childHasLog || childNode.childHasLog || childNode.hasLog
                                        curNode.childrenNodes.add(childNode)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isLogMethodCall(methodCall: PsiMethodCallExpression): Boolean {
        // 获取调用者表达式
        val qualifierExpression = methodCall.methodExpression.qualifierExpression

        // 确保调用者表达式存在并且是一个字段引用
        if (qualifierExpression is PsiReferenceExpression) {
            // 解析引用到的元素
            val resolvedElement = qualifierExpression.resolve()

            // 检查解析到的元素是否是字段
            if (resolvedElement is PsiField) {
                // 获取字段的类型
                val fieldType = resolvedElement.type
                val fieldTypeCanonicalText = fieldType.canonicalText

                // 检查字段类型是否为常见的 Logger 类
                if (fieldTypeCanonicalText == "org.slf4j.Logger" ||
                    fieldTypeCanonicalText == "org.apache.logging.log4j.Logger" ||
                    fieldTypeCanonicalText == "org.apache.log4j.Logger" ||
                    fieldTypeCanonicalText == "java.util.logging.Logger" ||
                    fieldTypeCanonicalText == "ch.qos.logback.classic.Logger") {
                    return true
                }
            }
        }

        // 如果不是字段引用或者类型不匹配，返回 false
        return false
    }

    private fun isAriesMethodCall(methodCall: PsiMethodCallExpression): Boolean {
        // 获取调用者表达式
        val qualifierExpression = methodCall.methodExpression.qualifierExpression

        // 确保调用者表达式存在并且是一个字段引用
        if (qualifierExpression is PsiReferenceExpression) {
            // 解析引用到的元素
            val resolvedElement = qualifierExpression.resolve()

            // 检查解析到的元素是否是字段
            if (resolvedElement is PsiField) {
                // 获取字段的类型
                val fieldType = resolvedElement.type
                val fieldTypeCanonicalText = fieldType.canonicalText

                // 检查字段类型是否为 AriesTemplate
                if (fieldTypeCanonicalText == "com.yupaopao.framework.spring.boot.aries.AriesTemplate") {
                    return true
                }
            }
        }

        // 如果不是字段引用或者类型不匹配，返回 false
        return false
    }

    private fun isRedisMethodCall(methodCall: PsiMethodCallExpression): Boolean {
        // 获取调用者表达式
        val qualifierExpression = methodCall.methodExpression.qualifierExpression

        // 确保调用者表达式存在并且是一个字段引用
        if (qualifierExpression is PsiReferenceExpression) {
            // 解析引用到的元素
            val resolvedElement = qualifierExpression.resolve()

            // 检查解析到的元素是否是字段
            if (resolvedElement is PsiField) {
                // 获取字段的类型
                val fieldType = resolvedElement.type
                val fieldTypeCanonicalText = fieldType.canonicalText

                // 检查字段类型是否为 RedisService、RedisTemplate 或 RedissonClient
                if (fieldTypeCanonicalText == "com.yupaopao.framework.spring.boot.redis.RedisService" ||
                    fieldTypeCanonicalText == "org.springframework.data.redis.core.RedisTemplate<*>" || // 处理泛型情况
                    fieldTypeCanonicalText.startsWith("org.springframework.data.redis.core.RedisTemplate<") ||
                    fieldTypeCanonicalText == "org.redisson.api.RedissonClient") { // RedissonClient 类型检查
                    return true
                }
            }
        }

        // 如果不是字段引用或者类型不匹配，返回 false
        return false
    }

    private fun isKafkaMethodCall(methodCall: PsiMethodCallExpression): Boolean {
        // 获取调用者表达式
        val qualifierExpression = methodCall.methodExpression.qualifierExpression

        // 确保调用者表达式存在并且是一个字段引用
        if (qualifierExpression is PsiReferenceExpression) {
            // 解析引用到的元素
            val resolvedElement = qualifierExpression.resolve()

            // 检查解析到的元素是否是字段
            if (resolvedElement is PsiField) {
                // 获取字段的类型
                val fieldType = resolvedElement.type

                // 检查字段类型是否为 com.yupaopao.framework.spring.boot.kafka.KafkaProducer
                if (fieldType.canonicalText == "com.yupaopao.framework.spring.boot.kafka.KafkaProducer") {
                    return true
                }
            }
        }

        // 如果不是字段引用或者类型不匹配，返回 false
        return false
    }

    private fun isMybatisMethodCall(it: PsiJavaCodeReferenceElement, project: Project): Boolean {
        val resolvedElement = it.resolve()

        // 检查 resolvedElement 是否为 PsiMethod
        if (resolvedElement is PsiMethod) {
            // 使用属性访问语法代替 getter
            val processor = CommonProcessors.CollectProcessor<IdDomElement>()
            JavaService.getInstance(it.project).processMethod(resolvedElement, processor)
            return processor.getResults().size > 0
        }
        // 如果 resolvedElement 不是 PsiMethod，返回 false 或者进行其他处理
        return false
    }

    fun isDubboReferenceMethodCall(methodCall: PsiMethodCallExpression): Boolean {
        // 获取调用者表达式
        val qualifierExpression = methodCall.methodExpression.qualifierExpression

        // 确保调用者表达式存在并且是一个字段引用
        if (qualifierExpression is PsiReferenceExpression) {
            // 解析引用到的元素
            val resolvedElement = qualifierExpression.resolve()

            // 检查解析到的元素是否是字段
            if (resolvedElement is PsiField) {
                // 获取字段上的注解
                val annotations = resolvedElement.annotations

                // 检查是否有 @DubboReference 或 @Reference 注解
                return annotations.any { annotation ->
                    val qualifiedName = annotation.qualifiedName
                    qualifiedName == "org.apache.dubbo.config.annotation.DubboReference" ||
                            qualifiedName == "org.apache.dubbo.config.annotation.Reference"
                }
            }
        }

        // 如果不是字段引用或者没有注解，返回 false
        return false
    }

    fun isElementInProject(element: PsiElement, project: Project): Boolean {
        val psiFile: PsiFile? = element.containingFile
        val virtualFile: VirtualFile? = psiFile?.virtualFile

        if (virtualFile != null) {
            val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
            return projectFileIndex.isInContent(virtualFile)
        }

        return false
    }

    private fun isDataClass(element: PsiElement?): Boolean {
        if (element !is PsiClass) return false

        val methods = element.methods
        val fields = element.fields

        // 如果类没有字段，通常不是数据类
        if (fields.isEmpty()) return false

        // 判断是否所有方法都是 getter、setter 或标准的类方法（如 equals、hashCode、toString）
        val allMethodsAreGettersSettersOrStandard = methods.all {
            ifGetterOrSetter(it) || it.isStandardClassMethod() || it.isConstructor
        }

        return allMethodsAreGettersSettersOrStandard
    }

    private fun ifGetterOrSetter(method: PsiMethod): Boolean {
        val name = method.name
        // 检查方法是否符合 getter 或 setter 的标准签名
        return (name.startsWith("get") && method.parameterList.isEmpty && method.returnType != PsiTypes.voidType()) ||
                (name.startsWith("set") && method.parameterList.parametersCount == 1 && method.returnType == PsiTypes.voidType())
    }

    private fun PsiMethod.isStandardClassMethod(): Boolean {
        return when (this.name) {
            "equals", "hashCode", "toString", "canEqual" -> true
            else -> false
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
