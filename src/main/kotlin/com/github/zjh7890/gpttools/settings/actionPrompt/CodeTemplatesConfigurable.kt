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
    private val appSettings: CodeTemplateApplicationSettingsService
        get() = CodeTemplateApplicationSettingsService.getInstance()
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

        val optionsDecorator = ToolbarDecorator.createDecorator(templateList)
            .setAddAction { addElement() }
            .setRemoveAction { removeSelectedElement() }
            .setMoveUpAction { moveElementUp() }
            .setMoveDownAction { moveElementDown() }
            .setEditAction { editSelectedElement() }
            .addExtraAction(object : AnAction("Export to Clipboard", "Export options to JSON string and copy to clipboard", AllIcons.ToolbarDecorator.Export) {
                override fun actionPerformed(e: AnActionEvent) {
                    exportOptionsToJson()
                }
            })
            .addExtraAction(object : AnAction("Import from Clipboard", "Import options from JSON string provided by user", AllIcons.ToolbarDecorator.Import) {
                override fun actionPerformed(e: AnActionEvent) {
                    importOptionsFromJson()
                }
            })

        updateCombinedPanelDisplay(project, null)
        val jsonPanel = JPanel(BorderLayout())
        jsonPanel.add(JScrollPane(jsonTextArea), BorderLayout.CENTER)

        val templatePanel = LeftRightComponent(optionsDecorator.createPanel(), combinedPanel).mainPanel

        panel.addTab("Options", templatePanel)
        panel.addTab("JSON Data", jsonPanel)
    }

    private fun updateCombinedPanelDisplay(project: Project, selectedItem: PromptTemplate?) {
        combinedPanel.removeAll()  // 清除所有当前组件
        if (selectedItem != null) {
            val keyTextField = JTextField()
            val descTextField = JTextField()
            val valueTextArea = JTextArea(5, 80)

            val formBuilder: FormBuilder = FormBuilder.createFormBuilder()
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
                .addComponentFillVertically(JPanel(), 0)
                .panel



            valueTextArea.text = selectedItem.value ?: ""
            keyTextField.text = selectedItem.key ?: ""
            descTextField.text = selectedItem.desc ?: ""
            keyTextField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = updateSelectedItem()
                override fun removeUpdate(e: DocumentEvent) = updateSelectedItem()
                override fun changedUpdate(e: DocumentEvent) = updateSelectedItem()

                private fun updateSelectedItem() {
                    selectedItem.key = keyTextField.text
                }
            })

            valueTextArea.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = updateSettings()
                override fun removeUpdate(e: DocumentEvent) = updateSettings()
                override fun changedUpdate(e: DocumentEvent) = updateSettings()

                private fun updateSettings() {
                    selectedItem.value = valueTextArea.text
                }
            })
            descTextField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = updateSettings()
                override fun removeUpdate(e: DocumentEvent) = updateSettings()
                override fun changedUpdate(e: DocumentEvent) = updateSettings()

                private fun updateSettings() {
                    selectedItem.desc = descTextField.text
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
    private fun exportOptionsToJson() {
        val model = templateList.model as DefaultListModel<PromptTemplate>
        val templates = List(model.size()) { model.getElementAt(it) }
        val json = Gson().toJson(templates)
        val stringSelection = StringSelection(json)
        CopyPasteManager.getInstance().setContents(stringSelection)
        Messages.showInfoMessage("Options exported to clipboard.", "Export Successful")
    }

    // 从剪切板导入模型
    private fun importOptionsFromJson() {
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
                (templateList.model as DefaultListModel<PromptTemplate>).remove(selectedIndex)
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

    private fun editSelectedElement() {
        SwingUtilities.invokeLater {
            val selectedIndex = templateList.selectedIndex
            if (selectedIndex != -1) {
                val selectedTemplate = (templateList.model as DefaultListModel<PromptTemplate>).getElementAt(selectedIndex)
                val updatedTemplate = showPromptTemplateDialog(selectedTemplate)
                updatedTemplate?.let {
                    (templateList.model as DefaultListModel<PromptTemplate>).set(selectedIndex, it) // Update display with the updated template
                }
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
        if (listOptions.size != appSetting.templates.size) {
            return true
        }

        // Check for changes in each PromptTemplate
        for (i in listOptions.indices) {
            val uiTemplate = listOptions[i]
            val originalTemplate = appSetting.templates.getOrNull(i)

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
        JsonUtils.parse(JsonUtils.toJson(appSetting.templates), object : TypeReference<List<PromptTemplate>>() {})
            .forEach(model::addElement)
        jsonTextArea.text = settings.jsonData ?: "{}"
    }

    fun applyTo(
        appSetting: CodeTemplateApplicationSettings,
        settings: CodeTemplateProjectSettings
    ) {
        SwingUtilities.invokeLater {
            val model = templateList.model as DefaultListModel<PromptTemplate>
            val newTemplatesList = model.elements().toList()

            val templateList1 = JsonUtils.parse(JsonUtils.toJson(newTemplatesList), object : TypeReference<List<PromptTemplate>>() {})

            // 替换旧的列表与新的列表
            appSetting.templates = templateList1

            // 更新JSON数据
            settings.jsonData = jsonTextArea.text
        }
    }
}
