package com.github.zjh7890.gpttools.context.base;

import com.intellij.psi.PsiElement

interface LLMCodeContextProvider<T : PsiElement?> {
    fun from(psiElement: T): LLMCodeContext?
}

