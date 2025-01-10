package com.github.zjh7890.gpttools.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.openapi.ui.Messages

class FindTopLevelJavaFilesAction : AnAction() {
    // 定义标识 Spring Bean 的注解列表
    private val springAnnotations = listOf(
        "org.springframework.stereotype.Component",
        "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Controller",
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.stereotype.Repository",
        "org.springframework.context.annotation.Configuration",
        "org.springframework.boot.autoconfigure.SpringBootApplication"
    )

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val topLevelFiles = findTopLevelSpringJavaFiles(project)

        // 构建结果消息
        val message = if (topLevelFiles.isEmpty()) {
            "未找到符合条件的顶级 Spring Java 文件。"
        } else {
            "符合条件的顶级 Spring Java 文件列表：\n" + topLevelFiles.joinToString("\n") { it.virtualFile.path }
        }

        // 显示结果
        Messages.showInfoMessage(project, message, "顶级 Spring Java 文件")
    }

    /**
     * 查找项目中所有未被其他非测试 Java 文件引用，并且包含 Spring 注解类的 Java 文件
     */
    private fun findTopLevelSpringJavaFiles(project: Project): List<PsiJavaFile> {
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

        // 过滤出符合条件的顶级 Spring 文件
        return javaFiles.filter { javaFile ->
            val filePath = javaFile.virtualFile.path
            val isTestFile = filePath.contains("/test/")

            // 排除 test 目录下的文件
            if (isTestFile) {
                return@filter false
            }

            // 获取文件中所有类
            val classes = javaFile.classes
            if (classes.isEmpty()) {
                // 如果文件中没有类，视为不符合条件
                return@filter false
            }

            // 检查是否至少有一个类具有 Spring 注解
            val hasSpringAnnotation = classes.any { psiClass ->
                psiClass.annotations.any { annotation ->
                    val qualifiedName = annotation.qualifiedName
                    qualifiedName != null && springAnnotations.contains(qualifiedName)
                }
            }

            if (!hasSpringAnnotation) {
                // 如果文件中没有类具有 Spring 注解，排除该文件
                return@filter false
            }

            // 检查是否有任何一个类被引用（由非测试文件引用）
            val isReferenced = classes.any { psiClass ->
                // 查找引用该类的非测试文件
                val references = ReferencesSearch.search(psiClass, GlobalSearchScope.projectScope(project), false)

                references.any { reference ->
                    val refElement = reference.element
                    val refFile = refElement.containingFile
                    refFile is PsiJavaFile && !refFile.virtualFile.path.contains("/test/")
                }
            }

            // 仅保留未被引用的文件
            !isReferenced
        }
    }
}
