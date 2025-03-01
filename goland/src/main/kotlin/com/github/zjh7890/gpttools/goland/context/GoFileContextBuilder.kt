package com.github.zjh7890.gpttools.goland.context

import com.github.zjh7890.gpttools.context.FileContext
import com.github.zjh7890.gpttools.context.builder.FileContextBuilder
import com.goide.psi.GoFile
import com.goide.psi.GoFunctionOrMethodDeclaration
import com.goide.psi.GoTypeDeclaration
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

class GoFileContextBuilder : FileContextBuilder {
    override fun getFileContext(psiFile: PsiFile): FileContext? {
        if (psiFile !is GoFile) return null

        val packageString = psiFile.packageName
        val path = psiFile.virtualFile.path
        val imports = psiFile.imports
        val classes = PsiTreeUtil.getChildrenOfTypeAsList(psiFile, GoTypeDeclaration::class.java)
        val methods = PsiTreeUtil.getChildrenOfTypeAsList(psiFile, GoFunctionOrMethodDeclaration::class.java)

        return FileContext(psiFile, psiFile.name, path, packageString, imports, classes, methods)
    }
}
