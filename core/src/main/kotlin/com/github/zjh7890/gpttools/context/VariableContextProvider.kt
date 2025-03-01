package com.github.zjh7890.gpttools.context

import com.github.zjh7890.gpttools.context.base.LLMCodeContextProvider
import com.github.zjh7890.gpttools.context.builder.VariableContextBuilder
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiElement

class VariableContextProvider(
    private val includeMethodContext: Boolean,
    private val includeClassContext: Boolean,
    private val gatherUsages: Boolean
): LLMCodeContextProvider<PsiElement> {

    private val providers: List<VariableContextBuilder>

    init {
        val registeredLanguages = Language.getRegisteredLanguages()
        providers = registeredLanguages.mapNotNull(languageExtension::forLanguage)
    }

    override fun from(psiElement: PsiElement): VariableContext {
        for (provider in providers) {
            val variableContext =
                provider.getVariableContext(psiElement, includeMethodContext, includeClassContext, gatherUsages)

            if (variableContext != null) {
                return variableContext
            }
        }

        return VariableContext(psiElement, psiElement.text, null)
    }

    companion object {
        val languageExtension: LanguageExtension<VariableContextBuilder> =
            LanguageExtension("com.github.zjh7890.gpttools.variableContextBuilder")

        val EP_NAME: LanguageExtension<VariableContextBuilder> = languageExtension
    }
}

