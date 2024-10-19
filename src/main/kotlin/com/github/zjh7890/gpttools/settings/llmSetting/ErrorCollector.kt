package com.github.zjh7890.gpttools.settings.llmSetting

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.Processor

class ErrorCollector {
    /**
     * 获取指定 PsiFile 中的所有错误和警告信息
     *
     * @param psiFile 要检查的 PsiFile
     * @return 包含所有错误和警告的 HighlightInfo 列表
     */
    fun getErrorsAndWarnings(psiFile: PsiFile): List<HighlightInfo> {
        val project: Project = psiFile.project
        val document: Document? = PsiDocumentManager.getInstance(project).getDocument(psiFile)

        if (document == null) {
            println("Document is null for the provided PsiFile.")
            return emptyList()
        }

        val highlights = mutableListOf<HighlightInfo>()

        // 定义一个处理器，用于收集高亮信息
        val processor = Processor<HighlightInfo> { highlightInfo ->
            // 过滤出错误和警告
            if (highlightInfo.severity == HighlightSeverity.ERROR ||
                highlightInfo.severity == HighlightSeverity.WARNING
            ) {
                highlights.add(highlightInfo)
            }
            true // 继续处理其他高亮信息
        }

        // 获取 DaemonCodeAnalyzerEx 实例
        val daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project)

        // 获取文件的文本范围
        val textRange = TextRange(0, document.textLength)

        // 处理高亮信息
        val success = DaemonCodeAnalyzerEx.processHighlights(
            document,
            project,
            HighlightSeverity.WARNING, // 设置最小严重性为 WARNING，包含 ERROR 和 WARNING
            textRange.startOffset,
            textRange.endOffset,
            processor
        )

        if (!success) {
            println("Failed to process highlights.")
        }

        return highlights
    }
}