package com.github.zjh7890.gpttools.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.impl.compiled.ClsTypeParameterImpl
import com.intellij.psi.util.PsiUtil
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
                            val findSourceCode = findSourceCode(psiClass, project)
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

    fun findSourceCode(psiClass: PsiClass, project: Project): VirtualFile? {
        val classFile = psiClass.containingFile.virtualFile ?: return null

        // 假设 classFile 路径类似于 jar://path/to/your.jar!/com/example/MyClass.class
        val classFilePath = classFile.path
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
            println("Sources JAR not found at: $sourcesJarPath")
            return null
        }

        println("Found sources JAR: ${sourcesJarFile.path}")

        // 获取源代码对应的路径
        val sourceEntryPath = classEntryPath.replace(".class", ".java")
        println("Looking for source entry: $sourceEntryPath")

        return try {
            JarFile(sourcesJarFile.path).use { jarFile ->
                val sourceEntry = jarFile.getEntry(sourceEntryPath)
                if (sourceEntry != null) {
                    val sourceFileUrl = "jar://${sourcesJarFile.path}!/$sourceEntryPath"
                    println("Found source file: $sourceFileUrl")
                    VirtualFileManager.getInstance().findFileByUrl(sourceFileUrl)
                } else {
                    println("Source entry not found in JAR: $sourceEntryPath")
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

data class ClassSourceInfo(val className: String, val sourceCode: String) {
    override fun toString(): String {
        return "$className\n```\n$sourceCode```\n"
    }
}
