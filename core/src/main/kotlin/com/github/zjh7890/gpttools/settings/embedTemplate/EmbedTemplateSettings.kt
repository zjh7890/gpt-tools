package com.github.zjh7890.gpttools.settings.embedTemplate

import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.components.*

@State(name = "EmbedTemplateSettings", storages = [Storage("EmbedTemplateSettings.xml")])
@Service(Service.Level.APP)
class EmbedTemplateSettings : PersistentStateComponent<EmbedTemplateSettings.State> {

    data class State(
        var codeReviewTemplate: String = "",
        var fixThisTemplate: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun getStoredCodeReviewTemplate(): String {
        if (myState.codeReviewTemplate.isBlank()) {
            myState.codeReviewTemplate = FileUtil.readResourceFile("template/CodeReviewPromptAction.md")
        }
        return myState.codeReviewTemplate
    }

    fun getStoredFixThisTemplate(): String {
        if (myState.fixThisTemplate.isBlank()) {
            myState.fixThisTemplate = FileUtil.readResourceFile("template/FixThisChunkAction.md")
        }
        return myState.fixThisTemplate
    }

    companion object {
        val instance: EmbedTemplateSettings
            get() = service()
    }
}