package com.github.zjh7890.gpttools.settings.other

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "com.github.zjh7890.gpttools.settings.other.OtherSettingsState",
    storages = [Storage("GptToolsOtherSettings.xml")]
)
class OtherSettingsState : PersistentStateComponent<OtherSettingsState> {
    var showAddFileAction: Boolean = false
    var showAddRecursiveFileAction: Boolean = false
    var showFindUsagesAcrossProjectsAction: Boolean = true
    var showFindImplAcrossProjectsAction: Boolean = true
    var showConvertToMermaidAction: Boolean = false
    var showPsiDependencyByMethodAction: Boolean = false
    var showCopyMethodSingleFile: Boolean = false
    var showAllMethodFile: Boolean = false
    var showOpenChatLogDirectoryAction: Boolean = false
    var showGptToolsContextWindow: Boolean = false

    @Synchronized
    override fun getState(): OtherSettingsState = this

    @Synchronized
    override fun loadState(state: OtherSettingsState) = XmlSerializerUtil.copyBean(state, this)

    companion object {
        fun getInstance(): OtherSettingsState {
            return ApplicationManager.getApplication().getService(OtherSettingsState::class.java)
        }
    }
}