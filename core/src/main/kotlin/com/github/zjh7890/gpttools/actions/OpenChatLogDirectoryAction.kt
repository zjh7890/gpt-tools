package com.github.zjh7890.gpttools.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.Desktop
import java.io.File
import java.io.IOException

class OpenChatLogDirectoryAction : AnAction() {

    // 设置当菜单被点击时执行的操作
    override fun actionPerformed(event: AnActionEvent) {
        val logDirectory = getLogDirectory()

        try {
            // 检查是否支持 Desktop 类
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                val logDirFile = File(logDirectory)

                // 确保目录存在
                if (logDirFile.exists()) {
                    desktop.open(logDirFile) // 在系统文件管理器中打开目录
                } else {
                    // 如果目录不存在，创建目录并打开
                    logDirFile.mkdirs()
                    desktop.open(logDirFile)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace() // 处理异常
        }
    }

    // 获取日志目录
    private fun getLogDirectory(): String {
        val userHome = System.getProperty("user.home")
        return "$userHome/.gpttools/chat_history"
    }
}