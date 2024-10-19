import com.github.zjh7890.gpttools.toolWindow.chat.block.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import java.awt.Component
import javax.swing.*


/**

--- old_file.txt  timestamp
+++ new_file.txt  timestamp
@@ -start,count +start,count @@ (section header)
lines from old and new files, starting with:
' ' for context lines
'-' for lines removed from the old file
'+' for lines added to the new file

 */

class CodeChangeBlockView2(
    private val project: Project,
    val changes: List<CodeChangeFile>
) {
    private val panel = SimpleToolWindowPanel(true, true)
    private lateinit var changesListView: ChangesListView


    init {
        initialize()
    }

    fun getComponent(): Component? {
        return panel
    }

    fun initialize() {
        changesListView = ChangesListView(changes, project)
        panel.setContent(changesListView)
        setupToolbar()
    }

    private fun setupToolbar() {
        val actionGroup = DefaultActionGroup().apply {
            add(ShowChangeViewAction(project, changesListView.getChangesList()))
        }

        val toolbar: ActionToolbar = ActionManager.getInstance().createActionToolbar("CodeChangesToolbar", actionGroup, true)
        // 设置目标组件，通常是包含工具栏的主面板或者任何其他适当的组件
        toolbar.targetComponent = panel
        panel.setToolbar(toolbar.component)
    }
}