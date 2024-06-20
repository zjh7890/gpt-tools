package com.github.zjh7890.gpttools.utils

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * @Author: zhujunhua
 * @Date: 2024/6/10 16:10
 */
object ClipboardUtils {
    fun copyToClipboard(text: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val selection = StringSelection(text)
        clipboard.setContents(selection, selection)
    }
}