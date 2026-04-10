package putl.ideaplugle.psi

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.PsiVariable
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

/**
 * 从当前光标位置提取 Java PSI 上下文，供命名 prompt 使用。
 */
object JavaNamingContextExtractor {

    fun describe(project: Project, editor: Editor): String {
        return ReadAction.compute<String, RuntimeException> {
            val documentManager = PsiDocumentManager.getInstance(project)
            documentManager.commitDocument(editor.document)
            val psiFile = documentManager.getPsiFile(editor.document)

            if (psiFile == null || !psiFile.language.isKindOf("JAVA")) {
                return@compute defaultContext()
            }

            val element = findRelevantElement(psiFile, editor.caretModel.offset)
            val psiClass = element?.let { PsiTreeUtil.getParentOfType(it, PsiClass::class.java, false) }
            val psiMethod = element?.let { PsiTreeUtil.getParentOfType(it, PsiMethod::class.java, false) }
            val psiVariable = element?.let { PsiTreeUtil.getParentOfType(it, PsiVariable::class.java, false) }
            val psiTypeElement = element?.let { PsiTreeUtil.getParentOfType(it, PsiTypeElement::class.java, false) }

            formatContext(
                className = psiClass?.name,
                methodName = psiMethod?.name,
                variableType = psiVariable?.type?.presentableText ?: psiTypeElement?.type?.presentableText
            )
        }
    }

    private fun findRelevantElement(
        psiFile: com.intellij.psi.PsiFile,
        caretOffset: Int
    ): com.intellij.psi.PsiElement? {
        if (psiFile.textLength == 0) {
            return null
        }

        val offsets = listOf(
            caretOffset.coerceIn(0, psiFile.textLength - 1),
            (caretOffset - 1).coerceIn(0, psiFile.textLength - 1),
            (caretOffset + 1).coerceIn(0, psiFile.textLength - 1)
        ).distinct()

        val elements = offsets.mapNotNull(psiFile::findElementAt)
        return elements.firstOrNull(::isMeaningfulElement) ?: elements.firstOrNull()
    }

    private fun isMeaningfulElement(element: com.intellij.psi.PsiElement): Boolean {
        return element !is PsiWhiteSpace && element !is PsiComment
    }

    private fun formatContext(
        className: String?,
        methodName: String?,
        variableType: String?
    ): String {
        return """
            - 所在类名: ${className ?: UNKNOWN_VALUE}
            - 所在方法: ${methodName ?: UNKNOWN_VALUE}
            - 正在定义的变量类型: ${variableType ?: UNKNOWN_VALUE}
        """.trimIndent()
    }

    private fun defaultContext(): String = formatContext(null, null, null)

    private const val UNKNOWN_VALUE = "未识别"
}
