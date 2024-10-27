package com.github.zjh7890.gpttools.settings.common

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(
    name = "CommonSettings",
    storages = [Storage("com.github.zjh7890.gpttools.settings.common.CommonSettings.xml")]
)
@Service(Service.Level.PROJECT)
class CommonSettings : PersistentStateComponent<CommonSettings.State> {

    companion object {
        fun getInstance(project: Project): CommonSettings {
            return project.getService(CommonSettings::class.java)
        }
    }

    data class State(
        var generateDiff: Boolean = false,
        var withContext: Boolean = true
    )

    private var state = State()
    

    override fun getState(): State {
        return state
    }

    override fun loadState(state: State) {
        this.state = state
    }

    var generateDiff: Boolean
        get() = state.generateDiff
        set(value) {
            state.generateDiff = value
        }

    var withContext: Boolean
        get() = state.withContext
        set(value) {
            state.withContext = value
        }
}
