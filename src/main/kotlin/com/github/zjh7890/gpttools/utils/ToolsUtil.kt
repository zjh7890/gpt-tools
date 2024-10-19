package com.github.zjh7890.gpttools.utils

import com.azure.ai.openai.models.ChatCompletionsFunctionToolDefinition
import com.azure.ai.openai.models.ChatCompletionsToolDefinition
import com.azure.ai.openai.models.FunctionDefinition
import com.azure.core.util.BinaryData
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters

@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,              // Class, interface, or object, including annotation class
    AnnotationTarget.ANNOTATION_CLASS,   // Annotation class only
    AnnotationTarget.TYPE_PARAMETER,     // Generic type parameter
    AnnotationTarget.PROPERTY,           // Property
    AnnotationTarget.FIELD,              // Field, including property's backing field
    AnnotationTarget.LOCAL_VARIABLE,     // Local variable
    AnnotationTarget.VALUE_PARAMETER,    // Value parameter of a function or constructor
    AnnotationTarget.FUNCTION,           // Function (excluding constructors)
    AnnotationTarget.PROPERTY_GETTER,    // Property getter only
    AnnotationTarget.PROPERTY_SETTER,    // Property setter only
    AnnotationTarget.TYPE,               // Type usage (e.g., variable type declaration)
    AnnotationTarget.FILE,               // File
    AnnotationTarget.TYPEALIAS           // Type alias
)
annotation class Desc(val description: String)

fun extractParametersToJsonSchema(parameter: KParameter): Map<String, Any?>? {
    val type = parameter.type
    val jsonType = mapKotlinTypeToJsonType(type)
    val description = parameter.findAnnotation<Desc>()?.description ?: "No description"

    // Identify and process array types
    val properties = when (jsonType) {
        "object" -> {
            (type.classifier as? KClass<*>)?.primaryConstructor?.parameters?.associate { subParam ->
                val key: String = subParam.name ?: "unknown"
                val value: Any? = extractParametersToJsonSchema(subParam)
                key to value
            }
        }
        "array" -> {
            // Assuming the array's element type can be extracted as the first type argument
            val elementType = type.arguments.firstOrNull()?.type?.classifier as? KClass<*>
            val elementSchema = elementType?.let { elementClass ->
                // Recursively handle all properties of the element class
                elementClass.primaryConstructor?.parameters?.associate { elementParam ->
                    val key: String = elementParam.name ?: "unknown"
                    val value: Any? = extractParametersToJsonSchema(elementParam)
                    key to value
                }
            }
            mapOf(
                "type" to "array",
                "items" to elementSchema,
                "description" to description
            )
        }
        else -> {
            mapOf(
                "type" to jsonType,
                "description" to description
            )
        }
    }

    return properties
}

fun extractParametersToJsonRegression(type: KType): Map<String, Any?>? {
    val classifier = type.classifier as KClass<*>
    val parameter = classifier.primaryConstructor!!.parameters.first()
    return extractParametersToJsonSchema(parameter)
}

fun extractToolDefinitionsFromAnnotations(clazz: KClass<*>): List<ChatCompletionsToolDefinition> {
    val objectMapper = jacksonObjectMapper() // Create an instance of Jackson ObjectMapper

    val map = clazz.memberFunctions.filter { function ->
        function.findAnnotation<Desc>() != null
    }.map { function ->
        val methodDescription = function.findAnnotation<Desc>()?.description ?: "No description"
        val parameters = function.valueParameters.associate { subParam ->
            val key: String = subParam.name ?: "unknown"
            val value: Any? = extractParametersToJsonSchema(subParam)
            key to value
        }
        val functionDefinition = FunctionDefinition(function.name)
        functionDefinition.setDescription(methodDescription)
        functionDefinition.setParameters(
            BinaryData.fromObject(
                mapOf(
                    "type" to "object",
                    "properties" to parameters
                )
            )
        ) // Serialize the parameters map to a JSON string
        ChatCompletionsFunctionToolDefinition(functionDefinition)
    }
    return map
}

fun mapKotlinTypeToJsonType(kotlinType: KType): String {
    val classifier = kotlinType.classifier as? KClass<*>
    return when (classifier) {
        String::class -> "string"
        Int::class, Long::class, Float::class, Double::class, Short::class, Byte::class -> "number"
        Boolean::class -> "boolean"
        List::class, Set::class, Collection::class -> "array"
        else -> if (classifier != null && classifier.isData) "object" else "unknown"
    }
}


