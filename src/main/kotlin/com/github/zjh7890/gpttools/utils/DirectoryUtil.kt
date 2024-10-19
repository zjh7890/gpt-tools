package com.github.zjh7890.gpttools.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.LocalFileSystem

object DirectoryUtil {

    fun findProjectByName(projectName: String): Project? {
        val projects = ProjectManager.getInstance().openProjects
        return projects.find { it.name == projectName }
    }

    fun getDirectoryContents(project: Project): String {
        val basePath = project.basePath ?: return ""
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return ""
        val srcDir = baseDir.findChild("src")
        val srcParentPath = srcDir?.parent?.path

        return processDirectory(baseDir, "", srcParentPath)
    }

    fun processDirectory(dir: VirtualFile, parentPath: String, srcParentPath: String?): String {
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
                // 只显示前 5 个文件
                val filesToShow = files.take(5)
                filesToShow.forEach { file -> output.append("- ${file.name}\n") }
                if (files.size > 5) {
                    output.append("- ...\n")  // 标记还有更多文件
                }
                // 递归处理子目录
                subdirs.forEach { subdir ->
                    output.append(processDirectory(subdir, fullDirname, srcParentPath))
                }
            }
            subdirs.isNotEmpty() -> {
                // 情况 3：只有子目录的目录
                subdirs.forEach { subdir ->
                    if (!subdir.name.startsWith(".")) {
                        output.append(processDirectory(subdir, fullDirname, srcParentPath))
                    }
                }
            }
        }

        return output.toString()
    }
}