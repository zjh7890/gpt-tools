package com.github.zjh7890.gpttools.settings.other

import com.intellij.ui.components.JBCheckBox
import javax.swing.BoxLayout
import javax.swing.JPanel

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

    val component: JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(showAddFileActionCheckBox)
        add(showAddRecursiveFileActionCheckBox)
        add(showFindUsagesAcrossProjectsActionCheckBox)
        add(showFindImplAcrossProjectsActionCheckBox)
        add(showConvertToMermaidActionCheckBox)
        add(showPsiDependencyByMethodActionCheckBox)
        add(showCopyMethodSingleFileCheckBox)
        add(showAllMethodFileCheckBox)
        add(showGptToolsContextWindowCheckBox)
    }

    fun isModified(settings: OtherSettingsState): Boolean {
        return showAddFileActionCheckBox.isSelected != settings.showAddFileAction ||
                showAddRecursiveFileActionCheckBox.isSelected != settings.showAddRecursiveFileAction ||
                showFindUsagesAcrossProjectsActionCheckBox.isSelected != settings.showFindUsagesAcrossProjectsAction ||
                showFindImplAcrossProjectsActionCheckBox.isSelected != settings.showFindImplAcrossProjectsAction ||
                showConvertToMermaidActionCheckBox.isSelected != settings.showConvertToMermaidAction ||
                showPsiDependencyByMethodActionCheckBox.isSelected != settings.showPsiDependencyByMethodAction ||
                showCopyMethodSingleFileCheckBox.isSelected != settings.showCopyMethodSingleFile ||
                showAllMethodFileCheckBox.isSelected != settings.showAllMethodFile ||
                showOpenChatLogDirectoryActionCheckBox.isSelected != settings.showOpenChatLogDirectoryAction ||
                showGptToolsContextWindowCheckBox.isSelected != settings.showGptToolsContextWindow
    }

    fun apply(settings: OtherSettingsState) {
        settings.showAddFileAction = showAddFileActionCheckBox.isSelected
        settings.showAddRecursiveFileAction = showAddRecursiveFileActionCheckBox.isSelected
        settings.showFindUsagesAcrossProjectsAction = showFindUsagesAcrossProjectsActionCheckBox.isSelected
        settings.showFindImplAcrossProjectsAction = showFindImplAcrossProjectsActionCheckBox.isSelected
        settings.showConvertToMermaidAction = showConvertToMermaidActionCheckBox.isSelected
        settings.showPsiDependencyByMethodAction = showPsiDependencyByMethodActionCheckBox.isSelected
        settings.showCopyMethodSingleFile = showCopyMethodSingleFileCheckBox.isSelected
        settings.showAllMethodFile = showAllMethodFileCheckBox.isSelected
        settings.showOpenChatLogDirectoryAction = showOpenChatLogDirectoryActionCheckBox.isSelected
        settings.showGptToolsContextWindow = showGptToolsContextWindowCheckBox.isSelected
    }

    fun reset(settings: OtherSettingsState) {
        showAddFileActionCheckBox.isSelected = settings.showAddFileAction
        showAddRecursiveFileActionCheckBox.isSelected = settings.showAddRecursiveFileAction
        showFindUsagesAcrossProjectsActionCheckBox.isSelected = settings.showFindUsagesAcrossProjectsAction
        showFindImplAcrossProjectsActionCheckBox.isSelected = settings.showFindImplAcrossProjectsAction
        showConvertToMermaidActionCheckBox.isSelected = settings.showConvertToMermaidAction
        showPsiDependencyByMethodActionCheckBox.isSelected = settings.showPsiDependencyByMethodAction
        showCopyMethodSingleFileCheckBox.isSelected = settings.showCopyMethodSingleFile
        showAllMethodFileCheckBox.isSelected = settings.showAllMethodFile
        showOpenChatLogDirectoryActionCheckBox.isSelected = settings.showOpenChatLogDirectoryAction
        showGptToolsContextWindowCheckBox.isSelected = settings.showGptToolsContextWindow
    }
}