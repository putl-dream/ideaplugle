package putl.ideaplugle.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * 插件配置面板
 */
class NamingPluginConfigurable(private val project: Project) : Configurable {

    private val apiKeyField = JBPasswordField()
    private val apiUrlField = JBTextField()
    private val modelField = JBTextField()

    override fun getDisplayName(): String = "AI起名助手"

    override fun createComponent(): JComponent {
        val settings = NamingPluginSettings.getInstance(project)

        apiKeyField.text = settings.pluginState.apiKey
        apiUrlField.text = settings.pluginState.apiUrl
        modelField.text = settings.pluginState.model

        return panel {
            row("API Key:") {
                cell(apiKeyField)
                    .resizableColumn()
                    .align(Align.FILL)
                    .comment("输入OpenAI兼容的API Key")
            }

            row("API URL:") {
                cell(apiUrlField)
                    .resizableColumn()
                    .align(Align.FILL)
                    .comment("API接口地址")
            }

            row("模型:") {
                cell(modelField)
                    .resizableColumn()
                    .align(Align.FILL)
                    .comment("使用的模型名称")
            }
        }
    }

    override fun isModified(): Boolean {
        val settings = NamingPluginSettings.getInstance(project)
        return apiKeyField.text != settings.pluginState.apiKey ||
                apiUrlField.text != settings.pluginState.apiUrl ||
                modelField.text != settings.pluginState.model
    }

    override fun apply() {
        val settings = NamingPluginSettings.getInstance(project)
        settings.pluginState.apiKey = apiKeyField.text
        settings.pluginState.apiUrl = apiUrlField.text
        settings.pluginState.model = modelField.text
    }

    override fun reset() {
        val settings = NamingPluginSettings.getInstance(project)
        apiKeyField.text = settings.pluginState.apiKey
        apiUrlField.text = settings.pluginState.apiUrl
        modelField.text = settings.pluginState.model
    }
}
