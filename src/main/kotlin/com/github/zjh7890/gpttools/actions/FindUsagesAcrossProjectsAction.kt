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
import com.github.zjh7890.gpttools.settings.other.OtherSettingsState


class FindUsagesAcrossProjectsAction : AnAction(), DumbAware {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val settings = OtherSettingsState.getInstance()
        e.presentation.isVisible = settings.showFindUsagesAcrossProjectsAction
    }
    private val logger = Logger.getInstance(FindUsagesAcrossProjectsAction::class.java)

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR) ?: return
        val psiFile = PsiUtilBase.getPsiFileInEditor(editor, project) ?: return
        // 获取光标所在的 PsiElement
        val elementAtCaret = psiFile.findElementAt(editor.caretModel.offset) ?: return

        // 获取 PsiMethod
        val psiMethod = elementAtCaret.let { PsiTreeUtil.getParentOfType(it, PsiMethod::class.java) } ?: return

        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                var usages: List<ReferenceUsage> = mutableListOf()
                ApplicationManager.getApplication().runReadAction {
                    usages = findUsagesAcrossProjects(project, psiMethod)
                }
                ApplicationManager.getApplication().invokeLater {
                    if (usages.isEmpty()) {
                        Messages.showInfoMessage(project, "No usages found in other projects.", "Search Complete")
                    } else {
                        displayUsagesPopup(project, usages, event)
                    }
                }
            },
            "Finding Usages Across Projects", true, project
        )
    }

    private fun findUsagesAcrossProjects(project: Project, method: PsiMethod): List<ReferenceUsage> {
        val qualifiedClassName = method.containingClass?.qualifiedName ?: return emptyList()
        val parentDir = project.baseDir?.parent?.path ?: return emptyList()

        val usages = ConcurrentLinkedQueue<ReferenceUsage>()
        val ignoredDirectories = setOf("/.git", "/target", "/test", "/.idea")
        val list = mutableListOf<File>()

        runBlocking {
            // 并行遍历顶层目录下的每一个子目录
            File(parentDir).listFiles()?.filter { it.isDirectory }?.map { dir ->
                val projectName = dir.name
                async(Dispatchers.IO) {
                    dir.walkTopDown().filter { file ->
                        file.isFile && file.extension == "java" &&
                                ignoredDirectories.none { ignoredDir -> file.absolutePath.contains(ignoredDir) }
                    }.forEach { file ->
                        val content = file.readText()
                        if (content.contains(qualifiedClassName)) {
                            ApplicationManager.getApplication().runReadAction {
                                list.add(file);
                                val psiFile = PsiFileFactory.getInstance(project).createFileFromText(file.name, JavaLanguage.INSTANCE, content)
                                processFileForUsages(psiFile, method, usages, projectName, file.absolutePath)
                            }
                        }
                    }
                }
            }?.awaitAll() // 等待所有异步任务完成
        }

        return usages.toList()
    }

    private fun processFileForUsages(
        psiFile: PsiFile,
        method: PsiMethod,
        usages: ConcurrentLinkedQueue<ReferenceUsage>,
        projectName: String,
        absolutePath: String
    ) {
        psiFile.accept(object : JavaRecursiveElementVisitor() {
            override fun visitReferenceExpression(reference: PsiReferenceExpression) {
                super.visitReferenceExpression(reference)
                if (reference.isReferenceTo(method)) {
                    usages.add(ReferenceUsage(reference, projectName, absolutePath))
                }
            }
        })
    }

    private fun displayUsagesPopup(project: Project, usages: List<ReferenceUsage>, event: AnActionEvent) {
        val projectBasePath = project.basePath ?: return // 获取当前项目的根目录
        val parentPath = File(projectBasePath).parent ?: return // 获取当前项目根目录的父路径

        val popupItems = usages.map { usage ->
            val projectName = usage.projectName
            val className = usage.reference.element?.containingFile?.name ?: "Unknown Class"
            val methodName = (usage.reference.element as? PsiElement)?.text ?: "Unknown Method"
            "$projectName - $className - $methodName"
        }

        logger.warn("popupItems: $popupItems")

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(popupItems)
            .setTitle("Usage Results")
            .setItemChosenCallback { item ->
                logger.warn("popupItems callback: $popupItems")
                val selectedUsage = usages[popupItems.indexOf(item)]
                navigateToUsage(project, selectedUsage)
            }
            .createPopup()

        ApplicationManager.getApplication().invokeLater {
            logger.warn("Attempting to show popup using showInBestPositionFor")
            popup.showInBestPositionFor(event.dataContext)
            logger.warn("Popup should now be visible")
        }
    }

    private fun navigateToUsage(project: Project, usage: ReferenceUsage) {
        val projectBasePath = project.basePath
        if (projectBasePath == null) {
            Messages.showErrorDialog(project, "projectBasePath 无法找到当前项目路径", "项目路径未找到")
            return
        }
        val parentPath = File(projectBasePath).parent
        if (parentPath == null) {
            Messages.showErrorDialog(project, "parentPath 无法找到当前项目路径的父路径", "项目路径未找到")
            return
        }

        // 生成目标项目路径
        val targetProjectPath = File(parentPath, usage.projectName).absolutePath

        // 判断目标项目是否已经打开
        val targetProject = ProjectManager.getInstance().openProjects.find { it.basePath == targetProjectPath }

        if (targetProject == null) {
            // 如果项目未打开，打开该项目
            ProjectManager.getInstance().loadAndOpenProject(targetProjectPath)
        }

        // 确保项目已打开后，进行跳转
//        val openedProject = ProjectManager.getInstance().openProjects.find { it.basePath == targetProjectPath }
        // 确保项目已打开后，进行跳转
        val openedProject = ProjectManager.getInstance().openProjects.find { it.basePath == targetProjectPath }
        if (openedProject != null) {
            val filePath = usage.absolutePath
            // 通过路径找到文件并在目标项目中打开
            val file = File(filePath)
            val newVirtualFile: VirtualFile? = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
            if (newVirtualFile != null) {
                logger.warn("popupItems navigate to: $filePath")
                // 直接通过路径和偏移量在目标项目中打开文件并跳转
                val descriptor = OpenFileDescriptor(openedProject, newVirtualFile, usage.reference.textOffset)
                descriptor.navigate(true) // 自动聚焦并跳转到指定位置
            } else {
                Messages.showErrorDialog(openedProject, "无法通过路径找到文件：$filePath", "文件未找到")
            }
        } else {
            Messages.showErrorDialog(project, "无法打开项目：$targetProjectPath", "项目未打开")
        }
    }
}

data class ReferenceUsage(
    val reference: PsiReferenceExpression, // PsiReferenceExpression 类型的引用
    val projectName: String, // 项目名称
    val absolutePath: String // 项目名称
)
