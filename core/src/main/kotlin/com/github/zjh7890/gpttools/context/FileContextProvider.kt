package com.github.zjh7890.gpttools.context

import com.github.zjh7890.gpttools.context.base.LLMCodeContextProvider
import com.github.zjh7890.gpttools.context.builder.FileContextBuilder
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiFile

class FileContextProvider: LLMCodeContextProvider<PsiFile> {
    private val languageExtension: LanguageExtension<FileContextBuilder> =
        LanguageExtension("com.github.zjh7890.gpttools.fileContextBuilder")

    private val providers: List<FileContextBuilder>

    init {
        val registeredLanguages = Language.getRegisteredLanguages()
        providers = registeredLanguages.mapNotNull { languageExtension.forLanguage(it) }
    }

    override fun from(psiElement: PsiFile): FileContext {
        for (provider in providers) {
            val fileContext = provider.getFileContext(psiElement)
            if (fileContext != null) {
                return fileContext
            }
        }

        return FileContext(psiElement, psiElement.name, psiElement.virtualFile?.path!!)
    }
}
