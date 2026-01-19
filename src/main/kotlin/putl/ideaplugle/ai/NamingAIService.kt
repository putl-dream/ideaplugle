package putl.ideaplugle.ai

import putl.ideaplugle.naming.NamingFormat
import putl.ideaplugle.settings.NamingPluginSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * AI起名服务
 */
class NamingAIService(private val project: Project) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 生成变量名
     */
    fun generateNames(
        description: String,
        format: NamingFormat,
        onResult: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        val settings = project.service<NamingPluginSettings>()
        val apiKey = settings.pluginState.apiKey

        if (apiKey.isBlank()) {
            onError("请先在设置中配置 API Key")
            return
        }

        val request = ChatRequest(
            model = settings.pluginState.model,
            messages = listOf(
                Message(
                    role = "system",
                    content = buildPrompt(format)
                ),
                Message(
                    role = "user",
                    content = description
                )
            ),
            stream = false
        )

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "正在生成变量名...", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val requestBody = json.encodeToString(request)
                    val responseBody = sendHttpRequest(settings.pluginState.apiUrl, apiKey, requestBody)
                    val response = json.decodeFromString<ChatResponse>(responseBody)

                    val names = response.choices?.firstOrNull()?.message?.content
                        ?.parseNamesFromResponse()
                        ?: emptyList()

                    // 在EDT线程中更新UI
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        onResult(names)
                    }
                } catch (e: Exception) {
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        onError("生成失败: ${e.message}")
                    }
                }
            }
        })
    }

    private fun sendHttpRequest(urlString: String, apiKey: String, requestBody: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            // 发送请求体
            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray(StandardCharsets.UTF_8))
            }

            // 读取响应
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } else {
                val errorStream = connection.errorStream
                val errorText = if (errorStream != null) {
                    errorStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                } else {
                    "HTTP $responseCode"
                }
                throw Exception("HTTP错误 $responseCode: $errorText")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPrompt(format: NamingFormat): String {
        return """
            你是一个专业的变量命名助手。请根据用户的描述，生成5个合适的变量名。

            命名格式要求：${format.displayName}（${format.description}）

            要求：
            1. 变量名要简洁、语义清晰
            2. 遵循${format.displayName}命名规范
            3. 使用英文单词
            4. 直接返回变量名列表，每行一个，不要添加序号或其他说明文字
            5. 不要包含代码块标记

            示例输出格式：
            variableName1
            variableName2
            variableName3
        """.trimIndent()
    }

    private fun String.parseNamesFromResponse(): List<String> {
        return this.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("```") }
            .take(5)
    }
}

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class ChatResponse(
    val choices: List<Choice>?
)

@Serializable
data class Choice(
    val message: Message
)
