package com.github.zjh7890.gpttools.settings.llmSettings

val OPENAI_MODEL = arrayOf("gpt-4-turbo-2024-04-09", "gpt-3.5-turbo-16k", "gpt-4", "custom")
val AI_ENGINES = arrayOf("OpenAI", "Custom", "Azure", "XingHuo")

enum class AIEngines {
    OpenAI, Custom, Azure, XingHuo
}

val GIT_TYPE = arrayOf("Github" , "Gitlab")
val DEFAULT_GIT_TYPE = GIT_TYPE[0]
enum class XingHuoApiVersion(val value: Double) {
    V1(1.1), V2(2.1), V3(3.1), V3_5(3.5);

    companion object {
        fun of(str: String): XingHuoApiVersion = when (str) {
            "V1" -> V1
            "V2" -> V2
            "V3" -> V3
            "V3_5" -> V3_5
            else -> V3
        }
    }
}

enum class ResponseType {
    SSE, JSON;
}


val DEFAULT_AI_ENGINE = AI_ENGINES[0]

val DEFAULT_AI_MODEL = OPENAI_MODEL[0]

val HUMAN_LANGUAGES = arrayOf("English", "中文")
val DEFAULT_HUMAN_LANGUAGE = HUMAN_LANGUAGES[0]
val MAX_TOKEN_LENGTH = 4000 * 10000
val SELECT_CUSTOM_MODEL = "custom"
