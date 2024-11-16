package com.github.zjh7890.gpttools.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object DirectoryUtil {

    fun findProjectByName(projectName: String): Project? {
        val projects = ProjectManager.getInstance().openProjects
        return projects.find { it.name == projectName }
    }

    fun getDirectoryContents(project: Project, maxFilesToShow: Int = 0): String {
        val basePath = project.basePath ?: return ""
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return ""
        val srcDir = baseDir.findChild("src")
        val srcParentPath = srcDir?.parent?.path

        return processDirectory(baseDir, "", srcParentPath, maxFilesToShow)
    }

    fun processDirectory(dir: VirtualFile, parentPath: String, srcParentPath: String?, maxFilesToShow: Int = 0): String {
        val children = dir.children
        val files = mutableListOf<VirtualFile>()
        val subdirs = mutableListOf<VirtualFile>()

        val dirname = dir.name
        val fullDirname = if (parentPath.isEmpty()) "." else "$parentPath/$dirname"

        // 遍历子文件和子目录
        for (child in children) {
            val name = child.name
            if (name.startsWith(".")) {
                continue  // 跳过隐藏文件和目录
            }
            // 检查当前目录是否与 src 同级，并且目录名为 "target" / "build"
            if (child.isDirectory && srcParentPath != null && dir.path == srcParentPath
                && (name == "target" || name == "build")) {
                continue  // 跳过与 src 同级的 target / build 目录
            }
            if (child.isDirectory) {
                subdirs.add(child)
            } else {
                files.add(child)
            }
        }

        val output = StringBuilder()

        when {
            files.isEmpty() && subdirs.isEmpty() -> {
                // 情况 1：空的叶子目录
                output.append("$fullDirname/\n[Empty]\n")
            }
            files.isNotEmpty() -> {
                // 目录包含文件（可能还有子目录）
                output.append("$fullDirname/\n")
                // 根据 maxFilesToShow 显示文件
                if (maxFilesToShow > 0) {
                    val filesToShow = files.take(maxFilesToShow)
                    filesToShow.forEach { file -> output.append("- ${file.name}\n") }
                    if (files.size > maxFilesToShow) {
                        output.append("- ...\n")  // 标记还有更多文件
                    }
                }
                // 递归处理子目录
                subdirs.forEach { subdir ->
                    output.append(processDirectory(subdir, fullDirname, srcParentPath, maxFilesToShow))
                }
            }
            subdirs.isNotEmpty() -> {
                // 情况 3：只有子目录的目录
                subdirs.forEach { subdir ->
                    if (!subdir.name.startsWith(".")) {
                        output.append(processDirectory(subdir, fullDirname, srcParentPath, maxFilesToShow))
                    }
                }
            }
        }

        return output.toString()
    }

fun getProjectStructure(project: Project, maxFilesToShow: Int = 0): String {
    val baseDir = project.basePath?.let { File(it) } ?: return "Project base path not found"
    val sb = StringBuilder()

    // 添加根目录
    sb.append("* ${baseDir.name}/\n")
    visitDirectory(baseDir, "  ", maxFilesToShow, sb)

    return sb.toString()
}

private fun visitDirectory(
    dir: File,
    prefix: String,
    maxFilesToShow: Int,
    sb: StringBuilder
) {
    val allFiles = getSortedFiles(dir) ?: return

    // 过滤掉以 . 开头的文件和目录，以及其他需要忽略的目录
    val filteredFiles = allFiles.filterNot {
        it.name.startsWith(".") || shouldSkipDirectory(it.name)
    }

    // 分离目录和文件
    val (directories, files) = filteredFiles.partition { it.isDirectory }

    // 处理目录
    for (directory in directories) {
        appendFileEntry(directory, prefix, sb)
        visitDirectory(directory, "$prefix  ", maxFilesToShow, sb)
    }

    // 处理文件（如果 maxFilesToShow > 0）
    if (maxFilesToShow > 0) {
        files.take(maxFilesToShow).forEach { file ->
            appendFileEntry(file, prefix, sb)
        }
    }
}

private fun getSortedFiles(dir: File): List<File>? {
    return dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
}

private fun shouldSkipDirectory(name: String): Boolean {
    return name in setOf(".git", ".idea", "build", "target", "node_modules", "test")
}

private fun appendFileEntry(file: File, prefix: String, sb: StringBuilder) {
    sb.append(prefix)
    sb.append("* ${file.name}${if (file.isDirectory) "/" else ""}\n")
}
}