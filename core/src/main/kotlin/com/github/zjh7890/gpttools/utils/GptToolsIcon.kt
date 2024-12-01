package com.github.zjh7890.gpttools.utils

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object GptToolsIcon {
    @JvmField
    val PRIMARY: Icon = IconLoader.getIcon("/icons/icon_16px.svg", GptToolsIcon::class.java)

    @JvmField
    val Send: Icon = IconLoader.getIcon("/icons/send.svg", GptToolsIcon::class.java)

    @JvmField
    val SendGray: Icon = IconLoader.getIcon("/icons/send_gray.svg", GptToolsIcon::class.java)

    @JvmField
    val Stop: Icon = IconLoader.getIcon("/icons/stop.svg", GptToolsIcon::class.java)

    @JvmField
    val ToPromptIcon: Icon = IconLoader.getIcon("/icons/contexts.svg", GptToolsIcon::class.java)

    @JvmField
    val ApplyCopyIcon: Icon = IconLoader.getIcon("/icons/download.svg", GptToolsIcon::class.java)

    @JvmField
    val SendThenDiff: Icon = IconLoader.getIcon("/icons/send_then_diff.svg", GptToolsIcon::class.java)
}
