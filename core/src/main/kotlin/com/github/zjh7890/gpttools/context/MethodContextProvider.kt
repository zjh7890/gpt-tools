package com.github.zjh7890.gpttools.context

import com.github.zjh7890.gpttools.context.base.LLMCodeContextProvider
import com.github.zjh7890.gpttools.context.builder.MethodContextBuilder
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.NotNull

class MethodContextProvider(private val includeClassContext: Boolean, private val gatherUsages: Boolean):
    LLMCodeContextProvider<PsiElement> {
    @NotNull
    private val languageExtension: LanguageExtension<MethodContextBuilder> =
        LanguageExtension("com.github.zjh7890.gpttools.methodContextBuilder")

    @NotNull
    private var providers: List<MethodContextBuilder> = emptyList()

    init {
        val registeredLanguages = Language.getRegisteredLanguages()
        providers = registeredLanguages.mapNotNull(languageExtension::forLanguage)
    }

    @NotNull
    override fun from(@NotNull psiElement: PsiElement): MethodContext {
        val iterator = providers.iterator()
        while (iterator.hasNext()) {
            val provider = iterator.next()
            val methodContext = provider.getMethodContext(psiElement, includeClassContext, gatherUsages)
            if (methodContext != null) {
                return methodContext
            }
        }

        return MethodContext(psiElement, psiElement.text, null)
    }
}
