package com.github.zjh7890.gpttools.context.builder

import com.github.zjh7890.gpttools.context.VariableContext
import com.intellij.psi.PsiElement

interface VariableContextBuilder {
    fun getVariableContext(
        psiElement: PsiElement,
        withMethodContext: Boolean,
        withClassContext: Boolean,
        gatherUsages: Boolean
    ): VariableContext?
}
