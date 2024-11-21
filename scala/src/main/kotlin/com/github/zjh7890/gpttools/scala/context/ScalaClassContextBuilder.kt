package com.github.zjh7890.gpttools.scala.context

import com.github.zjh7890.gpttools.context.ClassContext
import com.github.zjh7890.gpttools.context.builder.ClassContextBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

class ScalaClassContextBuilder : ClassContextBuilder {
    override fun getClassContext(psiElement: PsiElement, gatherUsages: Boolean): ClassContext? {
        if (psiElement !is ScClass) return null

        val name = psiElement.name ?: return null

        val functions = psiElement.methods.toList()
        val allFields = psiElement.fields.toList()
        val superClass = psiElement.supers.mapNotNull { it.qualifiedName }.toList()
        val usages = ClassContextBuilder.findUsages(psiElement as PsiNameIdentifierOwner)

        val annotations = psiElement.annotations.mapNotNull {
            it.text
        }

        val displayName = psiElement.qualifiedName ?: psiElement.name ?: ""
        return ClassContext(
            psiElement,
            psiElement.text,
            name,
            functions,
            allFields,
            superClass,
            usages,
            displayName = displayName,
            annotations
        )
    }
}
