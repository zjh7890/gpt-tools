package com.github.zjh7890.gpttools.services

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

class ClassDepsInSingleFileAction : AnAction() {
    override fun update(e: AnActionEvent) {
        super.update(e)
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

        // 查找包含光标的 class
        val psiClass = PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass::class.java) ?: return

        val message = classDepsInSingleFile(psiClass, project) ?: return
        ClipboardUtils.copyToClipboard(message)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    companion object {
        /**
         * 生成与指定类相关的简化文件内容，包括相关类和简化后的导入语句
         */
        fun classDepsInSingleFile(
            psiClass: PsiClass,
            project: Project
        ): String? {
            val dependency = findClassDependency(psiClass, project)
            val simplifiedElement = simplifyFileByDependency(psiClass.containingFile!!, dependency, project)
            return simplifiedElement.text
        }

        /**
         * 查找类的依赖关系
         */
        private fun findClassDependency(psiClass: PsiClass, project: Project): PsiDependency {
            val containingFile = psiClass.containingFile
            val classTree = NodeInfo(psiClass)
            val psiDependency = PsiDependency(methodTree = classTree)
            exploreClassDependencies(psiClass, psiDependency, project, classTree, containingFile)
            return psiDependency
        }

        /**
         * 递归探索类的依赖关系
         */
        private fun exploreClassDependencies(
            element: PsiElement,
            psiDependency: PsiDependency,
            project: Project,
            curNode: NodeInfo?,
            containingFile: PsiFile
        ) {
            if (element in psiDependency.psiElementList) {
                return
            }

            psiDependency.psiElementList.add(element)

            if (element.containingFile != containingFile) {
                return
            }

            if (containingFile !in psiDependency.psiFileList) {
                psiDependency.psiFileList.add(containingFile)
            }

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

            // 查找类中的引用
            PsiTreeUtil.findChildrenOfType(element, PsiJavaCodeReferenceElement::class.java).forEach {
                val resolvedElement = it.resolve()
                if (resolvedElement is PsiClass || resolvedElement is PsiField || resolvedElement is PsiMethod) {
                    // 添加依赖信息
                    val dependencies = psiDependency.elementDependsList.getOrDefault(element, mutableListOf())
                    dependencies.add(ElementDependInfo(resolvedElement!!, it))
                    psiDependency.elementDependsList[element] = dependencies

                    // 更新 incomingList
                    val incomingList = psiDependency.elementIncomingList.getOrDefault(resolvedElement, mutableListOf())
                    incomingList.add(ElementDependInfo(element, it))
                    psiDependency.elementIncomingList[resolvedElement] = incomingList

                    // 递归探索依赖
                    if (isElementInProject(resolvedElement, project) && resolvedElement.containingFile == containingFile) {
                        var childNode: NodeInfo? = null
                        if (resolvedElement is PsiClass) {
                            childNode = NodeInfo(resolvedElement)
                        }
                        exploreClassDependencies(resolvedElement, psiDependency, project, childNode, containingFile)

                        if (curNode != null && childNode != null) {
                            if (childNode.hasDependencies()) {
                                curNode.childrenNodes.add(childNode)
                            }
                        }
                    }
                }
            }
        }

        /**
         * 简化文件，根据依赖关系只保留相关的类和导入语句
         */
        private fun simplifyFileByDependency(containingFile: PsiFile, dependency: PsiDependency, project: Project): PsiElement {
            val copyFile = containingFile.copy() as PsiFile
            val processingClasses: MutableList<PsiClass> = mutableListOf()

            WriteCommandAction.runWriteCommandAction(project) {
                val relevantImports = mutableListOf<PsiImportStatement>()
                copyFile.children.forEach { child ->
                    if (child is PsiClass) {
                        deleteUnusedClasses(child, dependency, processingClasses, relevantImports)
                    } else {
                        // 保留非类的元素（如包声明）
                        if (child is PsiImportList || child is PsiPackageStatement) {
                            // 处理导入和包声明
                        } else {
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
         * 删除不相关的类及其成员，只保留依赖中的类
         */
        private fun deleteUnusedClasses(
            psiClass: PsiClass,
            dependency: PsiDependency,
            processingClasses: MutableList<PsiClass>,
            relevantImports: MutableList<PsiImportStatement>
        ) {
            if (dependency.psiClassList.any { it.isEquivalentTo(psiClass) }) {
                // 保留相关类，处理其成员
                psiClass.fields.forEach { field ->
                    if (!dependency.psiElementList.any { it.isEquivalentTo(field) }) {
                        field.delete()
                    } else {
                        addToImportList(field, relevantImports)
                    }
                }

                psiClass.methods.forEach { method ->
                    if (!dependency.psiElementList.any { it.isEquivalentTo(method) }) {
                        method.delete()
                    } else {
                        addToImportList(method, relevantImports)
                    }
                }

                psiClass.innerClasses.forEach { innerClass ->
                    if (!dependency.psiElementList.any { it.isEquivalentTo(innerClass) }) {
                        innerClass.delete()
                    } else {
                        addToImportList(innerClass, relevantImports)
                    }
                }
            } else {
                // 删除不相关的类
                psiClass.delete()
            }
        }

        /**
         * 添加相关导入到列表，避免重复
         */
        private fun addToImportList(
            element: PsiElement,
            relevantImports: MutableList<PsiImportStatement>
        ) {
            val imports = PsiUtils.getRelevantImportsForElement(element)
            imports.forEach { importStatement ->
                if (!relevantImports.any { it.isEquivalentTo(importStatement) }) {
                    relevantImports.add(importStatement)
                }
            }
        }

        /**
         * 检查元素是否在项目中
         */
        fun isElementInProject(element: PsiElement, project: Project): Boolean {
            val psiFile: PsiFile? = element.containingFile
            val virtualFile: VirtualFile? = psiFile?.virtualFile

            return virtualFile?.let {
                val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
                projectFileIndex.isInContent(it)
            } ?: false
        }
    }
}
