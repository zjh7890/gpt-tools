package com.github.zjh7890.gpttools.settings.llmSettings

import com.github.zjh7890.gpttools.settings.*
import com.github.zjh7890.gpttools.settings.llmSettings.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service
@State(name = "GptToolSettingsState", storages = [Storage("GptToolSettingsState.xml")])
class GptToolSettings : PersistentStateComponent<GptToolSettings> {
    var gitType = DEFAULT_GIT_TYPE
    var githubToken = ""
    var gitlabToken = ""
    var gitlabUrl = ""
    var openAiKey = ""
    var openAiModel = DEFAULT_AI_MODEL
    var delaySeconds = ""

    var aiEngine = DEFAULT_AI_ENGINE
    var customOpenAiHost = ""
    var customEngineServer = ""
    var customEngineToken = ""
    var customPrompts = ""
    var customModel = ""

    // 星火有三个版本 https://console.xfyun.cn/services/bm3
    var xingHuoApiVersion = XingHuoApiVersion.V3
    var xingHuoAppId = ""
    var xingHuoApiSecrect = ""
    var xingHuoApiKey = ""


    /**
     * 自定义引擎返回的数据格式是否是 [SSE](https://www.ruanyifeng.com/blog/2017/05/server-sent_events.html) 格式
     */
    var customEngineResponseType = ResponseType.SSE.name
    /**
     * should be a json path
     */
    var customEngineResponseFormat = ""
    /**
     * should be a json
     * {
     *     'customHeaders': { 'headerName': 'headerValue', 'headerName2': 'headerValue2' ... },
     *     'customFields' : { 'bodyFieldName': 'bodyFieldValue', 'bodyFieldName2': 'bodyFieldValue2' ... }
     *     'messageKey': {'role': 'roleKeyName', 'content': 'contentKeyName'}
     * }
     *
     * @see docs/custom-llm-server.md
     */
    var customEngineRequestFormat = ""

    @OptionTag(value = "lastCheckTime", converter = ZonedDateTimeConverter::class)
    var lastCheck: ZonedDateTime? = null


    var language = DEFAULT_HUMAN_LANGUAGE
    var maxTokenLength = MAX_TOKEN_LENGTH.toString()

    fun fetchMaxTokenLength(): Int = maxTokenLength.toIntOrNull() ?: MAX_TOKEN_LENGTH

    fun fetchLocalLanguage() : String {
        //todo: refactor, this is hardcode and magic number. Maybe it needs to match with AbstractBundle.getLocale()
        if (language.equals("中文")) return "zh"
        return "en"
    }

    @Synchronized
    override fun getState(): GptToolSettings = this

    @Synchronized
    override fun loadState(state: GptToolSettings) = XmlSerializerUtil.copyBean(state, this)

    companion object {
        val maxTokenLength: Int get() = getInstance().fetchMaxTokenLength()
        val language: String get() = getInstance().fetchLocalLanguage()

        var lastCheckTime: ZonedDateTime? = getInstance().lastCheck

        fun getInstance(): GptToolSettings {
            return ApplicationManager.getApplication().getService(GptToolSettings::class.java).state
        }

        class ZonedDateTimeConverter : Converter<ZonedDateTime>() {
            override fun toString(value: ZonedDateTime): String? = value.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

            override fun fromString(value: String): ZonedDateTime? {
                return ZonedDateTime.parse(value, DateTimeFormatter.ISO_ZONED_DATE_TIME)
            }
        }
    }

}
