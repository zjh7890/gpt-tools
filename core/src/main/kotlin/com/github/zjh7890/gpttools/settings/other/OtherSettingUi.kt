package com.github.zjh7890.gpttools.settings.other

import com.intellij.ui.components.JBCheckBox
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

class OtherSettingUi {
    private val showAddFileActionCheckBox = JBCheckBox("Show Add File Action")
    private val showAddRecursiveFileActionCheckBox = JBCheckBox("Show Add Recursive File Action")
    private val showFindUsagesAcrossProjectsActionCheckBox = JBCheckBox("Show Find Usages Across Projects Action")
    private val showFindImplAcrossProjectsActionCheckBox = JBCheckBox("Show Find Impl Across Projects Action")
    private val showConvertToMermaidActionCheckBox = JBCheckBox("Show Convert To Mermaid Action")
    private val showPsiDependencyByMethodActionCheckBox = JBCheckBox("Show Psi Dependency By Method Action")
    private val showCopyMethodSingleFileCheckBox = JBCheckBox("Show Copy Method Single File")
    private val showAllMethodFileCheckBox = JBCheckBox("Show All Method File")
    private val showOpenChatLogDirectoryActionCheckBox = JBCheckBox("Show Open Chat Log Directory Action")
    private val showGptToolsContextWindowCheckBox = JBCheckBox("Show GPT File Tree Window (Need Restart)")
    private val dependencyPatternsArea = JTextArea().apply {
        rows = 5
        toolTipText = "每行一个正则表达式，匹配依赖 jar 包类路径，路径形如 /Users/zjh/.m2/repository/com/platform/config-client/0.10.21/config-client-0.10.21.jar!/com/ctrip/framework/apollo/Config.class"
        text = ".*com\\/platform*"  // 设置 placeHolder
    }

    val component: JPanel = JPanel().apply {
        layout = BorderLayout()
        
        // 创建一个面板来包含所有复选框
        val checkBoxesPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            
            // 为每个复选框创建一个包装面板
            fun createCheckBoxPanel(checkBox: JBCheckBox): JPanel {
                return JPanel().apply {
                    layout = FlowLayout(FlowLayout.LEFT, 0, 0)  // 左对齐，无边距
                    add(checkBox)
                }
            }

            // 添加包装后的复选框
            add(createCheckBoxPanel(showAddFileActionCheckBox))
            add(createCheckBoxPanel(showAddRecursiveFileActionCheckBox))
            add(createCheckBoxPanel(showFindUsagesAcrossProjectsActionCheckBox))
            add(createCheckBoxPanel(showFindImplAcrossProjectsActionCheckBox))
            add(createCheckBoxPanel(showConvertToMermaidActionCheckBox))
            add(createCheckBoxPanel(showPsiDependencyByMethodActionCheckBox))
            add(createCheckBoxPanel(showCopyMethodSingleFileCheckBox))
            add(createCheckBoxPanel(showAllMethodFileCheckBox))
            add(createCheckBoxPanel(showGptToolsContextWindowCheckBox))
        }
        
        // 创建一个主面板来包含复选框面板和依赖模式面板
        val mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(checkBoxesPanel)
            
            // 依赖模式面板
            add(JPanel().apply {
                layout = BorderLayout(5, 0)
                add(JLabel("Dependency Patterns:"), BorderLayout.WEST)
                add(JScrollPane(dependencyPatternsArea), BorderLayout.CENTER)
            })
        }
        
        add(mainPanel, BorderLayout.NORTH)  // 将主面板添加到顶部
    }

    fun isModified(settings: OtherSettingsState): Boolean {
        return showAddFileActionCheckBox.isSelected != settings.showAddClassAction ||
                showAddRecursiveFileActionCheckBox.isSelected != settings.showAddRecursiveFileAction ||
                showFindUsagesAcrossProjectsActionCheckBox.isSelected != settings.showFindUsagesAcrossProjectsAction ||
                showFindImplAcrossProjectsActionCheckBox.isSelected != settings.showFindImplAcrossProjectsAction ||
                showConvertToMermaidActionCheckBox.isSelected != settings.showConvertToMermaidAction ||
                showPsiDependencyByMethodActionCheckBox.isSelected != settings.showPsiDependencyByMethodAction ||
                showCopyMethodSingleFileCheckBox.isSelected != settings.showCopyMethodSingleFile ||
                showAllMethodFileCheckBox.isSelected != settings.showAllMethodFile ||
                showOpenChatLogDirectoryActionCheckBox.isSelected != settings.showOpenChatLogDirectoryAction ||
                showGptToolsContextWindowCheckBox.isSelected != settings.showGptToolsContextWindow ||
                dependencyPatternsArea.text != settings.dependencyPatterns
    }

    fun apply(settings: OtherSettingsState) {
        settings.showAddClassAction = showAddFileActionCheckBox.isSelected
        settings.showAddRecursiveFileAction = showAddRecursiveFileActionCheckBox.isSelected
        settings.showFindUsagesAcrossProjectsAction = showFindUsagesAcrossProjectsActionCheckBox.isSelected
        settings.showFindImplAcrossProjectsAction = showFindImplAcrossProjectsActionCheckBox.isSelected
        settings.showConvertToMermaidAction = showConvertToMermaidActionCheckBox.isSelected
        settings.showPsiDependencyByMethodAction = showPsiDependencyByMethodActionCheckBox.isSelected
        settings.showCopyMethodSingleFile = showCopyMethodSingleFileCheckBox.isSelected
        settings.showAllMethodFile = showAllMethodFileCheckBox.isSelected
        settings.showOpenChatLogDirectoryAction = showOpenChatLogDirectoryActionCheckBox.isSelected
        settings.showGptToolsContextWindow = showGptToolsContextWindowCheckBox.isSelected
        settings.dependencyPatterns = dependencyPatternsArea.text
    }

    fun reset(settings: OtherSettingsState) {
        showAddFileActionCheckBox.isSelected = settings.showAddClassAction
        showAddRecursiveFileActionCheckBox.isSelected = settings.showAddRecursiveFileAction
        showFindUsagesAcrossProjectsActionCheckBox.isSelected = settings.showFindUsagesAcrossProjectsAction
        showFindImplAcrossProjectsActionCheckBox.isSelected = settings.showFindImplAcrossProjectsAction
        showConvertToMermaidActionCheckBox.isSelected = settings.showConvertToMermaidAction
        showPsiDependencyByMethodActionCheckBox.isSelected = settings.showPsiDependencyByMethodAction
        showCopyMethodSingleFileCheckBox.isSelected = settings.showCopyMethodSingleFile
        showAllMethodFileCheckBox.isSelected = settings.showAllMethodFile
        showOpenChatLogDirectoryActionCheckBox.isSelected = settings.showOpenChatLogDirectoryAction
        showGptToolsContextWindowCheckBox.isSelected = settings.showGptToolsContextWindow
        dependencyPatternsArea.text = settings.dependencyPatterns
    }
}