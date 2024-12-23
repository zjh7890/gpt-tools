package com.github.zjh7890.gpttools.kotlin.context

import com.github.zjh7890.gpttools.context.ClassContext
import com.github.zjh7890.gpttools.context.builder.ClassContextBuilder
import com.github.zjh7890.gpttools.kotlin.util.KotlinPsiUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtParameter

class KotlinClassContextBuilder : ClassContextBuilder {

    private fun getPrimaryConstructorFields(kotlinClass: KtClassOrObject): List<KtParameter> {
        return kotlinClass.getPrimaryConstructorParameters().filter { it.hasValOrVar() }
    }

    override fun getClassContext(psiElement: PsiElement, gatherUsages: Boolean): ClassContext? {
        if (psiElement !is KtClassOrObject) return null

        val text = psiElement.text
        val name = psiElement.name
        val functions = KotlinPsiUtil.getFunctions(psiElement)
        val allFields = getPrimaryConstructorFields(psiElement)
        val usages =
            if (gatherUsages) ClassContextBuilder.findUsages(psiElement as PsiNameIdentifierOwner) else emptyList()

        val annotations: List<String> = psiElement.annotationEntries.mapNotNull {
            it.text
        }

        val displayName = psiElement.fqName?.asString() ?: psiElement.name ?: ""
        return ClassContext(
            psiElement,
            text,
            name,
            functions,
            allFields,
            null,
            usages,
            displayName = displayName,
            annotations
        )
    }
}