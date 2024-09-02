package com.github.zjh7890.gpttools.actions

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class FindImplAcrossProjectsAction : AnAction(), DumbAware {
    private val logger = Logger.getInstance(FindImplAcrossProjectsAction::class.java)

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR) ?: return
        val psiFile = PsiUtilBase.getPsiFileInEditor(editor, project) ?: return
        val elementAtCaret = psiFile.findElementAt(editor.caretModel.offset) ?: return

        // 获取 PsiMethodCallExpression
        val methodCall = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethodCallExpression::class.java) ?: return

        // 获取解析后的 PsiMethod
        val resolvedMethod = methodCall.resolveMethod() ?: return

        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                var implementations: List<MethodImplUsage> = mutableListOf()
                ApplicationManager.getApplication().runReadAction {
                    implementations = findImplementationsAcrossProjects(project, resolvedMethod)
                }
                ApplicationManager.getApplication().invokeLater {
                    if (implementations.isEmpty()) {
                        Messages.showInfoMessage(project, "No implementations found in other projects.", "Search Complete")
                    } else {
                        displayImplementationsPopup(project, implementations)
                    }
                }
            },
            "Finding Implementations Across Projects", true, project
        )
    }

    private fun findImplementationsAcrossProjects(project: Project, method: PsiMethod): List<MethodImplUsage> {
        val qualifiedClassName = method.containingClass?.qualifiedName ?: return emptyList()
        val className = method.containingClass?.name ?: return emptyList()
        val parentDir = project.baseDir?.parent?.path ?: return emptyList()

        val implementations = ConcurrentLinkedQueue<MethodImplUsage>()
        val ignoredDirectories = setOf("/.git", "/target", "/test", "/.idea")
        val list = mutableListOf<File>()

        runBlocking {
            File(parentDir).listFiles()?.filter { it.isDirectory }?.map { dir ->
                val projectName = dir.name
                async(Dispatchers.IO) {
                    dir.walkTopDown().filter { file ->
                        file.isFile && file.extension == "java" &&
                                file.name == "$className.java" &&
                                ignoredDirectories.none { ignoredDir -> file.absolutePath.contains(ignoredDir) }
                    }.forEach { file ->
                        val content = file.readText()
                        if (content.contains("package ${qualifiedClassName.substringBeforeLast('.')}")) {
                            ApplicationManager.getApplication().runReadAction {
                                list.add(file)
                                val psiFile = PsiFileFactory.getInstance(project).createFileFromText(file.name, JavaLanguage.INSTANCE, content)
                                processFileForImplementations(psiFile, method, implementations, projectName, file.absolutePath)
                            }
                        }
                    }
                }
            }?.awaitAll()
        }

        return implementations.toList()
    }

    private fun processFileForImplementations(
        psiFile: PsiFile,
        method: PsiMethod,
        implementations: ConcurrentLinkedQueue<MethodImplUsage>,
        projectName: String,
        absolutePath: String
    ) {
        psiFile.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethod(methodImpl: PsiMethod) {
                super.visitMethod(methodImpl)
                if (methodImpl.name == method.name) {
                    implementations.add(MethodImplUsage(methodImpl, projectName, absolutePath))
                }
            }
        })
    }

    private fun displayImplementationsPopup(project: Project, implementations: List<MethodImplUsage>) {
        val projectBasePath = project.basePath ?: return
        val parentPath = File(projectBasePath).parent ?: return

        val popupItems = implementations.map { impl ->
            val projectName = impl.projectName
            val className = impl.reference.containingFile?.name ?: "Unknown Class"
            val methodName = (impl.reference as? PsiMethod)?.name ?: "Unknown Method"
            "$projectName - $className - $methodName"
        }

        logger.info("popupItems: $popupItems")

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(popupItems)
            .setTitle("Implementation Results")
            .setItemChosenCallback { item ->
                logger.info("popupItems callback: $popupItems")
                Messages.showErrorDialog(project, "callback 无法找到", "项目路径未找到")
                val selectedImpl = implementations[popupItems.indexOf(item)]
                navigateToImplementation(project, selectedImpl)
            }
            .createPopup()

        logger.info("popupItems showInFocusCenter: $popupItems")

        popup.showInFocusCenter()
    }

    private fun navigateToImplementation(project: Project, implementation: MethodImplUsage) {
        val projectBasePath = project.basePath
        if (projectBasePath == null) {
            Messages.showErrorDialog(project, "projectBasePath 无法找到", "项目路径未找到")
            return
        }
        val parentPath = File(projectBasePath).parent
        if (parentPath == null) {
            Messages.showErrorDialog(project, "parentPath 无法找到", "父路径未找到")
            return
        }

        val targetProjectPath = File(parentPath, implementation.projectName).absolutePath

        val targetProject = ProjectManager.getInstance().openProjects.find { it.basePath == targetProjectPath }

        if (targetProject == null) {
            ProjectManager.getInstance().loadAndOpenProject(targetProjectPath)
        }

        val openedProject = ProjectManager.getInstance().openProjects.find { it.basePath == targetProjectPath }
        if (openedProject != null) {
            val filePath = implementation.absolutePath
            val file = File(filePath)
            val newVirtualFile: VirtualFile? = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
            if (newVirtualFile != null) {
                logger.info("popupItems navigate to: $filePath")
                val descriptor = OpenFileDescriptor(openedProject, newVirtualFile, implementation.reference.textOffset)
                descriptor.navigate(true)
            } else {
                Messages.showErrorDialog(openedProject, "无法通过路径找到文件：$filePath", "文件未找到")
            }
        } else {
            Messages.showErrorDialog(project, "无法打开项目：$targetProjectPath", "项目未打开")
        }
    }
}

data class MethodImplUsage(
    val reference: PsiElement,  // 可以是 PsiMethod 或者更通用的 PsiElement
    val projectName: String,
    val absolutePath: String
)
