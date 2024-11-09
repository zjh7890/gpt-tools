package com.github.zjh7890.gpttools.components.welcome

import com.github.zjh7890.gpttools.settings.GptToolsConfigurable
import com.github.zjh7890.gpttools.utils.GptToolsIcon
import com.intellij.icons.AllIcons
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel

class WelcomePanel: JPanel(BorderLayout()) {
    private val welcomeItems: List<WelcomeItem> = listOf(
        WelcomeItem("Chat to start..."),
        WelcomeItem("⌥ + ↩ 添加文件"),
        WelcomeItem("右键菜单触发 Template Prompt"),
    )

    init {
        val panel = panel {
            row {
                text("Welcome to use gpt-tools")
            }
            welcomeItems.forEach {
                row {
                    // icon
                    icon(GptToolsIcon.PRIMARY).gap(RightGap.SMALL)
                    text(it.text)
                }
            }
            row {
                icon(GptToolsIcon.PRIMARY).gap(RightGap.SMALL)
                val linkPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    add(JLabel("设置 LLM, 推荐用 claude sonnet 3.5").apply {
                        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        foreground = java.awt.Color(0x31, 0x5F, 0xBD) // 设置颜色为 #315fbd
                        addMouseListener(object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent) {
                                ShowSettingsUtil.getInstance().showSettingsDialog(
                                    null,
                                    GptToolsConfigurable::class.java
                                )
                            }
                        })
                    })
                    add(JLabel(AllIcons.Ide.External_link_arrow))
                }
                cell(linkPanel)
            }
            row {
                text("<a href=\"https://gpt-tools.yuque.com/pfm3um/doc\">Learn more</a>")
            }
            row {
                text("<a href=\"https://github.com/zjh7890/gpt-tools/issues/\">Want new feature / Report bug</a>")
            }
        }.apply {
            border = javax.swing.BorderFactory.createEmptyBorder(20, 20, 20, 20)
        }

        add(panel, BorderLayout.CENTER)
    }
}