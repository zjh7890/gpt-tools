package com.github.zjh7890.gpttools.goland.context

import com.github.zjh7890.gpttools.context.ClassContext
import com.github.zjh7890.gpttools.context.builder.ClassContextBuilder
import com.github.zjh7890.gpttools.goland.util.GoPsiUtil
import com.goide.psi.GoMethodDeclaration
import com.goide.psi.GoTypeDeclaration
import com.goide.psi.GoTypeSpec
import com.intellij.psi.PsiElement

class GoStructContextBuilder : ClassContextBuilder {
    override fun getClassContext(psiElement: PsiElement, gatherUsages: Boolean): ClassContext? {
        if (psiElement !is GoTypeDeclaration && psiElement !is GoTypeSpec) {
            return null
        }

        val typeSpecs: List<GoTypeSpec> = when (psiElement) {
            is GoTypeSpec -> listOf(psiElement)
            is GoTypeDeclaration -> psiElement.typeSpecList
            else -> emptyList()
        }

        val methodPairs = typeSpecs.flatMap { type ->
            val methods = type.methods
            methods.map { method -> method to type.name }
        }

        val methods = methodPairs.map { it.first }
            .filterIsInstance<GoMethodDeclaration>()

        val name = GoPsiUtil.getDeclarationName(psiElement)

        return ClassContext(
            psiElement, psiElement.text, name, methods, emptyList(), emptyList(), emptyList()
        )
    }
}
