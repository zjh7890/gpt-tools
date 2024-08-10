package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.utils.PsiUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import java.util.*
import kotlin.collections.ArrayDeque

class PsiDependencyByMethodAction : AnAction() {

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

        val psiFields = findDubboReferenceFields(dependency)

        val element = simplyFileByDependency(method.containingFile!!, dependency, project)
        val message = element.text
        println(message)
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
        val psiDependency = PsiDependency()
        val queue = ArrayDeque<PsiElement>() // 用于广度遍历的队列

        // 将初始方法加入队列
        queue.add(method)

        while (queue.isNotEmpty()) {
            val element = queue.removeFirst() // 取出队列中的第一个元素

            // 如果元素已经在 psiElementList 中，跳过以避免重复处理
            if (element in psiDependency.psiElementList) {
                continue
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

                            // 将解析出的元素添加到队列中，进行广度遍历
                            if (isElementInProject(resolvedElement, project)) {
                                queue.add(resolvedElement)
                            }
                        }
                    }
                }
            }
        }

        return psiDependency
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
        return (name.startsWith("get") && method.parameterList.isEmpty && method.returnType != PsiType.VOID) ||
                (name.startsWith("set") && method.parameterList.parametersCount == 1 && method.returnType == PsiType.VOID)
    }

    private fun PsiMethod.isStandardClassMethod(): Boolean {
        return when (this.name) {
            "equals", "hashCode", "toString", "canEqual" -> true
            else -> false
        }
    }
}

data class PsiDependency(
    // 记录 psiFile 的遍历合集，广度遍历过程中加入，需要去重
    val psiFileList: MutableList<PsiFile> = mutableListOf(),
    // 记录 psiClass 的遍历合集，广度遍历过程中加入，需要去重
    val psiClassList: MutableList<PsiClass> = mutableListOf(),
    // 记录 psiElement 的遍历合集，可以是 method, field, 或者是 data class, data class 定义为只有 getter setter 的类，广度遍历过程中加入，需要去重
    val psiElementList: MutableList<PsiElement> = mutableListOf(),
    // 记录 element 依赖的节点，可以是 method, field, 或者是 data class
    val elementDependsList: MutableMap<PsiElement, MutableList<ElementDependInfo>> = mutableMapOf(),
    // 记录依赖 element 的节点，对方可以是 method, field, 或者是 data class
    val elementIncomingList: MutableMap<PsiElement, MutableList<ElementDependInfo>> = mutableMapOf()
)

data class ElementDependInfo (
    val element: PsiElement,
    val referenceElement: PsiElement
)

data class DependencyNode(
    val element: PsiElement,
    val children: MutableList<DependencyNode> = mutableListOf()
)
