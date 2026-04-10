package putl.ideaplugle.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/**
 * 将生成结果写回当前编辑器，优先替换选中内容，否则直接插入到光标位置。
 */
object EditorTextInserter {

    fun selectedEditor(project: Project): Editor? {
        return FileEditorManager.getInstance(project).selectedTextEditor
    }

    fun insert(project: Project, editor: Editor, text: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val document = editor.document
            val selectionModel = editor.selectionModel

            if (selectionModel.hasSelection()) {
                val start = selectionModel.selectionStart
                val end = selectionModel.selectionEnd
                document.replaceString(start, end, text)
                selectionModel.removeSelection()
                editor.caretModel.moveToOffset(start + text.length)
            } else {
                val offset = editor.caretModel.offset
                document.insertString(offset, text)
                editor.caretModel.moveToOffset(offset + text.length)
            }

            editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
        }
    }
}
