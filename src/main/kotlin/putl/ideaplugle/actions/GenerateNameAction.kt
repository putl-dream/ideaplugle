package putl.ideaplugle.actions

import putl.ideaplugle.editor.EditorTextInserter
import putl.ideaplugle.ui.popup.QuickNamePopup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

/**
 * 在编辑器中唤起快速命名输入框。
 */
class GenerateNameAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = project?.let(EditorTextInserter::selectedEditor) ?: e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = project != null && editor != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: EditorTextInserter.selectedEditor(project) ?: return
        QuickNamePopup(project, editor).show()
    }
}
