package com.github.zjh7890.gpttools.settings.actionPrompt

import com.fasterxml.jackson.core.type.TypeReference
import com.github.zjh7890.gpttools.components.JsonLanguageField
import com.github.zjh7890.gpttools.components.LeftRightComponent
import com.github.zjh7890.gpttools.utils.JsonUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.FormBuilder
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class PluginConfigurable(private val project: Project) : Configurable {
    private var myComponent: MyConfigurableComponent? = null
    private val appSettings: CodeTemplateApplicationSettingsService = CodeTemplateApplicationSettingsService.instance
    private val projectSettings: CodeTemplateProjectSetting
        get() = CodeTemplateProjectSetting.getInstance(project)

    override fun createComponent(): JComponent? {
        myComponent = MyConfigurableComponent(project)
        return myComponent?.panel
    }

    override fun isModified(): Boolean = myComponent?.isModified(appSettings.state, projectSettings.state) ?: false
    override fun apply(): Unit = myComponent!!.applyTo(appSettings.state, projectSettings.state)
    override fun reset(): Unit = myComponent!!.resetFrom(appSettings.state, projectSettings.state)
    override fun getDisplayName(): String = "GPT Tools Configuration"
    override fun disposeUIResources() {
        myComponent = null
    }
}

class MyConfigurableComponent(
    project: Project
) {
    val panel = JTabbedPane()
    private val templateList = JBList<PromptTemplate>(DefaultListModel<PromptTemplate>())
    val jsonTextArea = JsonLanguageField(project, "", "Context", "projectTreeConfig.json")
//    var selectedItem : PromptTemplate? = null
    private val combinedPanel = JPanel(BorderLayout())

    init {
        templateList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is PromptTemplate) {
                    text = value.desc  // Set text to description
                }
                return this
            }
        }

