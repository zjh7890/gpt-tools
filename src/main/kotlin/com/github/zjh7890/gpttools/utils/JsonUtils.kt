package com.github.zjh7890.gpttools.utils

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.text.SimpleDateFormat

/**
 * json工具
 *
 * @author makejava
 * @version 1.0.0
 * @date 2021/08/14 10:37
 */
object JsonUtils {

    private val INSTANCE: ObjectMapper = ObjectMapper().apply {
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
        enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
        enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
        dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    }

    fun getInstance(): ObjectMapper = INSTANCE

    fun <T> parse(json: String, cls: Class<T>): T = try {
        INSTANCE.readValue(json, cls)
    } catch (e: IOException) {
        throw IllegalArgumentException(e)
    }

    fun <T> parse(json: String, type: TypeReference<T>): T = try {
        INSTANCE.readValue(json, type)
    } catch (e: IOException) {
        throw IllegalArgumentException(e)
    }

    fun toJson(obj: Any): String = try {
        INSTANCE.writeValueAsString(obj)
    } catch (e: JsonProcessingException) {
        throw IllegalArgumentException(e)
    }

    fun toJsonByFormat(obj: Any): String = try {
        INSTANCE.writerWithDefaultPrettyPrinter().writeValueAsString(obj)
    } catch (e: JsonProcessingException) {
        throw IllegalArgumentException(e)
    }

    fun readTree(obj: Any): JsonNode = try {
        when (obj) {
            is String -> INSTANCE.readTree(obj)
            is ByteArray -> INSTANCE.readTree(obj)
            is InputStream -> INSTANCE.readTree(obj)
            is URL -> INSTANCE.readTree(obj)
            is File -> INSTANCE.readTree(obj)
            else -> INSTANCE.readTree(toJson(obj))
        }
    } catch (e: IOException) {
        throw IllegalArgumentException(e)
    }
}