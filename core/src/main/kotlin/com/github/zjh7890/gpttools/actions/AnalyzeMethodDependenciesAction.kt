package com.github.zjh7890.gpttools.actions

import com.github.zjh7890.gpttools.services.SessionManager
import com.github.zjh7890.gpttools.toolWindow.treePanel.ClassDependencyInfo
import com.github.zjh7890.gpttools.toolWindow.treePanel.FileTreeListPanel
import com.github.zjh7890.gpttools.utils.PsiUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

/**
 * 一个示例性的 Action。
 * 点击后调用 analyzeMethodDependencies 分析方法依赖，并将结果添加到 SessionManager 的当前会话中。
 */
class AnalyzeMethodDependenciesAction : AnAction("Analyze Method Dependencies") {

    override fun actionPerformed(e: AnActionEvent) {
        // 1. 获取当前的 Project
        val project = e.project ?: return

        // 2. 获取当前光标处的 PsiMethod
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset)
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java) ?: return

        // 3. 获取方法所在的类
        val psiClass = method.containingClass ?: return

        // 4. 判断是否是 data class
        if (PsiUtils.isAtomicClass(psiClass)) {
            // 如果是 data class，直接添加整个文件
            val virtualFile = psiFile.virtualFile
            if (virtualFile != null) {
                val sessionManager = SessionManager.getInstance(project)
                sessionManager.addFileToCurrentSession(virtualFile)
            }
            return
        }

        // 5. 如果不是 data class，按原来的逻辑处理
        val classDependencyGraph = mutableMapOf<PsiClass, ClassDependencyInfo>()
        FileTreeListPanel.analyzeMethodDependencies(method, psiClass, classDependencyGraph)

        // 6. 分析完成后，将依赖图中的所有方法加入到当前会话
        val sessionManager = SessionManager.getInstance(project)
        for ((key, dependencyInfo) in classDependencyGraph) {
            if (dependencyInfo.isAtomicClass) {
                sessionManager.addClassToCurrentSession(key)
            } else {
                dependencyInfo.usedMethods.forEach { usedMethod ->
                    sessionManager.addMethodToCurrentSession(usedMethod)
                }
            }
        }
    }
}
