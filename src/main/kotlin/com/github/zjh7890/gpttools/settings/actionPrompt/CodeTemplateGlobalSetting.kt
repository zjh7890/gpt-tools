package com.github.zjh7890.gpttools.settings.actionPrompt

import com.github.zjh7890.gpttools.utils.FileUtil
import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(name = "CodeTemplateApplicationSettings5", storages = [Storage("CodeTemplateAppSettings5.xml")])
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
    var templates: List<PromptTemplate> = listOf(
        PromptTemplate(
            key = "ClassFinderAction",
            value = FileUtil.readResourceFile("prompt/ClassFinderAction.md"),
            desc = "Find Method Related Class"
        ),
        PromptTemplate(
            key = "CodeReviewPromptAction",
            value = FileUtil.readResourceFile("prompt/CodeReviewPromptAction.md.md"),
            desc = "生成 Code Review"
        ),
        PromptTemplate(
            key = "DiffAction",
            value = FileUtil.readResourceFile("prompt/DiffAction.md"),
            desc = "获取更改代码"
        ),
        PromptTemplate(
            key = "FileTestAction",
            value = FileUtil.readResourceFile("prompt/FileTestAction.md"),
            desc = "生成类单测"
        ),
        PromptTemplate(
            key = "GenerateMethodTestAction",
            value = FileUtil.readResourceFile("prompt/GenerateMethodTestAction.md"),
            desc = "生成方法单测"
        ),
        PromptTemplate(
            key = "GenerateRpcAction",
            value = FileUtil.readResourceFile("prompt/GenerateRpcAction.md"),
            desc = "生成RPC代码"
        ),
        PromptTemplate(
            key = "GenJsonAction",
            value = FileUtil.readResourceFile("prompt/GenJsonAction.md"),
            desc = "生成 json 示例"
        ),
        PromptTemplate(
            key = "StructConverterCodeGenAction",
            value = FileUtil.readResourceFile("prompt/StructConverterCodeGenAction.md"),
            desc = "生成 converter 代码"
        )
    )
}

data class PromptTemplate (
    var key : String = "",
    var value : String = "",
    var desc : String = ""
)
