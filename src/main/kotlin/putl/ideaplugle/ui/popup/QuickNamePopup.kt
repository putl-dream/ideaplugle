package putl.ideaplugle.ui.popup

import putl.ideaplugle.ai.NamingAIService
import putl.ideaplugle.editor.EditorTextInserter
import putl.ideaplugle.naming.NamingFormat
import putl.ideaplugle.settings.NamingPluginSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.openapi.wm.WindowManager
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.KeyboardFocusManager
import java.awt.RenderingHints
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.AbstractAction
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JWindow
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Spotlight 风格的快速命名入口。
 */
class QuickNamePopup(
    private val project: Project,
    private val fallbackEditor: Editor
) {

    private val settings = NamingPluginSettings.getInstance(project)
    private val aiService = NamingAIService(project)
    private val resultModel = DefaultListModel<String>()
    private val resultList = JBList(resultModel).apply {
        isFocusable = false
        background = PANEL_COLOR
        selectionBackground = PANEL_COLOR
        selectionForeground = TEXT_COLOR
        selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 5
        fixedCellHeight = JBUI.scale(36)
        border = JBUI.Borders.empty()
        emptyText.text = "输入中文后自动生成候选"
        cellRenderer = CandidateCellRenderer()
        addListSelectionListener {
            repaint()
        }
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                insertSelectedAndClose()
            }
        })
    }

    private val debounceTimer = Timer(320) {
        requestSuggestions(autoInsertWhenReady = false)
    }.apply {
        isRepeats = false
    }

    private val inputField = JBTextField().apply {
        emptyText.text = "输入中文，AI 即时命名..."
        font = font.deriveFont(Font.PLAIN, font.size2D + 6f)
        border = JBUI.Borders.empty()
        background = INPUT_SURFACE_COLOR
        foreground = TEXT_COLOR
        caretColor = TEXT_COLOR
        putClientProperty("JTextField.Search.noBorderRing", true)
    }

    private val statusLabel = JBLabel(DEFAULT_HINT_TEXT).apply {
        foreground = INFO_COLOR
    }

    private var currentFormat = settings.pluginState.defaultFormat

    private val formatLabel = JBLabel(formatLabelText()).apply {
        foreground = META_COLOR
    }

    private var ownerWindow: Window? = null
    private var window: JWindow? = null
    private var closed = false
    private var requestToken = 0
    private var latestResolvedQuery = ""
    private var latestResolvedFormat: NamingFormat? = null
    private var pendingAutoInsertQuery: String? = null
    private var contentInitialized = false

    private val inputIcon = JBLabel(AllIcons.Actions.Search).apply {
        border = JBUI.Borders.emptyRight(10)
    }

    private val inputContainer = object : JPanel(BorderLayout(JBUI.scale(10), 0)) {
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.color = INPUT_SURFACE_COLOR
            g2.fillRoundRect(0, 0, width, height, JBUI.scale(16), JBUI.scale(16))
            g2.dispose()
        }
    }.apply {
        isOpaque = false
        border = JBUI.Borders.empty(10, 14)
        add(inputIcon, BorderLayout.WEST)
        add(inputField, BorderLayout.CENTER)
    }

    private val footer = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.emptyTop(6)
        add(statusLabel, BorderLayout.WEST)
        add(formatLabel, BorderLayout.EAST)
    }

    private val resultPane = JBScrollPane(resultList).apply {
        border = JBUI.Borders.emptyTop(8)
        isOpaque = false
        isVisible = false
        viewport.isOpaque = false
        preferredSize = JBUI.size(404, JBUI.scale(5 * 36 + 8))
        verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_NEVER
        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    }

    private val body = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(resultPane, BorderLayout.CENTER)
        add(footer, BorderLayout.SOUTH)
    }

    private val content = object : JPanel(BorderLayout(0, JBUI.scale(6))) {
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val surfaceX = JBUI.scale(12)
            val surfaceY = JBUI.scale(6)
            val surfaceWidth = width - JBUI.scale(24)
            val surfaceHeight = height - JBUI.scale(24)
            val arc = JBUI.scale(20)

            SHADOW_ALPHAS.forEachIndexed { index, alpha ->
                val spread = JBUI.scale(6 + index * 5)
                g2.color = Color(0, 0, 0, alpha)
                g2.fillRoundRect(
                    surfaceX - spread / 2,
                    surfaceY + JBUI.scale(6) - spread / 2,
                    surfaceWidth + spread,
                    surfaceHeight + spread,
                    arc + spread,
                    arc + spread
                )
            }

            g2.color = PANEL_COLOR
            g2.fillRoundRect(surfaceX, surfaceY, surfaceWidth, surfaceHeight, arc, arc)
            g2.dispose()
        }
    }.apply {
        border = JBUI.Borders.empty(22, 24, 24, 24)
        isOpaque = false
        add(inputContainer, BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)
    }

    fun show() {
        ownerWindow = resolveOwnerWindow()
        window = JWindow(ownerWindow).apply {
            type = Window.Type.POPUP
            background = Color(0, 0, 0, 0)
            rootPane.border = JBUI.Borders.empty()
            rootPane.isOpaque = false
            contentPane = createContent()
            addWindowFocusListener(object : WindowAdapter() {
                override fun windowLostFocus(e: WindowEvent) {
                    closeWindow()
                }
            })
        }

        updatePopupLayout(hasResults = false)
        window?.isVisible = true
        SwingUtilities.invokeLater {
            inputField.requestFocusInWindow()
        }
    }

    private fun createContent(): JComponent {
        if (!contentInitialized) {
            installInteractions()
            contentInitialized = true
        }

        return content
    }

    private fun installInteractions() {
        inputField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onInputChanged()
            override fun removeUpdate(e: DocumentEvent) = onInputChanged()
            override fun changedUpdate(e: DocumentEvent) = onInputChanged()
        })

        bindKey("moveDown", KeyEvent.VK_DOWN) { moveSelection(1) }
        bindKey("moveUp", KeyEvent.VK_UP) { moveSelection(-1) }
        bindKey("nextFormat", KeyEvent.VK_RIGHT) { cycleFormat(1) }
        bindKey("previousFormat", KeyEvent.VK_LEFT) { cycleFormat(-1) }
        bindKey("submitOrInsert", KeyEvent.VK_ENTER) { handleEnter() }
        bindKey("closeWindow", KeyEvent.VK_ESCAPE) { closeWindow() }
    }

    private fun bindKey(actionId: String, keyCode: Int, handler: () -> Unit) {
        inputField.inputMap.put(KeyStroke.getKeyStroke(keyCode, 0), actionId)
        inputField.actionMap.put(actionId, object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                handler()
            }
        })
    }

    private fun onInputChanged() {
        if (closed) {
            return
        }

        val query = normalizedQuery()
        pendingAutoInsertQuery = pendingAutoInsertQuery?.takeIf { it == query }

        if (query.isEmpty()) {
            latestResolvedQuery = ""
            latestResolvedFormat = null
            resultModel.clear()
            updatePopupLayout(hasResults = false)
            setStatus(DEFAULT_HINT_TEXT)
            return
        }

        setStatus("正在联想候选...")
        debounceTimer.restart()
    }

    private fun handleEnter() {
        val query = normalizedQuery()
        if (query.isEmpty()) {
            return
        }

        if (query == latestResolvedQuery && currentFormat == latestResolvedFormat && resultModel.size() > 0) {
            insertSelectedAndClose()
            return
        }

        pendingAutoInsertQuery = query
        requestSuggestions(autoInsertWhenReady = true)
    }

    private fun requestSuggestions(autoInsertWhenReady: Boolean) {
        val query = normalizedQuery()
        if (query.isEmpty() || closed) {
            return
        }

        requestToken += 1
        val currentToken = requestToken
        if (autoInsertWhenReady) {
            pendingAutoInsertQuery = query
        }

        setStatus("正在生成 5 个候选...")

        aiService.generateNames(
            description = query,
            format = currentFormat,
            onResult = { names ->
                if (closed || currentToken != requestToken) {
                    return@generateNames
                }

                latestResolvedQuery = query
                latestResolvedFormat = currentFormat
                updateResults(names)
                if (pendingAutoInsertQuery == query) {
                    insertSelectedAndClose()
                    return@generateNames
                }

                setStatus("${resultModel.size()} 个候选，回车直接插入")
            },
            onError = { error ->
                if (closed || currentToken != requestToken) {
                    return@generateNames
                }

                resultModel.clear()
                latestResolvedQuery = ""
                latestResolvedFormat = null
                updatePopupLayout(hasResults = false)
                setStatus(error, true)
            }
        )
    }

    private fun updateResults(names: List<String>) {
        resultModel.clear()
        names.take(5).forEach { resultModel.addElement(it) }
        if (resultModel.size() > 0) {
            updatePopupLayout(hasResults = true)
            resultList.selectedIndex = 0
            resultList.ensureIndexIsVisible(0)
        } else {
            updatePopupLayout(hasResults = false)
            setStatus("没有拿到可用候选，请换个描述试试", true)
        }
    }

    private fun moveSelection(delta: Int) {
        if (resultModel.size() == 0) {
            return
        }

        val currentIndex = resultList.selectedIndex.takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + delta).coerceIn(0, resultModel.size() - 1)
        resultList.selectedIndex = nextIndex
        resultList.ensureIndexIsVisible(nextIndex)
    }

    private fun cycleFormat(delta: Int) {
        val allFormats = NamingFormat.entries
        val currentIndex = allFormats.indexOf(currentFormat).coerceAtLeast(0)
        val nextIndex = ((currentIndex + delta) % allFormats.size + allFormats.size) % allFormats.size
        currentFormat = allFormats[nextIndex]
        settings.pluginState.defaultFormat = currentFormat
        formatLabel.text = formatLabelText()
        latestResolvedQuery = ""
        latestResolvedFormat = null

        if (normalizedQuery().isEmpty()) {
            setStatus(DEFAULT_HINT_TEXT)
            return
        }

        setStatus("已切换到 ${currentFormat.displayName}，正在刷新...")
        requestSuggestions(autoInsertWhenReady = false)
    }

    private fun insertSelectedAndClose() {
        val selectedValue = resultList.selectedValue ?: return
        val targetEditor = EditorTextInserter.selectedEditor(project) ?: fallbackEditor
        closeWindow()
        EditorTextInserter.insert(project, targetEditor, selectedValue)
    }

    private fun closeWindow() {
        if (closed) {
            return
        }

        closed = true
        debounceTimer.stop()
        requestToken += 1
        window?.dispose()
        window = null
    }

    private fun normalizedQuery(): String {
        return inputField.text.trim()
    }

    private fun setStatus(message: String, isError: Boolean = false) {
        statusLabel.text = message
        statusLabel.foreground = if (isError) ERROR_COLOR else INFO_COLOR
    }

    private fun updatePopupLayout(hasResults: Boolean) {
        resultPane.isVisible = hasResults
        val targetSize = if (hasResults) EXPANDED_SIZE else COLLAPSED_SIZE
        content.preferredSize = targetSize
        body.revalidate()
        body.repaint()
        content.revalidate()
        content.repaint()
        val currentWindow = window ?: return
        currentWindow.contentPane.preferredSize = targetSize
        val currentBounds = currentWindow.bounds
        currentWindow.pack()
        if (currentBounds.width == 0 || currentBounds.height == 0) {
            positionInOwnerCenter(currentWindow)
            return
        }

        if (currentBounds.width == currentWindow.width && currentBounds.height == currentWindow.height) {
            return
        }

        currentWindow.setLocation(
            currentBounds.x + (currentBounds.width - currentWindow.width) / 2,
            currentBounds.y + (currentBounds.height - currentWindow.height) / 2
        )
    }

    private fun resolveOwnerWindow(): Window? {
        return WindowManager.getInstance().suggestParentWindow(project)
            ?: SwingUtilities.getWindowAncestor(fallbackEditor.component)
            ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    }

    private fun positionInOwnerCenter(currentWindow: JWindow) {
        val ownerBounds = ownerWindow?.bounds
        if (ownerBounds != null) {
            currentWindow.setLocation(
                ownerBounds.x + (ownerBounds.width - currentWindow.width) / 2,
                ownerBounds.y + (ownerBounds.height - currentWindow.height) / 2
            )
        } else {
            currentWindow.setLocationRelativeTo(null)
        }
    }

    private fun formatLabelText(): String {
        return "\u2190 ${currentFormat.displayName} \u2192"
    }

    companion object {
        private const val DEFAULT_HINT_TEXT = "\u2190 \u2192 格式    \u2191 \u2193 候选    Enter 插入    Esc 关闭"
        private val SHADOW_ALPHAS = intArrayOf(36, 22, 12, 6)
        private val PANEL_COLOR = JBColor(0xFBFBFC, 0x262A30)
        private val INPUT_SURFACE_COLOR = JBColor(0xF3F5F7, 0x2D3138)
        private val INFO_COLOR = JBColor(0x8D949D, 0x69717C)
        private val META_COLOR = JBColor(0x99A0A8, 0x5E6672)
        private val ERROR_COLOR = JBColor(0xC42430, 0xFF6B68)
        private val SELECTED_ROW_COLOR = JBColor(0x5D82C5, 0x4468A8)
        private val TEXT_COLOR = JBColor(0x3A4350, 0xD6DCE5)
        private val SELECTED_TEXT_COLOR = JBColor(0xFFFFFF, 0xFFFFFF)
        private val COLLAPSED_SIZE: Dimension
            get() = JBUI.size(448, 118)
        private val EXPANDED_SIZE: Dimension
            get() = JBUI.size(448, 304)
    }

    private class CandidateCellRenderer : JPanel(BorderLayout()), ListCellRenderer<String> {
        private val textLabel = JLabel().apply {
            border = JBUI.Borders.empty(8, 16)
            font = font.deriveFont(Font.PLAIN, font.size2D + 1f)
        }

        private var selected = false

        init {
            isOpaque = false
            add(textLabel, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out String>,
            value: String?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            textLabel.text = value.orEmpty()
            textLabel.foreground = if (isSelected) SELECTED_TEXT_COLOR else TEXT_COLOR
            selected = isSelected
            return this
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.color = PANEL_COLOR
            g2.fillRect(0, 0, width, height)
            if (selected) {
                g2.color = SELECTED_ROW_COLOR
                g2.fillRoundRect(
                    JBUI.scale(10),
                    JBUI.scale(3),
                    width - JBUI.scale(20),
                    height - JBUI.scale(6),
                    JBUI.scale(10),
                    JBUI.scale(10)
                )
            }
            g2.dispose()
//            super.paintComponent(g)
        }
    }
}