//        selected = selectedTemplate
        templateList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val selectedTemplate = templateList.selectedValue
                updateCombinedPanelDisplay(project, selectedTemplate)
            }
        }

        val templatesDecorator = ToolbarDecorator.createDecorator(templateList)
            .setAddAction { addElement() }
            .setRemoveAction { removeSelectedElement() }
            .setMoveUpAction { moveElementUp() }
            .setMoveDownAction { moveElementDown() }
            .addExtraAction(object : AnAction("Export to Clipboard", "Export templates to JSON string and copy to clipboard", AllIcons.ToolbarDecorator.Export) {
                override fun actionPerformed(e: AnActionEvent) {
                    exportTemplatesToJson()
                }
            })
            .addExtraAction(object : AnAction("Import from Clipboard", "Import templates from JSON string provided by user", AllIcons.ToolbarDecorator.Import) {
                override fun actionPerformed(e: AnActionEvent) {
                    importTemplatesFromJson()
                }
            })
            .addExtraAction(object : AnAction("Copy Configuration", "Copy selected configuration", AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) {
                    copyTemplates()
                }
            })

        updateCombinedPanelDisplay(project, null)
        val jsonPanel = JPanel(BorderLayout())
        jsonPanel.add(JScrollPane(jsonTextArea), BorderLayout.CENTER)

        val templatePanel = LeftRightComponent(templatesDecorator.createPanel(), combinedPanel).mainPanel

        panel.addTab("Options", templatePanel)
        panel.addTab("JSON Data", jsonPanel)
    }

    private fun updateCombinedPanelDisplay(project: Project, selectedItem: PromptTemplate?) {
        combinedPanel.removeAll()  // 清除所有当前组件
        if (selectedItem != null) {
            val keyTextField = JTextField()
            val descTextField = JTextField()
            val valueTextArea = JTextArea(5, 80)
            val input1 = JTextField()
            val input2 = JTextField()
            val input3 = JTextField()
            val input4 = JTextField()
            val input5 = JTextField()


            val formBuilder: FormBuilder = FormBuilder.createFormBuilder()
            // 如果 desc 以 * 开头，添加警告标签
            if (selectedItem.desc.startsWith("*")) {
                val warningLabel = JLabel("* 开头的模板修改将不生效，请复制模板后去掉 * 号再进行修改").apply {
//                    foreground = Color.ORANGE
                    isOpaque = true
                    background = Color(255, 255, 204)  // 浅黄色背景
                }
                formBuilder.addComponent(warningLabel)
            }

            val formPane = formBuilder
                .addLabeledComponent("Desc:", descTextField)
                .addLabeledComponent("Key:", keyTextField)
                .addSeparator()
                .addComponent(
                    JButton("Open Editor").apply {
                        addActionListener {
                            val popup = EnhancedEditorDialog(project, selectedItem)
//                popup.setLocationRelativeTo(panel)
                            popup.show()
                        }
                    }
                )
                .addComponent(JScrollPane(valueTextArea))
                .addLabeledComponent("Input1:", input1)
                .addLabeledComponent("Input2:", input2)
                .addLabeledComponent("Input3:", input3)
                .addLabeledComponent("Input4:", input4)
                .addLabeledComponent("Input5:", input5)
                .addComponentFillVertically(JPanel(), 0)
                .panel



            keyTextField.text = selectedItem.key ?: ""
            keyTextField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = updateSelectedItem()
                override fun removeUpdate(e: DocumentEvent) = updateSelectedItem()
                override fun changedUpdate(e: DocumentEvent) = updateSelectedItem()

                private fun updateSelectedItem() {
                    selectedItem.key = keyTextField.text
                }
            })

            valueTextArea.text = selectedItem.value ?: ""
            valueTextArea.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = updateSettings()
                override fun removeUpdate(e: DocumentEvent) = updateSettings()
                override fun changedUpdate(e: DocumentEvent) = updateSettings()

                private fun updateSettings() {
                    selectedItem.value = valueTextArea.text
                }
            })

            descTextField.text = selectedItem.desc ?: ""
            descTextField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = updateSettings()
                override fun removeUpdate(e: DocumentEvent) = updateSettings()
                override fun changedUpdate(e: DocumentEvent) = updateSettings()

                private fun updateSettings() {
                    selectedItem.desc = descTextField.text
                }
            })

            input1.text = selectedItem.input1 ?: ""
            input1.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = updateSettings()
                override fun removeUpdate(e: DocumentEvent) = updateSettings()
                override fun changedUpdate(e: DocumentEvent) = updateSettings()

                private fun updateSettings() {
                    selectedItem.input1 = input1.text
                }
            })

            input2.text = selectedItem.input2 ?: ""
            input2.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = updateSettings()
                override fun removeUpdate(e: DocumentEvent) = updateSettings()
                override fun changedUpdate(e: DocumentEvent) = updateSettings()

                private fun updateSettings() {
                    selectedItem.input2 = input2.text
                }
            })

            input3.text = selectedItem.input3 ?: ""
            input3.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = updateSettings()
                override fun removeUpdate(e: DocumentEvent) = updateSettings()
                override fun changedUpdate(e: DocumentEvent) = updateSettings()

                private fun updateSettings() {
                    selectedItem.input3 = input3.text
                }
            })

            input4.text = selectedItem.input4 ?: ""
            input4.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = updateSettings()
                override fun removeUpdate(e: DocumentEvent) = updateSettings()
                override fun changedUpdate(e: DocumentEvent) = updateSettings()

                private fun updateSettings() {
                    selectedItem.input4 = input4.text
                }
            })

            input5.text = selectedItem.input5 ?: ""
            input5.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = updateSettings()
                override fun removeUpdate(e: DocumentEvent) = updateSettings()
                override fun changedUpdate(e: DocumentEvent) = updateSettings()

                private fun updateSettings() {
                    selectedItem.input5 = input5.text
                }
            })

            combinedPanel.add(formPane, BorderLayout.CENTER)
        } else {
            val noSelectionLabel = JLabel("No template selected", SwingConstants.CENTER)
            noSelectionLabel.setPreferredSize(Dimension(200, 50))  // 设置合适的尺寸以便可见
            combinedPanel.add(noSelectionLabel, BorderLayout.CENTER)
        }
        combinedPanel.revalidate()
        combinedPanel.repaint()
    }

    // 导出模型到剪切板
    private fun exportTemplatesToJson() {
        val model = templateList.model as DefaultListModel<PromptTemplate>
        val templates = List(model.size()) { model.getElementAt(it) }
        val json = Gson().toJson(templates)
        val stringSelection = StringSelection(json)
        CopyPasteManager.getInstance().setContents(stringSelection)
        Messages.showInfoMessage("Options exported to clipboard.", "Export Successful")
    }

    // 从剪切板导入模型
    private fun importTemplatesFromJson() {
        val jsonInput = Messages.showInputDialog(
            "Paste the JSON here:",
            "Import Options",
            AllIcons.Actions.ShowImportStatements
        )
        if (jsonInput != null) {
            try {
                val type = object : TypeToken<List<PromptTemplate>>() {}.type
                val importedTemplates = Gson().fromJson<List<PromptTemplate>>(jsonInput, type)
                val model = templateList.model as DefaultListModel<PromptTemplate>
                model.removeAllElements()
                importedTemplates.forEach(model::addElement)
                if (model.size() > 0) {
                    templateList.selectedIndex = 0
                }
            } catch (ex: Exception) {
                Messages.showErrorDialog("Failed to import options: " + ex.message, "Import Error")
            }
        }
    }

    private fun copyTemplates() {
        val selectedTemplate = templateList.selectedValue
        if (selectedTemplate != null) {
            val newTemplate = PromptTemplate(
                key = selectedTemplate.key,
                desc = selectedTemplate.desc.trim(' ').trim('*').trim(' '),
                value = selectedTemplate.value,
                input1 = selectedTemplate.input1,
                input2 = selectedTemplate.input2,
                input3 = selectedTemplate.input3,
                input4 = selectedTemplate.input4,
                input5 = selectedTemplate.input5
            )
            (templateList.model as DefaultListModel<PromptTemplate>).addElement(newTemplate)
            templateList.selectedIndex = templateList.model.size - 1
        } else {
            Messages.showWarningDialog("No template selected to copy.", "Copy Failed")
        }
    }

    private fun addElement() {
        SwingUtilities.invokeLater {
            val newTemplate = showPromptTemplateDialog()
            if (newTemplate != null) {
                (templateList.model as DefaultListModel<PromptTemplate>).addElement(newTemplate)
            }
        }
    }

    private fun showPromptTemplateDialog(template: PromptTemplate? = null): PromptTemplate? {
        val keyField = JTextField(10).apply { text = template?.key ?: "" }
//        val valueTextArea = JTextArea(5, 20).apply { text = template?.value ?: ""; lineWrap = true; wrapStyleWord = true }
        val descField = JTextField(10).apply { text = template?.desc ?: "" }

        val panel = JPanel(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = GridBagConstraints.RELATIVE
                anchor = GridBagConstraints.WEST
                insets = Insets(4, 4, 4, 4)
            }
            add(JLabel("Key:"), gbc)
            add(keyField, gbc.clone().apply {  })
//            add(JLabel("Value:"), gbc)
//            add(valueTextArea, gbc.clone().apply { })
            add(JLabel("Description:"), gbc)
            add(descField, gbc.clone().apply {  })
        }

        val result = JOptionPane.showConfirmDialog(panel, panel, "Edit Prompt Template", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        if (result == JOptionPane.OK_OPTION) {
            return PromptTemplate(key = keyField.text, desc = descField.text)
        }
        return null
    }

    private fun removeSelectedElement() {
        SwingUtilities.invokeLater {
            val selectedIndex = templateList.selectedIndex
            if (selectedIndex != -1) {
                val model = templateList.model as DefaultListModel<PromptTemplate>
                model.remove(selectedIndex)

                // 计算新的选中索引
                val newIndex = if (selectedIndex >= model.size) {
                    model.size - 1
                } else {
                    selectedIndex
                }

                // 如果模型中还有元素，则设置新的选中索引
                if (newIndex >= 0 && newIndex < model.size) {
                    templateList.selectedIndex = newIndex
                }
            }
        }
    }

    private fun moveElementUp() {
        SwingUtilities.invokeLater {
            val selectedIndex = templateList.selectedIndex
            if (selectedIndex > 0) {
                val model = templateList.model as DefaultListModel<PromptTemplate>
                val element = model.remove(selectedIndex)
                model.add(selectedIndex - 1, element)
                templateList.selectedIndex = selectedIndex - 1
            }
        }
    }

    private fun moveElementDown() {
        SwingUtilities.invokeLater {
            val model = templateList.model as DefaultListModel<PromptTemplate>
            val selectedIndex = templateList.selectedIndex
            if (selectedIndex < model.size - 1) {
                val element = model.remove(selectedIndex)
                model.add(selectedIndex + 1, element)
                templateList.selectedIndex = selectedIndex + 1
            }
        }
    }

    fun isModified(
        appSetting: CodeTemplateApplicationSettings,
        settings: CodeTemplateProjectSettings
    ): Boolean {
        val model = templateList.model as DefaultListModel<PromptTemplate>
        val listOptions = model.elements().asSequence().toList()

        // Check if the number of options has been modified
        val templates = JsonUtils.parse(appSetting.templates, object : TypeReference<List<PromptTemplate>>() {})
        if (listOptions.size != templates.size) {
            return true
        }

        // Check for changes in each PromptTemplate
        for (i in listOptions.indices) {
            val uiTemplate = listOptions[i]
            val originalTemplate = templates.getOrNull(i)

            if (originalTemplate == null || uiTemplate.key != originalTemplate.key || uiTemplate.value != originalTemplate.value || uiTemplate.desc != originalTemplate.desc) {
                return true
            }
        }

        // Check if the JSON data in the settings has been modified
        val isJsonModified = jsonTextArea.text != settings.jsonData

        return isJsonModified
    }

    fun resetFrom(
        appSetting: CodeTemplateApplicationSettings,
        settings: CodeTemplateProjectSettings
    ) {
        val model = templateList.model as DefaultListModel<PromptTemplate>
        model.clear()
        JsonUtils.parse(appSetting.templates, object : TypeReference<List<PromptTemplate>>() {})
            .forEach(model::addElement)
        jsonTextArea.text = settings.jsonData ?: "{}"
    }

    fun applyTo(
        appSetting: CodeTemplateApplicationSettings,
        settings: CodeTemplateProjectSettings
    ) {
        val model = templateList.model as DefaultListModel<PromptTemplate>
        val newTemplatesList = model.elements().toList()

        val templateList1 = JsonUtils.parse(JsonUtils.toJson(newTemplatesList), object : TypeReference<List<PromptTemplate>>() {})

        // 替换旧的列表与新的列表
        appSetting.templates = JsonUtils.toJson(templateList1)

        // 更新JSON数据
        settings.jsonData = jsonTextArea.text
    }
}
