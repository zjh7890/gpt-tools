package com.github.zjh7890.gpttools.java.util

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.impl.compiled.ClsTypeParameterImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.github.zjh7890.gpttools.settings.other.OtherSettingsState
import java.util.jar.JarFile


object PsiUtils {
    fun generateSignature(method: PsiMethod, appendClassName: Boolean): String {
        val sb = StringBuilder()

        // 处理返回值类型，包括泛型信息
        val returnType = method.returnTypeElement?.type
        if (returnType != null) {
            sb.append(returnType.canonicalText).append(" ")
        }

        // 方法名

        if (appendClassName) {
            sb.append(method.containingClass?.qualifiedName)
        }
        sb.append(".").append(method.name).append("(")

        // 参数列表
        method.parameterList.parameters.forEachIndexed { index, psiParameter ->
            sb.append(psiParameter.type.canonicalText)
            if (index < method.parameterList.parameters.size - 1) {
                sb.append(", ")
            }
        }
        sb.append(")")

        // 异常签名
        val exceptions = method.throwsList.referenceElements
        if (exceptions.isNotEmpty()) {
            sb.append(" throws ")
            exceptions.forEachIndexed { index, psiJavaCodeReferenceElement ->
                sb.append(psiJavaCodeReferenceElement.qualifiedName)
                if (index < exceptions.size - 1) {
                    sb.append(", ")
                }
            }
        }

        return sb.toString()
    }

    fun findClassesFromMethod(method: PsiMethod, project: Project): Set<ClassSourceInfo> {
        val result = HashSet<ClassSourceInfo>()
        val visited = HashSet<PsiType>()
        collectClasses(method.returnType, result, visited, project)
        method.parameterList.parameters.forEach { param ->
            collectClasses(param.type, result, visited, project)
        }
        return result
    }

