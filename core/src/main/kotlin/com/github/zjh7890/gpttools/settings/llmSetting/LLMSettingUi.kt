package com.github.zjh7890.gpttools.settings.llmSetting

import com.github.zjh7890.gpttools.LLMCoroutineScope
import com.github.zjh7890.gpttools.components.LeftRightComponent
import com.github.zjh7890.gpttools.llm.ChatMessage
import com.github.zjh7890.gpttools.llm.LlmConfig
import com.github.zjh7890.gpttools.llm.LlmProvider
import com.github.zjh7890.gpttools.toolWindow.chat.ChatRole
import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.google.gson.Gson
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.awt.*
import java.awt.event.ItemEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class LLMSettingUi : ConfigurableUi<LLMSettingsState> {

    private var mainPanel: JComponent? = null

    // 使用 DefaultListModel 管理 ShireSetting 项目
    private val listModel = DefaultListModel<LLMSetting>()

    // 创建一个 JBList 并设置模型
    private val settingsList = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        // 自定义单元格渲染器，显示配置项的描述或名称
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is LLMSetting) {
                val provider = value.provider.name
                val displayName = if (value.name.isNullOrEmpty()) value.modelName else value.name
                text = "$displayName"
            }
                return this
            }
        }
    }

    // 右侧详细信息面板
    private val detailPanel = JPanel(BorderLayout())

    // 当前选中的配置项组件
    private var selectedConfiguration: ShireConfigItemComponent? = null
    
    // 新增下拉框成员变量
    private val defaultModelComboBox = JComboBox<LLMSetting>().apply {
        renderer = createComboBoxRenderer()
    }
    private val formatCopyModelComboBox = JComboBox<LLMSetting>().apply {
        renderer = createComboBoxRenderer()
    }

    init {
        // 创建工具栏装饰器
        val toolbarDecorator = ToolbarDecorator.createDecorator(settingsList)
            .setAddAction { addConfiguration() }
            .setRemoveAction { removeSelectedConfiguration() }
            .setMoveUpAction { moveConfigurationUp() }
            .setMoveDownAction { moveConfigurationDown() }
            .addExtraAction(object : AnAction("Export to Clipboard", "Export configurations to JSON and copy to clipboard", AllIcons.ToolbarDecorator.Export) {
                override fun actionPerformed(e: AnActionEvent) {
                    exportConfigurationsToJson()
                }
            })
            .addExtraAction(object : AnAction("Import from Clipboard", "Import configurations from JSON string", AllIcons.ToolbarDecorator.Import) {
                override fun actionPerformed(e: AnActionEvent) {
                    importConfigurationsFromJson()
                }
            })
            .addExtraAction(object : AnAction("Copy Configuration", "Copy selected configuration", AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) {
                    copySelectedConfiguration()
                }
            })

        // 创建左侧列表面板
        val listPanel = toolbarDecorator.createPanel().apply {
            preferredSize = Dimension(250, 400) // 根据需要调整尺寸
        }

        // 监听列表选择变化
        settingsList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedSetting = settingsList.selectedValue
                updateDetailPanel(selectedSetting)
            }
        }

        // 初始化详细面板为空
        updateDetailPanel(null)

        // 使用 LeftRightComponent 组合左侧列表和右侧详细面板
        val combinedPanel = LeftRightComponent(listPanel, detailPanel).mainPanel

        

        // 创建下拉框面板
        val comboBoxPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel("Default Model:"))
            add(defaultModelComboBox)
            add(Box.createHorizontalStrut(10))
            add(JLabel("Format Copy Model:")) 
            add(formatCopyModelComboBox)
        }

        // 创建包含下拉框和原有面板的主面板
        val mainWrapperPanel = JPanel(BorderLayout()).apply {
            add(comboBoxPanel, BorderLayout.NORTH)
            add(combinedPanel, BorderLayout.CENTER)
        }

        // 监听列表变化,更新下拉框选项
        settingsList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                // 更新下拉框选项
                updateComboBoxes()
                updateDetailPanel(settingsList.selectedValue)
            }
        }

        // 初始化主选项卡
        mainPanel = mainWrapperPanel
    }

    override fun reset(settings: LLMSettingsState) {
        // 清空当前列表和配置项
        listModel.clear()

        // 使用副本填充列表模型
        settings.getFillSettings().forEach { setting ->
            listModel.addElement(setting.copy())
        }

        // 选择第一个配置项
        if (!listModel.isEmpty) {
            settingsList.selectedIndex = 0
        } else {
            updateDetailPanel(null)
        }
    }

    override fun isModified(settings: LLMSettingsState): Boolean {
        val currentDefaultModelName = (defaultModelComboBox.selectedItem as? LLMSetting)?.name
        val currentFormatCopyModelName = (formatCopyModelComboBox.selectedItem as? LLMSetting)?.name

        // 比较下拉框选中值与 settings 中保存的值
        if (currentDefaultModelName != settings.defaultModelName ||
            currentFormatCopyModelName != settings.formatCopyModelName) {
            return true
        }

        if (listModel.size != settings.getFillSettings().size) return true
        for (i in 0 until listModel.size) {
            val current = listModel.getElementAt(i)
            val original = settings.getFillSettings()[i]
            if (current.name != original.name ||
                current.temperature != original.temperature ||
                current.apiHost != original.apiHost ||
                current.modelName != original.modelName ||
                current.apiToken != original.apiToken ||
                current.azureModel != original.azureModel ||
                current.azureEndpoint != original.azureEndpoint ||
                current.azureApiKey != original.azureApiKey ||
                current.stream != original.stream
            ) {
                return true
            }
        }
        return false
    }

    override fun apply(settings: LLMSettingsState) {
        // 保存下拉框选中的值
        settings.defaultModelName = (defaultModelComboBox.selectedItem as? LLMSetting)?.name
        settings.formatCopyModelName = (formatCopyModelComboBox.selectedItem as? LLMSetting)?.name

        // 清空现有设置并应用列表中的配置项
        val newSettings = mutableListOf<LLMSetting>()
        for (i in 0 until listModel.size) {
            newSettings.add(listModel.getElementAt(i).copy())
        }
        settings.settings = newSettings
        LLMSettingsState.getInstance().notifySettingsChanged()
    }

    // 创建下拉框渲染器的辅助方法
    private fun createComboBoxRenderer() = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is LLMSetting) {
                text = value.name
            }
            return this
        }
    }

    override fun getComponent(): JComponent {
        return mainPanel!!
    }

    // 添加新配置项
    private fun addConfiguration() {
        SwingUtilities.invokeLater {
            // 显示对话框以获取新配置项信息
            val newSetting = showShireSettingDialog()
            if (newSetting != null) {
                listModel.addElement(newSetting)
            }
        }
    }

    // 移除选中的配置项
    private fun removeSelectedConfiguration() {
        val selectedIndex = settingsList.selectedIndex
        if (selectedIndex != -1) {
            listModel.remove(selectedIndex)
            // 选择下一个或上一个配置项
            if (listModel.size > 0) {
                settingsList.selectedIndex = if (selectedIndex >= listModel.size) listModel.size - 1 else selectedIndex
            }
        } else {
            Messages.showWarningDialog("No configuration selected to remove.", "Remove Failed")
        }
    }

    // 上移配置项
    private fun moveConfigurationUp() {
        val selectedIndex = settingsList.selectedIndex
        if (selectedIndex > 0) {
            val element = listModel.remove(selectedIndex)
            listModel.add(selectedIndex - 1, element)
            settingsList.selectedIndex = selectedIndex - 1
        }
    }

    // 下移配置项
    private fun moveConfigurationDown() {
        val selectedIndex = settingsList.selectedIndex
        if (selectedIndex != -1 && selectedIndex < listModel.size - 1) {
            val element = listModel.remove(selectedIndex)
            listModel.add(selectedIndex + 1, element)
            settingsList.selectedIndex = selectedIndex + 1
        }
    }

    // 导出配置项到 JSON 并复制到剪贴板
    private fun exportConfigurationsToJson() {
        val configurationsList = mutableListOf<LLMSetting>()
        for (i in 0 until listModel.size) {
            configurationsList.add(listModel.getElementAt(i))
        }
        val json = Gson().toJson(configurationsList)
        ClipboardUtils.copyToClipboard(json)
        Messages.showInfoMessage("Configurations exported to clipboard.", "Export Successful")
    }

    // 从剪贴板导入配置项
    private fun importConfigurationsFromJson() {
        val jsonInput = Messages.showMultilineInputDialog(
            null,
            "Paste the JSON here:",
            "Import Configurations",
            "",
            null,
            null
        )
        if (jsonInput != null) {
            try {
                val importedSettings: List<LLMSetting> = Gson().fromJson(
                    jsonInput,
                    object : com.google.gson.reflect.TypeToken<List<LLMSetting>>() {}.type
                )
                listModel.clear()
                importedSettings.forEach { setting ->
                    listModel.addElement(setting)
                }
                Messages.showInfoMessage("Configurations imported successfully.", "Import Successful")
            } catch (ex: Exception) {
                Messages.showErrorDialog("Failed to import configurations: ${ex.message}", "Import Error")
            }
        }
    }

    // 复制选中的配置项
    private fun copySelectedConfiguration() {
        val selectedSetting = settingsList.selectedValue
        if (selectedSetting != null) {
            val copiedSetting = selectedSetting.copy() // 假设 ShireSetting 是 data class
            // 默认属性不复制
            listModel.addElement(copiedSetting)
            settingsList.selectedIndex = settingsList.model.size - 1
        } else {
            Messages.showWarningDialog("No configuration selected to copy.", "Copy Failed")
        }
    }

    // 显示配置项编辑对话框
    private fun showShireSettingDialog(setting: LLMSetting? = null): LLMSetting? {
        val nameField = JTextField(20).apply { text = setting?.name ?: "" }
        val modelNameField = JTextField(20).apply { text = setting?.modelName ?: "" }

        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel("Name:"))
            add(nameField)
            add(Box.createVerticalStrut(10))
            add(JLabel("Model Name:"))
            add(modelNameField)
            add(Box.createVerticalStrut(10))
            // 添加更多字段的标签和输入框
            // 例如 provider 选择框等，可以根据需要扩展
        }

        val result = JOptionPane.showConfirmDialog(
            null,
            panel,
            if (setting == null) "Add Configuration" else "Edit Configuration",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )

        return if (result == JOptionPane.OK_OPTION) {
            LLMSetting(
                name = nameField.text.trim(),
                modelName = modelNameField.text.trim(),
                // 初始化其他字段为默认值，用户需要在详细面板中进行编辑
            )
        } else {
            null
        }
    }

    // 更新右侧详细面板
    private fun updateDetailPanel(setting: LLMSetting?) {
        detailPanel.removeAll()
        if (setting != null) {
            // 查找现有的配置项组件或创建新的
            val configComponent = ShireConfigItemComponent(setting, this)
            selectedConfiguration = configComponent
            detailPanel.add(configComponent.getComponent())
        } else {
            val noSelectionLabel = JLabel("No configuration selected", SwingConstants.CENTER)
            noSelectionLabel.preferredSize = Dimension(200, 50)
            detailPanel.add(noSelectionLabel)
        }
        detailPanel.revalidate()
        detailPanel.repaint()
    }

    // 更新下拉框选项的方法
    private fun updateComboBoxes() {
        val st = LLMSettingsState.getInstance()
        
        defaultModelComboBox.removeAllItems()
        formatCopyModelComboBox.removeAllItems()
        
        // 添加空选项
        defaultModelComboBox.addItem(null)
        formatCopyModelComboBox.addItem(null)
        
        // 添加所有配置项
        for (i in 0 until listModel.size) {
            val setting = listModel.getElementAt(i)
            defaultModelComboBox.addItem(setting)
            formatCopyModelComboBox.addItem(setting)
        }

        // 选中已保存的选项
        val defaultSetting = listModel.elements().toList().find { it.name == st.defaultModelName }
        val formatCopySetting = listModel.elements().toList().find { it.name == st.formatCopyModelName }
        
        defaultModelComboBox.selectedItem = defaultSetting
        formatCopyModelComboBox.selectedItem = formatCopySetting
    }
}

