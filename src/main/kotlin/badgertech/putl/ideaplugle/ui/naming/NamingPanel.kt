package badgertech.putl.ideaplugle.ui.naming

import badgertech.putl.ideaplugle.ai.NamingAIService
import badgertech.putl.ideaplugle.naming.NamingFormat
import badgertech.putl.ideaplugle.settings.NamingPluginConfigurable
import badgertech.putl.ideaplugle.settings.NamingPluginSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel

/**
 * 起名面板
 */
class NamingPanel(private val project: Project) {

    // 输入组件
    private val descriptionField = com.intellij.ui.components.JBTextField()
    private var selectedFormat: NamingFormat = NamingFormat.CAMEL_CASE

    // 结果显示
    private val resultModel = DefaultListModel<String>()
    private val resultList = JBList(resultModel).apply {
        emptyText.text = "暂无生成结果"
        
        // 设置渲染器：添加图标和内边距
        cellRenderer = object : ColoredListCellRenderer<String>() {
            override fun customizeCellRenderer(
                list: JList<out String>,
                value: String?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                if (value == null) return
                icon = AllIcons.Actions.Copy
                append(value)
                ipad = JBUI.insets(5, 10) // 增加内边距
                toolTipText = "点击复制: $value"
            }
        }
        
        // 鼠标悬停变为手型
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    // AI服务
    private val aiService = NamingAIService(project)
    private val settings = NamingPluginSettings.getInstance(project)

    init {
        // 初始化选中的格式
        selectedFormat = settings.pluginState.defaultFormat
    }

    val component: JComponent = panel {
        group("配置") {
            row("描述:") {
                cell(descriptionField)
                    .align(AlignX.FILL)
                    .comment("输入变量用途的描述，如：用户名、订单号等")
                    .focused()
            }

            row("格式:") {
                val comboBox = JComboBox(NamingFormat.getAll())
                comboBox.selectedItem = selectedFormat
                
                // 设置渲染器以显示 displayName
                comboBox.renderer = SimpleListCellRenderer.create { label, value, _ ->
                    label.text = value?.displayName ?: ""
                }

                comboBox.addActionListener {
                    selectedFormat = comboBox.selectedItem as NamingFormat
                    // 更新全局配置
                    settings.pluginState.defaultFormat = selectedFormat
                }
                cell(comboBox)
                    .align(AlignX.FILL)
                    .comment("选择变量命名格式")
            }

            row {
                button("生成变量名") {
                    generateNames()
                }.align(AlignX.FILL)
            }
        }

        group("生成结果") {
            row {
                scrollCell(resultList)
                    .align(Align.FILL)
            }.resizableRow()

            row {
                comment("💡 点击列表项即可复制到剪贴板")
                
                // 添加设置链接
                link("设置") {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, NamingPluginConfigurable::class.java)
                }.align(AlignX.RIGHT)
            }
        }.resizableRow()

        // 点击复制功能
        resultList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        
        // 使用 MouseListener 处理点击，比 ListSelectionListener 更适合处理重复点击同一项的情况
        resultList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (resultList.model.size == 0) return
                
                val index = resultList.locationToIndex(e.point)
                if (index >= 0) {
                    val selected = resultModel.getElementAt(index)
                    copyToClipboard(selected)
                    showCopiedHint(e, selected)
                    resultList.clearSelection()
                }
            }
        })
    }

    private fun generateNames() {
        val description = descriptionField.text.trim()
        if (description.isEmpty()) {
            showError("请输入变量描述")
            return
        }

        resultModel.clear()
        resultModel.addElement("正在生成...")

        aiService.generateNames(
            description = description,
            format = selectedFormat,
            onResult = { names ->
                resultModel.clear()
                if (names.isEmpty()) {
                    resultModel.addElement("未生成结果，请重试")
                } else {
                    names.forEach { name ->
                        resultModel.addElement(name)
                    }
                }
            },
            onError = { error ->
                resultModel.clear()
                resultModel.addElement("错误: $error")
            }
        )
    }

    private fun copyToClipboard(text: String) {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val stringSelection = java.awt.datatransfer.StringSelection(text)
        clipboard.setContents(stringSelection, null)
    }
    
    private fun showCopiedHint(e: MouseEvent, text: String) {
        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder("已复制: $text", com.intellij.openapi.ui.MessageType.INFO, null)
            .setFadeoutTime(2000)
            .createBalloon()
            .show(RelativePoint(e.component, e.point), Balloon.Position.above)
    }

    private fun showError(message: String) {
        resultModel.clear()
        resultModel.addElement("错误: $message")
    }
}