    fun collectClasses(
        psiType: PsiType?,
        result: HashSet<ClassSourceInfo>,
        visited: HashSet<PsiType> = HashSet(),
        project: Project
    ) {
        if (psiType == null || visited.contains(psiType)) return

        psiType.getDeepComponentType().let { deepType ->
            PsiUtil.resolveClassInType(deepType)?.let { psiClass ->
                try {
                    if (psiClass is ClsTypeParameterImpl) {
                        return
                    }
                    if (!psiClass.qualifiedName?.startsWith("java.")!!) {
                        visited.add(psiType)
                        val psiFile = psiClass.containingFile
                        val classSourceCode: String

                        if (psiFile.name.endsWith(".class")) {
                            val findSourceCode = findSourceCode(psiClass)
                            if (findSourceCode != null) {
                                classSourceCode = PsiManager.getInstance(project).findFile(findSourceCode)!!.text  // 获取类的源代码
                            } else {
                                classSourceCode = psiFile.text  // 获取类的源代码
                            }
                            // 处理编译后的字节码文件
                            // 可能需要使用反编译工具来获取源代码，或者寻找其他方式处理
                        } else {
                            classSourceCode = psiFile.text  // 获取类的源代码
                        }

                        result.add(ClassSourceInfo(psiClass.qualifiedName ?: "Anonymous", classSourceCode))
                        // 递归处理成员变量
                        psiClass.fields.forEach { field ->
                            collectClasses(field.type, result, visited, project)
                        }
                        // 递归处理继承的类
                        psiClass.superTypes.forEach { superClassType ->
                            collectClasses(superClassType, result, visited, project)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }
        }

        // 处理泛型参数
        if (psiType is PsiClassType) {
            psiType.parameters.forEach { typeParameter ->
                collectClasses(typeParameter, result, visited, project)
            }
        }

        if (psiType is PsiArrayType) {
            collectClasses(psiType.componentType, result, visited, project)
        }
    }

    fun findSourceCode(psiElement: PsiElement): VirtualFile? {
        val classFile = psiElement.containingFile.virtualFile ?: return null

        // 假设 classFile 路径类似于 jar://path/to/your.jar!/com/example/MyClass.class
        val classFilePath = classFile.path
        if (!classFilePath.contains(".jar!")) {
            return classFile
        }

        val jarSeparatorIndex = classFilePath.indexOf("!/")
        if (jarSeparatorIndex == -1) {
            return null
        }

        val jarPath = classFilePath.substring(0, jarSeparatorIndex)
        val classEntryPath = classFilePath.substring(jarSeparatorIndex + 2)

        // 替换 jar 文件路径为 sources jar 文件路径
        val sourcesJarPath = jarPath.replace(".jar", "-sources.jar")
        val sourcesJarFile = LocalFileSystem.getInstance().findFileByPath(sourcesJarPath) ?: return null

        if (sourcesJarFile == null) {
            return null
        }

        // 获取源代码对应的路径
        val sourceEntryPath = classEntryPath.replace(".class", ".java")

        return try {
            JarFile(sourcesJarFile.path).use { jarFile ->
                val sourceEntry = jarFile.getEntry(sourceEntryPath)
                if (sourceEntry != null) {
                    val sourceFileUrl = "jar://${sourcesJarFile.path}!/$sourceEntryPath"
                    VirtualFileManager.getInstance().findFileByUrl(sourceFileUrl)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getDependencies(file: VirtualFile, project: Project): List<VirtualFile> {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return emptyList()
        val referencedFiles = mutableListOf<VirtualFile>()

        PsiTreeUtil.findChildrenOfType(psiFile, PsiElement::class.java).forEach { element ->
            element.references.forEach { reference ->
                val resolvedFile = reference.resolve()?.containingFile
                // 假设 resolvedFile 是 PsiFile 类型
                if (resolvedFile != null && resolvedFile !== psiFile && resolvedFile.virtualFile != null) {
                    val virtualFile = resolvedFile.virtualFile
                    // virtualFile.path
                    // example /Users/zjh/.m2/repository/com/platform/config-client/0.10.21/config-client-0.10.21.jar!/com/ctrip/framework/apollo/Config.class
                    val patterns = OtherSettingsState.getInstance().dependencyPatterns
                        .split("\n")
                        .filter { it.isNotEmpty() }
                        .map { it.toRegex() }
                    if (isFileInProject(virtualFile, project) || 
                        patterns.any { pattern -> virtualFile.path.matches(pattern) }) {
                        // 在这里继续处理 virtualFile
                        findSourceCode(resolvedFile)?.let { referencedFiles.add(it) }
                    }
                }
            }
        }

        return referencedFiles.distinct()
    }

    fun isFileInProject(file: VirtualFile, project: Project): Boolean {
        return ProjectRootManager.getInstance(project).fileIndex.isInContent(file)
    }

    fun getMethodStartAndEndLines(method: PsiMethod): Pair<Int, Int> {
        val document: Document? = PsiDocumentManager.getInstance(method.project).getDocument(method.containingFile)
        if (document == null) return Pair(-1, -1)

        val startLine = document.getLineNumber(method.textRange.startOffset)
        val endLine = document.getLineNumber(method.textRange.endOffset)

        return Pair(startLine, endLine)
    }

    fun getLineContent(virtualFile: VirtualFile, lineNumber: Int, project: Project): String {
        // Get the document associated with the VirtualFile
        val document: Document? = FileDocumentManager.getInstance().getDocument(virtualFile)

        // If the document is null or the line number is out of bounds, return an empty string
        if (document == null || lineNumber < 0 || lineNumber >= document.lineCount) {
            return ""
        }

        // Get the start and end offsets of the line
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineEndOffset = document.getLineEndOffset(lineNumber)

        // Return the text of the line
        return document.getText().substring(lineStartOffset, lineEndOffset)
    }

    fun getRelevantImportsForElement(element: PsiElement): List<PsiImportStatement> {
        val psiFile = element.containingFile as? PsiJavaFile ?: return emptyList()
        val importList = psiFile.importList ?: return emptyList()
        val relevantImports = mutableListOf<PsiImportStatement>()

        val methodReferences = PsiTreeUtil.findChildrenOfType(element, PsiJavaCodeReferenceElement::class.java)
        for (importStatement in importList.allImportStatements) {
            if (importStatement is PsiImportStatement) {
                val importReference = importStatement.importReference ?: continue
                val resolved = importReference.resolve()
                val importQualifiedName = resolved?.let {
                    when (it) {
                        is PsiClass -> it.qualifiedName
                        is PsiPackage -> it.qualifiedName
                        else -> null
                    }
                } ?: importReference.qualifiedName

                if (importQualifiedName != null) {
                    if (importQualifiedName.endsWith(".*")) {
                        // Handle wildcard imports, check if any method references match the package
                        val packageName = importQualifiedName.removeSuffix(".*")
                        if (methodReferences.any { ref ->
                                val refText = ref.qualifiedName ?: ref.text
                                refText.startsWith(packageName)
                            }) {
                            relevantImports.add(importStatement)
                        }
                    } else {
                        // Handle specific imports
                        if (methodReferences.any { ref ->
                                val refText = ref.qualifiedName ?: ref.text
                                refText == importQualifiedName || refText.startsWith("${importQualifiedName}.")
                            }) {
                            relevantImports.add(importStatement)
                        }
                    }
                }
            }
        }
        return relevantImports
    }
}

data class ClassSourceInfo(val className: String, val sourceCode: String) {
    override fun toString(): String {
        return "$className\n```\n$sourceCode```\n"
    }
}