class ShireConfigItemComponent(
    var setting: LLMSetting,
    LLMSettingUi: LLMSettingUi,
) {
    private val panel: JPanel
    private val nameField = JBTextField(setting.name)
    private val providerComboBox = JComboBox(Provider.values()).apply {
        minimumSize = Dimension(130, minimumSize.height)
        preferredSize = Dimension(130, preferredSize.height)
    }

    // 独有字段
    private val apiHostField = JBTextField(setting.apiHost)
    private val modelNameField = JBTextField(setting.modelName)
    private val apiTokenField = JBPasswordField() // 使用密码字段更安全

    // Azure 特有字段
    private val azureEndpointField = JBTextField(setting.azureEndpoint)
    private val azureApiKeyField = JBPasswordField()
    private val azureModelField = JBTextField(setting.azureModel)

    // 公共字段
    private val temperatureField = JBTextField(setting.temperature.toString())
    private val streamCheckBox = JBCheckBox("stream").apply {
        isSelected = setting.stream
    }
    // Test Connection 相关组件
    private val testConnectionButton = JButton("Test Connection")
    private val testResultField = JTextPane()
    private var testJob: Job? = null

    // 使用 CardLayout 管理独有字段的显示
    private val uniqueFieldsPanel = JPanel(CardLayout())

    init {
        // 创建 OpenAI 独有字段面板
        val openAIPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JLabel("API Host:"), apiHostField)
            .addLabeledComponent(JLabel("Model Name:"), modelNameField)
            .addLabeledComponent(JLabel("API Token:"), apiTokenField)
            .panel

        // 创建 Azure 独有字段面板
        val azurePanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JLabel("Azure Endpoint:"), azureEndpointField)
            .addLabeledComponent(JLabel("Azure Model:"), azureModelField)
            .addLabeledComponent(JLabel("Azure API Key:"), azureApiKeyField)
            .panel

        // 创建一个空的面板用于其他 Provider（如 Azure）
        val emptyPanel = JPanel()

        // 将独有字段面板添加到 uniqueFieldsPanel 中
        uniqueFieldsPanel.layout = CardLayout()
        uniqueFieldsPanel.add(openAIPanel, Provider.OpenAILike.name)
        uniqueFieldsPanel.add(azurePanel, Provider.Azure.name) // 添加 Azure 面板
        uniqueFieldsPanel.add(emptyPanel, "Other") // 其他 Provider 的面板（可选）

        // 构建主面板
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JLabel("Name:"), nameField)
            .addLabeledComponent(JLabel("Provider:"), providerComboBox)
            .addComponent(uniqueFieldsPanel)
            .addLabeledComponent(JLabel("Temperature:"), temperatureField)
            .addComponent(streamCheckBox)
            // 添加 Test Connection 按钮和结果显示
            .addComponent(testConnectionButton)
            .addComponent(testResultField)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // 初始化 provider 选择框
        providerComboBox.selectedItem = setting.provider
        showProviderFields(setting.provider)

        // 监听 provider 选择变化
        providerComboBox.addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                val selectedProvider = event.item as Provider
                showProviderFields(selectedProvider)
                setting.provider = selectedProvider
            }
        }

        // 设置各字段的监听器
        setDocumentListeners()

        // 初始化字段值

        // 初始化字段值
        reset(setting)

        // 添加 Test Connection 按钮的 ActionListener
        testConnectionButton.addActionListener {
            onTestConnection()
        }
    }

    /**
     * 根据选择的 provider 显示对应的独有字段面板
     */
    private fun showProviderFields(provider: Provider) {
        val cl = uniqueFieldsPanel.layout as CardLayout
        cl.show(uniqueFieldsPanel, provider.name)
    }

    /**
     * 为各字段设置文档监听器，实时更新 setting 对象
     */
    private fun setDocumentListeners() {
        // name 字段监听器
        nameField.document.addDocumentListener(createDocumentListener { setting.name = nameField.text })
        // 独有字段监听器
        apiHostField.document.addDocumentListener(createDocumentListener { setting.apiHost = apiHostField.text })
        modelNameField.document.addDocumentListener(createDocumentListener { setting.modelName = modelNameField.text })
        apiTokenField.document.addDocumentListener(createDocumentListener { setting.apiToken = apiTokenField.text })

        // azure 字段监听
        azureEndpointField.document.addDocumentListener(createDocumentListener { setting.azureEndpoint = azureEndpointField.text })
        azureApiKeyField.document.addDocumentListener(createDocumentListener { setting.azureApiKey = azureApiKeyField.text })
        azureModelField.document.addDocumentListener(createDocumentListener { setting.azureModel = azureModelField.text })

        // 公共字段监听器
        temperatureField.document.addDocumentListener(createDocumentListener {
            setting.temperature = temperatureField.text.toDoubleOrNull() ?: 0.0
        })
        streamCheckBox.addItemListener { event ->
            setting.stream = event.stateChange == ItemEvent.SELECTED
        }
    }

    /**
     * 创建文档监听器
     */
    private fun createDocumentListener(onUpdate: () -> Unit): DocumentListener {
        return object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onUpdate()
            override fun removeUpdate(e: DocumentEvent?) = onUpdate()
            override fun changedUpdate(e: DocumentEvent?) = onUpdate()
        }
    }

    /**
     * 获取当前组件面板
     */
    fun getComponent(): JPanel = panel

    /**
     * 应用当前配置到 setting 对象
     */
    fun apply(): LLMSetting {
        setting.provider = providerComboBox.selectedItem as Provider

        // 根据 provider 设置独有字段
        when (setting.provider) {
            Provider.OpenAILike -> {
                setting.apiHost = apiHostField.text
                setting.modelName = modelNameField.text
                setting.apiToken = apiTokenField.text
            }
            Provider.Azure -> {
                setting.azureEndpoint = azureEndpointField.text
                setting.azureApiKey = azureApiKeyField.text
                setting.azureModel = azureModelField.text
            }
            else -> {
                // 处理其他 Provider 的字段
            }
        }

        // 设置公共字段
        setting.temperature = temperatureField.text.toDoubleOrNull() ?: 0.0
        setting.stream = streamCheckBox.isSelected

        return setting
    }

    /**
     * 重置字段值为指定的 setting 对象
     */
    fun reset(setting: LLMSetting) {
        nameField.text = setting.name
        providerComboBox.selectedItem = setting.provider
        showProviderFields(setting.provider)

        // 设置独有字段值
        when (setting.provider) {
            Provider.OpenAILike -> {
                apiHostField.text = setting.apiHost
                modelNameField.text = setting.modelName
                apiTokenField.text = setting.apiToken
            }
            Provider.Azure -> {
                azureEndpointField.text = setting.azureEndpoint
                azureApiKeyField.text = setting.azureApiKey
                azureModelField.text = setting.azureModel
            }
            else -> {
                // 处理其他 Provider 的字段
            }
        }

        // 设置公共字段
        temperatureField.text = setting.temperature.toString()
        streamCheckBox.isSelected = setting.stream

        // 重置 Test Connection 结果
        testResultField.text = ""
    }

    /**
     * 执行连接测试
     */
    private fun onTestConnection() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: run {
            testResultField.text = "No open project found."
            return
        }

        // 取消上一个测试任务（如果有）
        testJob?.cancel()
        testResultField.text = "Testing connection..."

        // 使用 CoroutineExceptionHandler 处理异常，自动忽略 CancelledException
        testJob = LLMCoroutineScope.scope(project).launch(CoroutineExceptionHandler { _, throwable ->
            testResultField.text = throwable.message ?: "Unknown error"
        }) {
            val llmConfig = LlmConfig(
                title = setting.modelName,
                provider = setting.provider,
                apiKey = if (setting.provider == Provider.OpenAILike) setting.apiToken else setting.azureApiKey,
                model = if (setting.provider == Provider.OpenAILike) setting.modelName else setting.azureModel,
                temperature = setting.temperature,
                apiBase = if (setting.provider == Provider.OpenAILike) setting.apiHost else setting.azureEndpoint,
                stream = setting.stream,  // 改用 stream
                azureEndpoint = setting.azureEndpoint,
                azureApiKey = setting.azureApiKey,
                azureModel = setting.azureModel
            )

            val flowString: Flow<String> =
                LlmProvider.stream(
                    mutableListOf(ChatMessage(ChatRole.user, content = "hi")),
                    llmConfig
                )
            flowString.collect {
                // 更新 UI 需要在 EDT 线程中进行
                SwingUtilities.invokeLater {
                    testResultField.text += it
                }
            }
        }
    }
}
