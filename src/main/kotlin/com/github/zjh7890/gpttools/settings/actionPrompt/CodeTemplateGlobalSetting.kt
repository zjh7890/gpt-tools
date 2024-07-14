package com.github.zjh7890.gpttools.settings.actionPrompt

import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(name = "CodeTemplateApplicationSettings3", storages = [Storage("CodeTemplateAppSettings3.xml")])
class CodeTemplateApplicationSettingsService : PersistentStateComponent<CodeTemplateApplicationSettings> {

    private var myState = CodeTemplateApplicationSettings()

    override fun getState(): CodeTemplateApplicationSettings = myState
    override fun loadState(state: CodeTemplateApplicationSettings) {
        myState = state
    }

    companion object {
        fun getInstance(): CodeTemplateApplicationSettingsService = service()
    }
}

class CodeTemplateApplicationSettings : BaseState() {
    val templates: MutableMap<String, PromptTemplate> = mutableMapOf(
        "DiffAction" to PromptTemplate(
            key = "DiffAction",
            value = FileUtil.readResourceFile("prompt/DiffAction.md"),
            desc = "获取更改代码"
        ),
        "FileTestAction" to PromptTemplate(
            key = "FileTestAction",
            value = FileUtil.readResourceFile("prompt/FileTestAction.md"),
            desc = "生成类单测"
        ),
        "GenerateMethodTestAction" to PromptTemplate(
            key = "GenerateMethodTestAction",
            value = FileUtil.readResourceFile("prompt/GenerateMethodTestAction.md"),
            desc = "生成方法单测"
        ),
        "GenerateRpcAction" to PromptTemplate(
            key = "GenerateRpcAction",
            value = FileUtil.readResourceFile("prompt/GenerateRpcAction.md"),
            desc = "生成RPC代码"
        ),
        "StructConverterCodeGenAction" to PromptTemplate(
            key = "StructConverterCodeGenAction",
            value = FileUtil.readResourceFile("prompt/StructConverterCodeGenAction.md"),
            desc = "生成 converter 代码"
        )
    )
}

data class PromptTemplate (
    val key : String = "",
    var value : String = "",
    val desc : String = ""
)
