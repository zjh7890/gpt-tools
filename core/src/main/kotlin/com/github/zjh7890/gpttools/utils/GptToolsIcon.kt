package com.github.zjh7890.gpttools.utils

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object GptToolsIcon {
    @JvmField
    val PRIMARY: Icon = IconLoader.getIcon("/icons/icon_16px.svg", GptToolsIcon::class.java)

    @JvmField
    val Send: Icon = IconLoader.getIcon("/icons/send.svg", GptToolsIcon::class.java)

    @JvmField
    val Stop: Icon = IconLoader.getIcon("/icons/stop.svg", GptToolsIcon::class.java)
}
