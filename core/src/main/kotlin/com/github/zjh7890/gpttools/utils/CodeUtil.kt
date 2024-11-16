package com.github.zjh7890.gpttools.utils

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.PlainTextLanguage

class Code(val language: Language, val languageId: String, val text: String, val isComplete: Boolean) {
    companion object {
        fun parse(content: String): Code {
            val lines = content.lines()
            var codeStarted = false
            var codeClosed = false
            var languageId = ""
            val codeBuilder = StringBuilder()
            var backtickCount = 0

            for (line in lines) {
                if (!codeStarted) {
                    val trimmedLine = line.trimStart()
                    if (trimmedLine.startsWith("```")) {
                        // 获取反引号数量
                        backtickCount = getLeadingBacktickCount(trimmedLine)
                        // 提取语言标识
                        languageId = trimmedLine.substring(backtickCount).trim()
                        codeStarted = true
                    }
                } else if (line.startsWith("`".repeat(backtickCount))) {
                    codeClosed = true
                    break
                } else {
                    codeBuilder.append(line).append("\n")
                }
            }

            // 去除首尾的空白字符
            var trimmedCode = codeBuilder.toString().trim()
            val language = findLanguage(languageId ?: "")

            // 如果内容不为空，但代码为空，则认为是 Markdown
            if (trimmedCode.isEmpty()) {
                return Code(findLanguage("markdown"), languageId, content.replace("\\n", "\n"), codeClosed)
            }

            if (languageId == "devin" || languageId == "devins") {
                trimmedCode = trimmedCode.replace("\\`\\`\\`", "```")
            }

            return Code(language, languageId, trimmedCode, codeClosed)
        }

        private fun getLeadingBacktickCount(line: String): Int {
            var count = 0
            while (count < line.length && line[count] == '`') {
                count++
            }
            return count
        }
        /**
         * Searches for a language by its name and returns the corresponding [Language] object. If the language is not found,
         * [PlainTextLanguage.INSTANCE] is returned.
         *
         * @param languageName The name of the language to find.
         * @return The [Language] object corresponding to the given name, or [PlainTextLanguage.INSTANCE] if the language is not found.
         */
        fun findLanguage(languageName: String): Language {
            val fixedLanguage = when (languageName) {
                "csharp" -> "c#"
                "cpp" -> "c++"
                else -> languageName
            }

            val languages = Language.getRegisteredLanguages()
            val registeredLanguages = languages
                .filter { it.displayName.isNotEmpty() }

            return registeredLanguages.find { it.displayName.equals(fixedLanguage, ignoreCase = true) }
                ?: PlainTextLanguage.INSTANCE
        }
    }
}