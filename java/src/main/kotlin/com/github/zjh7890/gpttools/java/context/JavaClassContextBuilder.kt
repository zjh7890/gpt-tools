package com.github.zjh7890.gpttools.java.context

import com.github.zjh7890.gpttools.context.ClassContext
import com.github.zjh7890.gpttools.context.builder.ClassContextBuilder
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner

class JavaClassContextBuilder : ClassContextBuilder {
    override fun getClassContext(psiElement: PsiElement, gatherUsages: Boolean): ClassContext? {
        if (psiElement !is PsiClass) return null

        val supers = runReadAction {  psiElement.extendsList?.referenceElements?.mapNotNull {
            it.text
        }}

        val fields = psiElement.fields.toList()
        val methods = psiElement.methods.toList()

        val usages =
            if (gatherUsages) ClassContextBuilder.findUsages(psiElement as PsiNameIdentifierOwner) else emptyList()

        val annotations: List<String> = psiElement.annotations.mapNotNull {
            it.text
        }

        return ClassContext(
            psiElement, psiElement.text, psiElement.name, methods, fields, supers, usages,
            displayName = runReadAction { psiElement.qualifiedName },
            annotations
        )
    }
}
