package com.github.zjh7890.gpttools.settings.template

import com.fasterxml.jackson.core.type.TypeReference
import com.github.zjh7890.gpttools.components.LeftRightComponent
import com.github.zjh7890.gpttools.settings.GptToolsConfigurable
import com.github.zjh7890.gpttools.utils.ClipboardUtils
import com.github.zjh7890.gpttools.utils.JsonUtils
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.FormBuilder
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class TemplateSettingUi(
    val templateSettings: CodeTemplateApplicationSettingsService,
    val gptToolsConfigurable: GptToolsConfigurable
) {
    val panel = JPanel(BorderLayout())
    private val templateList = JBList<PromptTemplate>(DefaultListModel<PromptTemplate>())
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

        templateList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val selectedTemplate = templateList.selectedValue
                updateCombinedPanelDisplay(selectedTemplate)
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

        updateCombinedPanelDisplay(null)

        val templatePanel = LeftRightComponent(templatesDecorator.createPanel(), combinedPanel).mainPanel
        panel.add(templatePanel, BorderLayout.CENTER)


        // 在 templatePanel 创建后添加
        val linkPanel = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
            add(JLabel("Help Documentation").apply {
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        Desktop.getDesktop().browse(URI("https://gpt-tools.yuque.com/pfm3um/doc/ba0sbxf3gc0bn5f6"))
                    }
                })
            })
            add(JLabel(AllIcons.Ide.External_link_arrow))
        }

        panel.add(linkPanel, BorderLayout.NORTH)
    }

    private fun updateCombinedPanelDisplay(selectedItem: PromptTemplate?) {
        combinedPanel.removeAll()  // 清除所有当前组件
        if (selectedItem != null) {
            val descTextField = JTextField()
            val valueTextArea = JTextArea(5, 80)
            val input1 = JTextField()
            val input2 = JTextField()
            val input3 = JTextField()
            val input4 = JTextField()
            val input5 = JTextField()

            val formBuilder: FormBuilder = FormBuilder.createFormBuilder()

            val showInEditorPopupMenuCheckBox = JCheckBox("Show in Editor Popup Menu", selectedItem?.showInEditorPopupMenu ?: true)
            val showInFloatingToolBarCheckBox = JCheckBox("Show in Floating Toolbar", selectedItem?.showInFloatingToolBar ?: true)
            val newChatCheckBox = JCheckBox("Start a new chat", selectedItem?.newChat ?: true)

            showInEditorPopupMenuCheckBox.addActionListener {
                selectedItem?.showInEditorPopupMenu = showInEditorPopupMenuCheckBox.isSelected
            }

            showInFloatingToolBarCheckBox.addActionListener {
                selectedItem?.showInFloatingToolBar = showInFloatingToolBarCheckBox.isSelected
            }

            newChatCheckBox.addActionListener {
                selectedItem?.newChat = newChatCheckBox.isSelected
            }

            val formPane = formBuilder
                .addLabeledComponent("Desc:", descTextField)
                .addComponent(showInEditorPopupMenuCheckBox)
                .addComponent(showInFloatingToolBarCheckBox)
                .addComponent(newChatCheckBox)
                .addSeparator()
                .addComponent(
                    JButton("Open Editor").apply {
                        addActionListener {
                            val popup = EnhancedEditorDialog(selectedItem)
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

    private fun exportTemplatesToJson() {
        val model = templateList.model as DefaultListModel<PromptTemplate>
        val newTemplatesList = model.elements().toList()

        val templateList1 = JsonUtils.parse(JsonUtils.toJson(newTemplatesList), object : TypeReference<List<PromptTemplate>>() {})
        ClipboardUtils.copyToClipboard(JsonUtils.toJson(templateList1))

        Messages.showInfoMessage("Options exported to clipboard.", "Export Successful")
    }

    private fun importTemplatesFromJson() {
        val jsonInput = Messages.showMultilineInputDialog(
            null,
            "Paste the JSON here:",
            "Import Options",
            "",
            null,
            null
        )
        if (jsonInput != null) {
            try {
                val type = object : TypeToken<List<PromptTemplate>>() {}.type
                val importedTemplates = Gson().fromJson<List<PromptTemplate>>(jsonInput, type)

                templateSettings.state.templates = jsonInput

                ApplicationManager.getApplication().invokeLater {
                    gptToolsConfigurable.reset()
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
        val descField = JTextField(10).apply { text = template?.desc ?: "" }

        val panel = JPanel(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = GridBagConstraints.RELATIVE
                anchor = GridBagConstraints.WEST
                insets = Insets(4, 4, 4, 4)
            }
            add(JLabel("Description:"), gbc)
            add(descField, gbc.clone().apply {  })
        }

        val result = JOptionPane.showConfirmDialog(panel, panel, "Edit Prompt Template", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        if (result == JOptionPane.OK_OPTION) {
            return PromptTemplate(desc = descField.text)
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

    fun isModified(appSetting: CodeTemplateApplicationSettings): Boolean {
        val model = templateList.model as DefaultListModel<PromptTemplate>
        val listOptions = model.elements().asSequence().toList()

        // Parse the original templates from the app settings
        val templates = JsonUtils.parse(appSetting.templates, object : TypeReference<List<PromptTemplate>>() {})

        // Check if the number of templates has been modified
        if (listOptions.size != templates.size) {
            return true
        }

        // Check for changes in each PromptTemplate
        for (i in listOptions.indices) {
            val uiTemplate = listOptions[i]
            val originalTemplate = templates.getOrNull(i)

            if (originalTemplate == null ||
                uiTemplate.value != originalTemplate.value ||
                uiTemplate.desc != originalTemplate.desc ||
                uiTemplate.input1 != originalTemplate.input1 ||
                uiTemplate.input2 != originalTemplate.input2 ||
                uiTemplate.input3 != originalTemplate.input3 ||
                uiTemplate.input4 != originalTemplate.input4 ||
                uiTemplate.input5 != originalTemplate.input5 ||
                uiTemplate.showInEditorPopupMenu != originalTemplate.showInEditorPopupMenu ||
                uiTemplate.showInFloatingToolBar != originalTemplate.showInFloatingToolBar ||
                uiTemplate.newChat != originalTemplate.newChat) {
                return true
            }
        }

        return false
    }

    fun resetFrom(appSetting: CodeTemplateApplicationSettings) {
        val model = templateList.model as DefaultListModel<PromptTemplate>
        model.clear()
        JsonUtils.parse(appSetting.templates, object : TypeReference<List<PromptTemplate>>() {})
            .forEach(model::addElement)
    }

    fun applyTo(appSetting: CodeTemplateApplicationSettings) {
        val model = templateList.model as DefaultListModel<PromptTemplate>
        val newTemplatesList = model.elements().toList()

        val templateList1 = JsonUtils.parse(JsonUtils.toJson(newTemplatesList), object : TypeReference<List<PromptTemplate>>() {})

        // 替换旧的列表与新的列表
        appSetting.templates = JsonUtils.toJson(templateList1)
    }
}