package com.github.zjh7890.gpttools.settings.actionPrompt

import com.github.zjh7890.gpttools.components.JsonLanguageField
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

val Project.customAgentSetting: CodeTemplateProjectSetting
    get() = service<CodeTemplateProjectSetting>()

class PluginConfigurable(private val project: Project) : Configurable {
    private var myComponent: MyConfigurableComponent? = null
    private val appSettings: CodeTemplateApplicationSettingsService
        get() = CodeTemplateApplicationSettingsService.getInstance()
    private val projectSettings: CodeTemplateProjectSetting
        get() = CodeTemplateProjectSetting.getInstance(project)

    override fun createComponent(): JComponent? {
        myComponent = MyConfigurableComponent(project, appSettings.state, projectSettings.state)
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
    project: Project,
    appSettingState: CodeTemplateApplicationSettings,
    private val settingsState: CodeTemplateProjectSettings
) {
    val panel = JTabbedPane()
    private val templateList = JBList<PromptTemplate>(DefaultListModel<PromptTemplate>())
    private val valueTextArea = JTextArea(5, 80)
    val jsonTextArea = JsonLanguageField(project, "", "Context", "projectTreeConfig.json")
    val keyTextField = JTextField()
    var selectedItem : PromptTemplate? = null

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
                valueTextArea.text = selectedTemplate?.value ?: ""
                keyTextField.text = selectedTemplate?.key ?: ""
                selectedItem = selectedTemplate
            }
        }

        valueTextArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateSettings()
            override fun removeUpdate(e: DocumentEvent) = updateSettings()
            override fun changedUpdate(e: DocumentEvent) = updateSettings()

            private fun updateSettings() {
                val selectedTemplate = templateList.selectedValue
                selectedTemplate.value = valueTextArea.text
            }
        })

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

        val openEditorButton = JButton("Open Editor").apply {
            addActionListener {
                if (selectedItem == null) {
                    return@addActionListener
                }
                val popup = EnhancedEditorDialog(project, selectedItem)
//                popup.setLocationRelativeTo(panel)
                popup.show()
            }
        }

        val keyPanel = JPanel(BorderLayout())
        val keyLabel = JLabel("Key:")
        keyTextField.preferredSize = Dimension(50, keyTextField.preferredSize.height)

        keyPanel.add(keyLabel, BorderLayout.WEST)
        keyPanel.add(keyTextField, BorderLayout.CENTER)
        keyPanel.add(openEditorButton, BorderLayout.EAST)

        val templatePanel = JPanel(BorderLayout())
        templatePanel.add(optionsDecorator.createPanel(), BorderLayout.CENTER)

        val textScrollPane = JScrollPane(valueTextArea)
        val combinedPanel = JPanel(BorderLayout())
        combinedPanel.add(keyPanel, BorderLayout.NORTH)

//        combinedPanel.add(, BorderLayout.CENTER)
        combinedPanel.add(textScrollPane, BorderLayout.CENTER)

        templatePanel.add(combinedPanel, BorderLayout.EAST)
        val jsonPanel = JPanel(BorderLayout())
        jsonPanel.add(JScrollPane(jsonTextArea), BorderLayout.CENTER)

        panel.addTab("Options", templatePanel)
        panel.addTab("JSON Data", jsonPanel)
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
        val valueTextArea = JTextArea(5, 20).apply { text = template?.value ?: ""; lineWrap = true; wrapStyleWord = true }
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
            add(JLabel("Value:"), gbc)
            add(valueTextArea, gbc.clone().apply { })
            add(JLabel("Description:"), gbc)
            add(descField, gbc.clone().apply {  })
        }

        val result = JOptionPane.showConfirmDialog(panel, panel, "Edit Prompt Template", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        if (result == JOptionPane.OK_OPTION) {
            return PromptTemplate(keyField.text, valueTextArea.text, descField.text)
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
        SwingUtilities.invokeLater {
            val model = templateList.model as DefaultListModel<PromptTemplate>
            model.clear()
            appSetting.templates.forEach(model::addElement)

            val firstTemplate = appSetting.templates.firstOrNull()
            keyTextField.text = firstTemplate?.key ?: ""
            jsonTextArea.text = settings.jsonData ?: "{}"

        }
    }

    fun applyTo(
        appSetting: CodeTemplateApplicationSettings,
        settings: CodeTemplateProjectSettings
    ) {
        SwingUtilities.invokeLater {
            val model = templateList.model as DefaultListModel<PromptTemplate>
            val newTemplatesList = model.elements().toList()

            // 替换旧的列表与新的列表
            appSetting.templates = newTemplatesList

            // 更新JSON数据
            settings.jsonData = jsonTextArea.text
        }
    }
}
