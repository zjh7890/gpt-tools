package com.github.zjh7890.gpttools.settings.actionPrompt

import com.fasterxml.jackson.core.type.TypeReference
import com.github.zjh7890.gpttools.utils.FileUtil
import com.github.zjh7890.gpttools.utils.JsonUtils
import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(name = "CodeTemplateApplicationSettingsService8", storages = [Storage("CodeTemplateAppSettings8.xml")])
class CodeTemplateApplicationSettingsService : PersistentStateComponent<CodeTemplateApplicationSettings> {

    private var myState = CodeTemplateApplicationSettings()

    @Synchronized
    override fun getState(): CodeTemplateApplicationSettings = myState
    @Synchronized
    override fun loadState(state: CodeTemplateApplicationSettings) {
        // 解析当前 myState 的模板
        val defaultTemplates = JsonUtils.parse(myState.templates, object : TypeReference<List<PromptTemplate>>() {})

        // 解析传入 state 的模板，并过滤掉描述以 * 开头的模板
        val customTemplates = JsonUtils.parse(state.templates, object : TypeReference<List<PromptTemplate>>() {})
            .filterNot { it.desc.trim(' ').startsWith("*") }

        // 合并模板列表，将默认模板放在前面
        val mergedTemplates = defaultTemplates + customTemplates

        // 序列化合并后的模板列表，并更新 myState
        myState.templates = JsonUtils.toJson(mergedTemplates)
    }

    companion object {
        val instance: CodeTemplateApplicationSettingsService
            get() = service()

        fun getTemplates(): List<PromptTemplate> {
            return JsonUtils.parse(instance.myState.templates, object : TypeReference<List<PromptTemplate>>() {})
        }

//        fun getInstance(): CodeTemplateApplicationSettingsService = service()
    }
}

class CodeTemplateApplicationSettings {
    var templates: String =
        JsonUtils.toJson(listOf(
        PromptTemplate(
            key = "ClassFinderAction",
            value = FileUtil.readResourceFile("prompt/ClassFinderAction.md"),
            desc = "* 递归获取参数返回值类信息"
        ),
        PromptTemplate(
            key = "CodeReviewPromptAction",
            value = FileUtil.readResourceFile("prompt/CodeReviewPromptAction.md"),
            desc = "* 生成 Code Review",
        ),
        PromptTemplate(
            key = "FileTestAction",
            value = FileUtil.readResourceFile("prompt/FileTestAction.md"),
            desc = "* 生成类单测"
        ),
        PromptTemplate(
            key = "GenerateMethodTestAction",
            value = FileUtil.readResourceFile("prompt/GenerateMethodTestAction.md"),
            desc = "* 生成方法单测"
        ),
        PromptTemplate(
            key = "GenerateRpcAction",
            value = FileUtil.readResourceFile("prompt/GenerateRpcAction.md"),
            desc = "* 生成RPC代码"
        ),
        PromptTemplate(
            key = "GenJsonAction",
            value = FileUtil.readResourceFile("prompt/GenJsonAction.md"),
            desc = "* 生成 json 示例"
        ),
        PromptTemplate(
            key = "StructConverterCodeGenAction",
            value = FileUtil.readResourceFile("prompt/StructConverterCodeGenAction.md"),
            desc = "* 生成 converter 代码"
        ),
        PromptTemplate(
            key = "ServiceImplAction",
            value = FileUtil.readResourceFile("prompt/ServiceImplAction.md"),
            desc = "* 实现 service 逻辑",
            input1 = "UML Text:",
            input2 = "Function Text"
        ),
        PromptTemplate(
            key = "ServiceImplAction",
            value = FileUtil.readResourceFile("prompt/GenApolloConfigByJson.md"),
            desc = "* 根据 json 生成 apollo 配置",
            input1 = "json",
        ),
        PromptTemplate(
            key = "ServiceImplAction",
            value = FileUtil.readResourceFile("prompt/AddRedisCache.md"),
            desc = "* 给函数增加 redis 缓存"
        ),
            PromptTemplate(
                key = "FixThisChunkAction",
                value = FileUtil.readResourceFile("prompt/FixThisChunkAction.md"),
                desc = "* 修复块"
            )
    ))
}

data class PromptTemplate (
    var key : String = "",
    var value : String = "",
    var desc : String = "",
    var input1 : String = "",
    var input2 : String = "",
    var input3 : String = "",
    var input4 : String = "",
    var input5 : String = ""
)
