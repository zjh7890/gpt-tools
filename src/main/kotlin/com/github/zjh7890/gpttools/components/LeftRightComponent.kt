package com.github.zjh7890.gpttools.components

import com.intellij.openapi.ui.Splitter
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel

/**
 * 左右组件
 *
 * @author makejava
 * @version 1.0.0
 * @date 2021/08/10 16:49
 */
class LeftRightComponent(
    private val leftPanel: JPanel,
    private val rightPanel: JPanel,
    private val proportion: Float = 0.3F,
    private val preferredSize: Dimension = JBUI.size(400, 300)
) {
    /**
     * 主面板
     */
    val mainPanel: JPanel by lazy {
        JPanel(BorderLayout()).also { panel ->
            val splitter = Splitter(false, proportion).apply {
                firstComponent = leftPanel
                secondComponent = rightPanel
            }
            panel.add(splitter, BorderLayout.CENTER)
            panel.preferredSize = preferredSize
        }
    }
}