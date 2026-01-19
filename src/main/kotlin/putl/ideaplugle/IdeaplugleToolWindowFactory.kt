package putl.ideaplugle

import putl.ideaplugle.ui.naming.NamingPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * AI起名助手工具窗口工厂
 */
class IdeaplugleToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 1. 创建 UI
        val namingPanel = NamingPanel(project)

        // 2. 包装成 Content
        val content = ContentFactory.getInstance()
            .createContent(namingPanel.component, "起名助手", false)

        // 3. 注册到 ToolWindow
        toolWindow.contentManager.addContent(content)
    }
}
