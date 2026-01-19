package badgertech.putl.ideaplugle.settings

import badgertech.putl.ideaplugle.naming.NamingFormat
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * 插件设置状态
 */
data class NamingPluginState(
    var apiKey: String = "",
    var apiUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
    var model: String = "qwen-plus-2025-07-28",
    var defaultFormat: NamingFormat = NamingFormat.CAMEL_CASE
)

/**
 * 插件设置服务
 */
@Service(Service.Level.PROJECT)
@State(
    name = "NamingPluginSettings",
    storages = [Storage("NamingPluginSettings.xml")]
)
class NamingPluginSettings : PersistentStateComponent<NamingPluginState> {

    var pluginState = NamingPluginState()

    override fun getState(): NamingPluginState = pluginState

    override fun loadState(state: NamingPluginState) {
        this.pluginState = state
    }

    companion object {
        fun getInstance(project: Project): NamingPluginSettings {
            return project.getService(NamingPluginSettings::class.java)
        }
    }
}
