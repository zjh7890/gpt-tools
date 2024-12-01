package com.github.zjh7890.gpttools.llm.custom.sse

class GptToolsHttpException(val error: String, private val statusCode: Int) : RuntimeException(error) {
    override fun toString(): String {
        return "GptToolsHttpException(statusCode=$statusCode, message=$message)"
    }
}