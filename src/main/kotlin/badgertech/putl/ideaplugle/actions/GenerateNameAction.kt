package badgertech.putl.ideaplugle.actions

import badgertech.putl.ideaplugle.ai.NamingAIService
import badgertech.putl.ideaplugle.naming.NamingFormat
import badgertech.putl.ideaplugle.settings.NamingPluginSettings
import badgertech.putl.ideaplugle.ui.popup.NameSelectionPopup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.JBColor
import java.awt.Component
import java.awt.Point
import javax.swing.JLabel

/**
 * 在编辑器中生成变量名的Action
 */
class GenerateNameAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val selectionModel = editor?.selectionModel
        e.presentation.isEnabledAndVisible = selectionModel != null && selectionModel.hasSelection()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText ?: return

        val settings = project.service<NamingPluginSettings>()
        val aiService = NamingAIService(project)

        // 显示加载中
        val loadingLabel = JLabel("正在生成变量名...").apply {
            foreground = JBColor.BLUE
        }
        val loadingPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(loadingLabel, null)
            .setTitle("AI起名助手")
            .createPopup()
            .also {
                it.showInBestPositionFor(editor)
            }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "正在生成变量名...", true) {
            override fun run(indicator: ProgressIndicator) {
                aiService.generateNames(
                    description = selectedText,
                    format = settings.pluginState.defaultFormat,
                    onResult = { names ->
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            loadingPopup?.cancel()
                            if (names.isNotEmpty()) {
                                showNameSelectionPopup(editor.contentComponent, names)
                            }
                        }
                    },
                    onError = { error ->
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            loadingPopup?.cancel()
                            showErrorMessage(editor.contentComponent, error)
                        }
                    }
                )
            }
        })
    }

    private fun showNameSelectionPopup(component: Component, names: List<String>) {
        val popup = NameSelectionPopup(names) { selectedName ->
            copyToClipboard(selectedName)
        }

        val point = getBestPopupLocation(component)
        popup.show(RelativePoint.fromScreen(point))
    }

    private fun getBestPopupLocation(component: Component): Point {
        val locationOnScreen = component.locationOnScreen
        return Point(locationOnScreen.x + 100, locationOnScreen.y + 100)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val stringSelection = java.awt.datatransfer.StringSelection(text)
        clipboard.setContents(stringSelection, null)
    }

    private fun showErrorMessage(component: Component, message: String) {
        JBPopupFactory.getInstance()
            .createMessage(message)
            .show(RelativePoint.fromScreen(getBestPopupLocation(component)))
    }
}
