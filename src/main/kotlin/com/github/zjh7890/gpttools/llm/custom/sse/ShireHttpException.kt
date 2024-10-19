package com.github.zjh7890.gpttools.llm.custom.sse

class ShireHttpException(val error: String, private val statusCode: Int) : RuntimeException(error) {
    override fun toString(): String {
        return "ShireHttpException(statusCode=$statusCode, message=$message)"
    }
}