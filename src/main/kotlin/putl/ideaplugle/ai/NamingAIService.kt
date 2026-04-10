package putl.ideaplugle.ai

import putl.ideaplugle.naming.NamingFormat
import putl.ideaplugle.settings.NamingPluginSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
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
        codeContext: String? = null,
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
                    content = buildSystemPrompt(format)
                ),
                Message(
                    role = "user",
                    content = buildUserPrompt(description, codeContext)
                )
            ),
            stream = false
        )

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val requestBody = json.encodeToString(request)
                val responseBody = sendHttpRequest(settings.pluginState.apiUrl, apiKey, requestBody)
                val response = json.decodeFromString<ChatResponse>(responseBody)

                val names = response.choices?.firstOrNull()?.message?.content
                    ?.parseNamesFromResponse()
                    ?: emptyList()

                ApplicationManager.getApplication().invokeLater {
                    onResult(names)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    onError("生成失败: ${e.message}")
                }
            }
        }
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

    private fun buildSystemPrompt(format: NamingFormat): String {
        return """
            你是一个资深的 Java 架构师。请根据用户的中文描述和当前代码上下文，生成 5 个合适的英文代码命名。

            命名格式要求：${format.displayName}（${format.description}）

            要求：
            1. 第一行必须给出最推荐、最适合直接插入代码的结果
            2. 命名要简洁、语义清晰
            3. 只输出英文命名结果，不要输出中文解释
            4. 严格遵循${format.displayName}命名规范
            5. 每行仅输出一个结果，不要加序号、项目符号、代码块或额外说明
            6. 优先结合类职责、方法语义和变量类型做命名
            7. 优先使用开发中常见、自然的英文表达

            示例输出：
            userLoginStatus
            currentUserStatus
            loginState
        """.trimIndent()
    }

    private fun buildUserPrompt(description: String, codeContext: String?): String {
        val contextBlock = codeContext ?: """
            - 所在类名: 未识别
            - 所在方法: 未识别
            - 正在定义的变量类型: 未识别
        """.trimIndent()

        return """
            [系统提供的当前代码上下文]
            $contextBlock

            [用户输入的描述]
            $description

            请根据上下文，生成符合规范的名称。
        """.trimIndent()
    }

    private fun String.parseNamesFromResponse(): List<String> {
        return lineSequence()
            .filter { !it.contains("```") }
            .flatMap { line -> line.split(',', '，').asSequence() }
            .map { it.trim().trim('`') }
            .map { it.replace(NUMBER_PREFIX_REGEX, "") }
            .map { it.replace(BULLET_PREFIX_REGEX, "") }
            .map { it.trim() }
            .filter { it.isNotEmpty() && NAME_REGEX.matches(it) }
            .distinct()
            .take(5)
            .toList()
    }

    companion object {
        private val NUMBER_PREFIX_REGEX = Regex("""^\d+[\.\)\]、\s-]+""")
        private val BULLET_PREFIX_REGEX = Regex("""^[-*•]+\s*""")
        private val NAME_REGEX = Regex("""[A-Za-z_][A-Za-z0-9._-]*""")
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
