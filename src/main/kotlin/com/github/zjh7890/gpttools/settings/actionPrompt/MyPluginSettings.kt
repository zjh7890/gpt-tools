package com.github.zjh7890.gpttools.settings.actionPrompt

import com.intellij.openapi.components.*

@State(
    name = "com.example.plugin.MyPluginSettings",
    storages = [Storage("MyPluginSettings.xml")]
)
@Service(Service.Level.APP)
class MyPluginSettings : PersistentStateComponent<MyPluginSettings.State> {

    class State {
        var myKey: String = "default value"
    }

    private var myState: State = State()

    companion object {
        val instance: MyPluginSettings
            get() = ServiceManager.getService(MyPluginSettings::class.java)
    }

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        myState.myKey = state.myKey
    }

//    var myKey: String
//        get() = myState.myKey
//        set(value) {
//            myState.myKey = value
//        }
}