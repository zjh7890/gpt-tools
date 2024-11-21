package com.github.zjh7890.gpttools.javascript.context

import com.github.zjh7890.gpttools.context.VariableContext
import com.github.zjh7890.gpttools.context.builder.VariableContextBuilder
import com.intellij.lang.javascript.psi.JSFieldVariable
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.util.JSUtils
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil

class JavaScriptVariableContextBuilder : VariableContextBuilder {
    override fun getVariableContext(
        psiElement: PsiElement,
        withMethodContext: Boolean,
        withClassContext: Boolean,
        gatherUsages: Boolean
    ): VariableContext? {
        if (psiElement !is JSFieldVariable) {
            return null
        }

        val parentOfType: PsiElement? = PsiTreeUtil.getParentOfType(psiElement, JSFunction::class.java, true)
        val memberContainingClass: PsiElement = JSUtils.getMemberContainingClass(psiElement)
        val psiReferences: List<PsiReference> = if (gatherUsages) {
            findUsages(psiElement as PsiNameIdentifierOwner)
        } else {
            emptyList()
        }

        return VariableContext(
            psiElement,
            psiElement.getText(),
            psiElement.name!!,
            parentOfType,
            memberContainingClass,
            psiReferences,
            withMethodContext,
            withClassContext
        )
    }

}