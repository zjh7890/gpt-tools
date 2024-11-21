package com.github.zjh7890.gpttools.context

import com.github.zjh7890.gpttools.context.base.LLMCodeContextProvider
import com.github.zjh7890.gpttools.context.builder.ClassContextBuilder
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiElement

class ClassContextProvider(private val gatherUsages: Boolean) : LLMCodeContextProvider<PsiElement> {
    private val languageExtension = LanguageExtension<ClassContextBuilder>("com.github.zjh7890.gpttools.classContextBuilder")
    private val providers: List<ClassContextBuilder>

    init {
        val registeredLanguages = Language.getRegisteredLanguages()
        providers = registeredLanguages.mapNotNull(languageExtension::forLanguage)
    }

    override fun from(psiElement: PsiElement): ClassContext {
        for (provider in providers) {
            try {
                provider.getClassContext(psiElement, gatherUsages)?.let {
                    return it
                }
            } catch (e: Exception) {
                logger<ClassContextProvider>().error("Error while getting class context from $provider", e)
            }
        }

        return ClassContext(psiElement, null, null)
    }
}
