package com.github.zjh7890.gpttools

import com.azure.ai.openai.OpenAIClientBuilder
import com.azure.ai.openai.models.*
import com.azure.core.credential.AzureKeyCredential
import com.jetbrains.rd.util.string.println

fun parseGitDiff(diffText: String): String {
    val result = mutableListOf<Map<String, Any>>()
    val lines = diffText.split("\n")
    var currentLineOld = 0
    var currentLineNew = 0

    lines.forEach { line ->
        when {
            line.startsWith("@@") -> {
                // Extract line numbers from the header
                val headerParts = line.split(" ")
                val oldLineInfo = headerParts[1]
                val newLineInfo = headerParts[2]

                // Parse the start lines
                val oldStart = oldLineInfo.substring(1).split(",")[0].toInt()
                val newStart = newLineInfo.substring(1).split(",")[0].toInt()

                currentLineOld = oldStart
                currentLineNew = newStart - 1  // Adjust because the line following will increment
            }
            line.startsWith("-") -> {
                // Deleted line
                currentLineOld++
                result.add(mapOf(
                    "type" to "DELETE_MARK",
                    "markPos" to listOf(currentLineOld, currentLineOld)  // Adjust based on exact needs
                ))
            }
            line.startsWith("+") -> {
                // Added line
                currentLineNew++
                result.add(mapOf(
                    "type" to "ADD",
                    "lineStart" to currentLineNew,
                    "lineEnd" to currentLineNew
                ))
            }
            else -> {
                // Regular line or other kind of line, increment both
                currentLineOld++
                currentLineNew++
            }
        }
    }

    return result.map { entry ->
        entry.entries.joinToString(", ") { (key, value) ->
            "\"$key\": ${if (value is List<*>) value.joinToString(prefix = "[", postfix = "]") else "\"$value\""}"
        }
    }.joinToString(prefix = "[", postfix = "]") { "{ $it }" }
}

// 示例调用
val diffText = """
diff --git a/live-link-core/src/test/java/com/yupaopao/live/link/core/GlobalLazyProcessor.java b/live-link-core/src/test/java/com/yupaopao/live/link/core/GlobalLazyProcessor.java
index 850dd6a8..329367a0 100644
--- a/live-link-core/src/test/java/com/yupaopao/live/link/core/GlobalLazyProcessor.java
+++ b/live-link-core/src/test/java/com/yupaopao/live/link/core/GlobalLazyProcessor.java
@@ -5,17 +6,11 @@ import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
 import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
 import org.springframework.stereotype.Component;

-/**
- * @Author: zhujunhua
- * @Date: 2023/6/19 19:36
- */
 @Component
 public class GlobalLazyProcessor implements BeanFactoryPostProcessor {

     @Override
     public void postProcessBeanFactory( ConfigurableListableBeanFactory beanFactory ) throws BeansException {
-        for ( String name : beanFactory.getBeanDefinitionNames() ) {
-            //beanFactory.getBeanDefinition( name ).setLazyInit( true );
-        }
+
     }
 }
"""

fun main2() {
//    println(parseGitDiff(diffText))

    val client = OpenAIClientBuilder()
        .credential(AzureKeyCredential(""))
        .endpoint("https://dashscope.aliyuncs.com/compatible-mode/v1")
        .buildClient()

    val chatMessages: MutableList<ChatRequestMessage> = ArrayList()
    chatMessages.add(ChatRequestSystemMessage("You are a helpful assistant. You will talk like a pirate."))
    chatMessages.add(ChatRequestUserMessage("Can you help me?"))
    chatMessages.add(ChatRequestAssistantMessage("Of course, me hearty! What can I do for ye?"))
    chatMessages.add(ChatRequestUserMessage("What's the best way to train a parrot?"))

    val chatCompletions = client.getChatCompletions(
        "qwen-max",
        ChatCompletionsOptions(chatMessages)
    )

    System.out.printf("Model ID=%s is created at %s.%n", chatCompletions.id, chatCompletions.createdAt)
    for (choice in chatCompletions.choices) {
        val message = choice.message
        System.out.printf("Index: %d, Chat Role: %s.%n", choice.index, message.role)
        println("Message:")
        println(message.content)
    }


}

fun main() {
    val path = "/Users/zjh/.m2/repository/com/yupaopao/platform/config-client/0.10.21/config-client-0.10.21.jar!/com/ctrip/framework/apollo/Config.class"
    val regex = ".*/repository/(.+)/([^/]+)/([^/]+)/([^/]+!)/(.*)".toRegex()
    val matchResult = regex.find(path) ?: return

    val (groupPath, artifactId, version) = matchResult.destructured
    println("fdsklajf")
}
