package com.github.zjh7890.gpttools.utils

object TemplateUtils {
    /**
     * 替换字符串中的 ${GPT_} 格式的占位符。
     * @param template 包含占位符的字符串。
     * @param replacements 一个字典，其键为占位符（不包括大括号和前缀 $），值为替换内容。
     * @return 替换后的字符串。
     */
    fun replacePlaceholders(template: String, replacements: Map<String, String>): String {
        // 正则表达式用于匹配 ${GPT_} 格式的占位符
        val regex = "\\$\\{(GPT_[a-zA-Z0-9_]+)\\}".toRegex()
        return regex.replace(template) { matchResult ->
            // 提取占位符内部的键名
            val key = matchResult.groups[1]?.value
            // 根据键名从替换映射中获取替换值，如果未找到，则保留原文本
            replacements[key] ?: matchResult.value
        }
    }

    /**
     * Checks if all variables in the template are present in the variables map.
     * @param template The template string containing placeholders.
     * @param variablesMap A map of variables to check against.
     * @return A list of missing variable names. Empty if all variables are present.
     */
    fun checkVariables(template: String, variablesMap: Map<String, String>): List<String> {
        // Regex to match placeholders like ${GPT_}
        val regex = "\\$\\{(GPT_[a-zA-Z0-9_]+)\\}".toRegex()
        // Find all unique variables in the template
        val variablesInTemplate = regex.findAll(template).map { it.groupValues[1] }.toSet()
        // Identify missing variables
        val missingVariables = variablesInTemplate.filter { variablesMap[it].isNullOrBlank() }
        return missingVariables
    }
}