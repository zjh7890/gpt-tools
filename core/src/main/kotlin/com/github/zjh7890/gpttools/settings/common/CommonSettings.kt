package com.github.zjh7890.gpttools.settings.common

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

import com.intellij.util.messages.Topic

// 定义设置变更监听器接口
interface CommonSettingsListener {
    companion object {
        val TOPIC = Topic.create("CommonSettings changed", CommonSettingsListener::class.java)
    }
    
    fun onSettingsChanged()
}

@State(
    name = "CommonSettings",
    storages = [Storage("com.github.zjh7890.gpttools.settings.common.CommonSettings.xml")]
)
@Service(Service.Level.APP)
class CommonSettings : PersistentStateComponent<CommonSettings.State> {

    private val messageBus = ApplicationManager.getApplication().messageBus

    companion object {
        fun getInstance(): CommonSettings {
            return ApplicationManager.getApplication().getService(CommonSettings::class.java)
        }
    }

    data class State(
        var generateDiff: Boolean = true,
        var withFiles: Boolean = true,
        var withDir: Boolean = false  // 添加 withDir 属性
    )

    private var state = State()

    override fun getState(): State {
        return state
    }

    override fun loadState(state: State) {
        this.state = state
        // 通知设置已变更
        messageBus.syncPublisher(CommonSettingsListener.TOPIC).onSettingsChanged()
    }

    var generateDiff: Boolean
        get() = state.generateDiff
        set(value) {
            state.generateDiff = value
            // 通知设置已变更
            messageBus.syncPublisher(CommonSettingsListener.TOPIC).onSettingsChanged()
        }

    var withFiles: Boolean
        get() = state.withFiles
        set(value) {
            state.withFiles = value
            // 通知设置已变更
            messageBus.syncPublisher(CommonSettingsListener.TOPIC).onSettingsChanged()
        }

    var withDir: Boolean  // 添加 withDir 属性的 getter 和 setter
        get() = state.withDir
        set(value) {
            state.withDir = value
            // 通知设置已变更
            messageBus.syncPublisher(CommonSettingsListener.TOPIC).onSettingsChanged()
        }
}