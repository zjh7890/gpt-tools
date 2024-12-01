package com.github.zjh7890.gpttools.settings.template

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
//        val defaultTemplates = JsonUtils.parse(myState.templates, object : TypeReference<List<PromptTemplate>>() {})
//
//        // 解析传入 state 的模板，并过滤掉描述以 * 开头的模板
//        val customTemplates = JsonUtils.parse(state.templates, object : TypeReference<List<PromptTemplate>>() {})
//            .filterNot { it.desc.trim(' ').startsWith("*") }
//            .map {
//                it.apply {
//                }
//            }
//
//        // 合并模板列表，将默认模板放在前面
//        val mergedTemplates = defaultTemplates + customTemplates
//
//        // 序列化合并后的模板列表，并更新 myState
//        myState.templates = JsonUtils.toJson(mergedTemplates)
        myState.templates = state.templates
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
        JsonUtils.toJson(
            listOf(
                PromptTemplate(
                    value = FileUtil.readResourceFile("template/ChatWithSelectedCode.md"),
                    desc = "Chat with selected code",
                    showInEditorPopupMenu = false,
                    newChat = false
                ),
                PromptTemplate(
                    value = FileUtil.readResourceFile("template/ChatWithMethod.md"),
                    desc = "Chat with method",
                    showInEditorPopupMenu = false,
                    newChat = false
                ),
                PromptTemplate(
                    value = FileUtil.readResourceFile("template/ClassFinderAction.md"),
                    desc = "查找方法及相关内容",
                    showInFloatingToolBar = false
                ),
                PromptTemplate(
                    value = FileUtil.readResourceFile("template/FileTestAction.md"),
                    desc = "生成类单测",
                    showInFloatingToolBar = false
                ),
                PromptTemplate(
                    value = FileUtil.readResourceFile("template/GenerateMethodTestAction.md"),
                    desc = "根据调用日志生成方法单测",
                    input1 = "调用 json:",
                    showInFloatingToolBar = false
                ),
                PromptTemplate(
                    value = FileUtil.readResourceFile("template/GenerateRpcAction.md"),
                    desc = "生成RPC代码",
                    showInFloatingToolBar = false
                ),
                PromptTemplate(
                    value = FileUtil.readResourceFile("template/GenJsonAction.md"),
                    desc = "生成 json 示例",
                    showInFloatingToolBar = false
                ),
                PromptTemplate(
                    value = FileUtil.readResourceFile("template/StructConverterCodeGenAction.md"),
                    desc = "生成 converter 代码",
                    showInFloatingToolBar = false
                ),
//                PromptTemplate(
//                    value = FileUtil.readResourceFile("template/ServiceImplAction.md"),
//                    desc = "实现 service 逻辑",
//                    input1 = "UML Text:",
//                    input2 = "Function Text",
//                    showInFloatingToolBar = false
//                ),
                PromptTemplate(
                    value = FileUtil.readResourceFile("template/GenApolloConfigByJson.md"),
                    desc = "根据 json 生成 apollo 配置",
                    input1 = "json",
                    showInFloatingToolBar = false
                ),
                PromptTemplate(
                    value = FileUtil.readResourceFile("template/AddRedisCache.md"),
                    desc = "给函数增加 redis 缓存",
                    showInFloatingToolBar = false
                )
            )
        )
}

data class PromptTemplate (
    var value : String = "",
    var desc : String = "",
    var input1 : String = "",
    var input2 : String = "",
    var input3 : String = "",
    var input4 : String = "",
    var input5 : String = "",
    var showInEditorPopupMenu: Boolean = true,
    var showInFloatingToolBar: Boolean = true,
    var newChat: Boolean = true
)
