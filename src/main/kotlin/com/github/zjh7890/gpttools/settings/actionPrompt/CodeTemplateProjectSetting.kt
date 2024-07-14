package com.github.zjh7890.gpttools.settings.actionPrompt

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "CodeTemplateProjectSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class CodeTemplateProjectSetting : SimplePersistentStateComponent<CodeTemplateProjectSettings>(
    CodeTemplateProjectSettings()
) {

    companion object {
        fun getInstance(project: Project): CodeTemplateProjectSetting = project.service()
    }
}

class CodeTemplateProjectSettings : BaseState() {
    var jsonData by string("{}")
}
