package com.github.zjh7890.gpttools.services

import com.github.zjh7890.gpttools.utils.Desc
import com.github.zjh7890.gpttools.utils.DirectoryUtil
import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import java.util.stream.Collectors

/**
 * @Date: 2024/9/28 08:28
 */
@Service(Service.Level.PROJECT)
class ToolsService(val project: Project) {

    @Desc("获取项目目录结构，入参是项目名称")
    fun getProjectStructure(projectName: String): String {
        val project = DirectoryUtil.findProjectByName(projectName)
        if (project == null) {
            return "未找到该项目"
        }

        val basePath = project.basePath ?: return "未找到该项目路径"
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return "未找到该项目的根目录"
        return DirectoryUtil.processDirectory(baseDir, "", srcParentPath = null)
    }

    @Desc("根据简单类名获取文件内容，如 HelloService")
    fun getFileContentBySimpleClassName(className: String): String {
        val projectScope = GlobalSearchScope.projectScope(project)

        // 使用 JavaPsiFacade 搜索类
        val psiClass = JavaPsiFacade.getInstance(project).findClass(className, projectScope)

        return if (psiClass != null) {
            // 获取类对应的文件并返回其内容
            psiClass.containingFile?.virtualFile?.contentsToByteArray()?.toString(Charsets.UTF_8)
                ?: "未能获取文件内容"
        } else {
            "未找到类名为 $className 的文件"
        }
    }

    @Desc("根据全限定类名获取文件内容，如 com.nice.HelloService")
    fun getFileContentByQualifiedClassName(qualifiedClassName: String): String {
        val projectScope = GlobalSearchScope.projectScope(project)

        // 根据全限定类名查找
        val psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedClassName, projectScope)

        return if (psiClass != null) {
            // 获取类对应的文件并返回其内容
            val content = psiClass.containingFile?.virtualFile?.contentsToByteArray()?.toString(Charsets.UTF_8)
            val res = (content
                ?: "未能获取文件内容")
            val border = FileUtil.determineBorder(res)
            return "${border}\n${res}\n${border}"

        } else {
            "未找到类名为 $qualifiedClassName 的文件"
        }
    }

    @Desc("根据函数签名获取参数返回值信息，签名形如 com.user.UserService#getUserByUid(java.lang.Long, java.lang.Integer)，方法名 getUserByUid 后可能有参数，可能没有参数，取决于该方法是否重载")
    fun getMethodDetailsBySignature(signature: String): String {
        // 签名格式：com.yupaopao.xxq.user.UserService#getUserByUid(java.lang.Long, java.lang.Integer)
        val parts = signature.split("#")
        if (parts.size != 2) return "无效的签名格式"

        val className = parts[0]
        val methodSignature = parts[1]

        val methodName = methodSignature.substringBefore('(')  // 方法名，如 getUserByUid
        val parametersSignature = methodSignature.substringAfter('(', "").substringBefore(')', "")  // 参数签名，如 java.lang.Long, java.lang.Integer

        // 获取项目范围
        val projectScope = GlobalSearchScope.projectScope(project)

        // 查找类
        val psiClass = JavaPsiFacade.getInstance(project).findClass(className, projectScope)
            ?: return "未找到类: $className"

        // 获取类中的所有方法
        val methods = psiClass.findMethodsByName(methodName, false)

        var matchingMethods = mutableListOf<PsiMethod>()
        if (methods.size <= 0) {
            matchingMethods = methods.toMutableList()
        } else {
            // 查找匹配的方法
            matchingMethods = methods.filter { method ->
                val parameterTypes = method.parameterList.parameters.map { param -> param.type.canonicalText }

                // 比较参数签名
                val expectedParameterTypes = parametersSignature.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (expectedParameterTypes.isEmpty()) {
                    return@filter true
                }
                expectedParameterTypes.size == parameterTypes.size &&
                        expectedParameterTypes.zip(parameterTypes).all { (expected, actual) -> expected == actual }
            }.toMutableList()
        }

        if (matchingMethods.isEmpty()) {
            return "未找到匹配的方法: $methodName($parametersSignature)"
        }

//        val method = matchingMethods.get(0)
//        val classes = findClassesFromMethod(method, project)
//        val classInfos =
//            classes.stream().map { x -> x.className }.collect(Collectors.toList()).joinToString("\n")
//        val GPT_methodInfo = classes.joinToString("\n")
//        val GPT_methodName = method.name
//        return """
//${GPT_methodName}
//参数及返回值信息:
//${GPT_methodInfo}
//        """.trimIndent()
        return ""
    }

    @Desc("读取文件列表内容")
    fun readFileList(files: List<String>): String {
        return FileUtil.readFileInfoForLLM(project, files)
    }
}