package putl.ideaplugle.ui.popup

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.awt.RelativePoint
import java.awt.Point

/**
 * 变量名选择弹窗
 */
class NameSelectionPopup(
    private val names: List<String>,
    private val onNameSelected: (String) -> Unit
) {

    private val popup: ListPopup

    init {
        val listStep = object : BaseListPopupStep<String>("选择变量名", names) {
            override fun getTextFor(value: String): String = value

            override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                onNameSelected(selectedValue)
                return FINAL_CHOICE
            }

            override fun isAutoSelectionEnabled(): Boolean = false
            override fun isSpeedSearchEnabled(): Boolean = true

            override fun getIndexedString(value: String?): String = value ?: ""
        }

        popup = JBPopupFactory.getInstance().createListPopup(listStep)
    }

    fun show(location: RelativePoint) {
        popup.show(location)
    }

    fun showInBestPositionFor(editor: com.intellij.openapi.editor.Editor) {
        popup.showInBestPositionFor(editor)
    }
}
