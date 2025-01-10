package com.github.zjh7890.gpttools.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.openapi.ui.Messages

class FindTopLevelJavaFilesAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val topLevelFiles = findTopLevelJavaFiles(project)

        // 构建结果消息
        val message = if (topLevelFiles.isEmpty()) {
            "未找到顶级 Java 文件。"
        } else {
            "顶级 Java 文件列表：\n" + topLevelFiles.joinToString("\n") { it.virtualFile.path }
        }

        // 显示结果
        Messages.showInfoMessage(project, message, "顶级 Java 文件")
    }

    /**
     * 查找项目中所有未被其他非测试 Java 文件引用的 Java 文件
     */
    private fun findTopLevelJavaFiles(project: Project): List<PsiJavaFile> {
        val psiManager = PsiManager.getInstance(project)
        val javaFiles = mutableListOf<PsiJavaFile>()

        // 获取所有 Java 文件
        val fileIndex = com.intellij.openapi.roots.ProjectRootManager.getInstance(project).fileIndex
        fileIndex.iterateContent { virtualFile ->
            if (virtualFile.extension == "java") {
                val psiFile = psiManager.findFile(virtualFile)
                if (psiFile is PsiJavaFile) {
                    javaFiles.add(psiFile)
                }
            }
            true
        }

        // 过滤出顶级文件
        return javaFiles.filter { javaFile ->
            val filePath = javaFile.virtualFile.path
            val isTestFile = filePath.contains("/test/")

            // 排除 test 目录下的文件
            if (isTestFile) {
                return@filter false
            }

            // 查找引用该文件的非测试文件
            val references = ReferencesSearch.search(javaFile, GlobalSearchScope.projectScope(project), false)

            val isReferenced = references.any { reference ->
                val refElement = reference.element
                val refFile = refElement.containingFile
                refFile is PsiJavaFile && !refFile.virtualFile.path.contains("/test/")
            }

            // 仅保留未被引用的文件
            !isReferenced
        }
    }
}
