package com.github.zjh7890.gpttools.llm.custom.sse

import com.intellij.openapi.diagnostic.Logger
import com.nfeld.jsonpathkt.JsonPath
import com.nfeld.jsonpathkt.extension.read
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

class JSONBodyResponseCallback(private val responseFormat: String, private val callback: suspend (String) -> Unit) : Callback {
    private val logger = Logger.getInstance(JSONBodyResponseCallback::class.java)

    override fun onFailure(call: Call, e: IOException) {
        runBlocking {
            callback("error. ${e.message}")
        }
    }

    override fun onResponse(call: Call, response: Response) {
        val responseBody: String? = response.body?.string()
        if (responseFormat.isEmpty()) {
            runBlocking {
                logger.warn("Response Body: ${responseBody}") // 使用 Logger 打印
                callback(responseBody ?: "")
            }

            return
        }

        val responseContent: String = JsonPath.parse(responseBody)?.read(responseFormat) ?: ""

        runBlocking {
            callback(responseContent)
        }
    }
}