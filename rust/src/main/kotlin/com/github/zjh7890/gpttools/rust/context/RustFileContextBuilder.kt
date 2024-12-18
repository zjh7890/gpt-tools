package com.github.zjh7890.gpttools.rust.context

import com.github.zjh7890.gpttools.context.FileContext
import com.github.zjh7890.gpttools.context.builder.FileContextBuilder
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RsUseItem

class RustFileContextBuilder : FileContextBuilder {
    override fun getFileContext(psiFile: PsiFile): FileContext? {
        if (psiFile !is RsFile) return null

        val path = psiFile.virtualFile.path
        val imports = PsiTreeUtil.getChildrenOfTypeAsList(psiFile, RsUseItem::class.java)
        val structures = PsiTreeUtil.getChildrenOfTypeAsList(psiFile, RsStructItem::class.java)
        val functions = emptyList<PsiFile>()

        return FileContext(psiFile, psiFile.name, path, null, imports, structures, functions)
    }
}
