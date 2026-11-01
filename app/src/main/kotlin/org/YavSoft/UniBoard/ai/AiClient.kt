/*
 * UniBoard — Unicode 输入法
 * Copyright (C) 2026 UniBoard contributors
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证第 3 版
 * 或（你选择的）任何更新版本所规定的条款，重新分发和/或修改它。
 *
 * 本程序基于"希望它有用"的目的分发，但不提供任何担保，
 * 也不承诺适用于特定用途。完整条款见项目根目录的 LICENSE 文件。
 */

package org.YavSoft.UniBoard.ai

import android.os.Handler
import android.os.Looper
import org.YavSoft.UniBoard.core.Prefs
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * OpenAI 兼容接口的极简客户端，默认对接 DeepSeek。
 *
 * 只用 HttpURLConnection 与手写 JSON，不引入第三方网络库，便于审计。
 */
class AiClient(private val prefs: Prefs) {

    /** 常见 OpenAI 兼容服务商预设 */
    data class Preset(
        val id: String,
        val label: String,
        val endpoint: String,
        val model: String
    )

    companion object {
        val PRESETS = listOf(
            Preset("deepseek", "DeepSeek", "https://api.deepseek.com/chat/completions", "deepseek-chat"),
            Preset("openai", "OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini"),
            Preset("moonshot", "Kimi", "https://api.moonshot.cn/v1/chat/completions", "moonshot-v1-8k"),
            Preset("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4-flash"),
            Preset("local", "本地 / 局域网", "http://127.0.0.1:11434/v1/chat/completions", "qwen2.5"),
            Preset("custom", "自定义", "", "")
        )

        fun presetOf(id: String): Preset = PRESETS.firstOrNull { it.id == id } ?: PRESETS.first()

        /** 是否本机 / 局域网地址（这类地址不出公网，隐私模式下也允许） */
        fun isLocalEndpoint(url: String): Boolean {
            val host = runCatching { java.net.URI(url.trim()).host?.lowercase() }.getOrNull() ?: return false
            if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host.endsWith(".local")) return true
            if (host.startsWith("192.168.") || host.startsWith("10.")) return true
            if (host.startsWith("172.")) {
                val second = host.split('.').getOrNull(1)?.toIntOrNull() ?: 0
                if (second in 16..31) return true
            }
            return false
        }

        /** 是否可用：启用 +（有 Key 或 本地地址） */
        fun isConfigured(prefs: org.YavSoft.UniBoard.core.Prefs): Boolean =
            prefs.aiEnabled && (prefs.aiApiKey.isNotBlank() || isLocalEndpoint(prefs.aiEndpoint))
    }

    interface Callback {
        fun onResult(text: String)
        fun onError(message: String)
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val polishSystem =
        "润色下列文字，使其通顺、准确。仅输出润色后的文字本身，不作解释，不加引号，不添加任何前后缀。"

    private val suggestSystem =
        "你是输入法补全引擎。根据已输入内容给出至多 3 个简短、自然的后续片段，以 | 分隔。仅输出结果，不作解释。"

    /** 通用补全：自定义 system / user 提示词，供词典生成等场景使用 */
    fun complete(system: String, user: String, callback: Callback) {
        if (!ready(callback)) return
        request(system, user, callback)
    }

    fun polish(text: String, callback: Callback) {
        if (!ready(callback)) return
        if (text.isBlank()) {
            callback.onError("没有可润色的文本")
            return
        }
        val system = prefs.aiPolishPrompt.ifBlank { polishSystem }
        request(system, text, callback)
    }

    fun suggest(typed: String, callback: Callback) {
        if (!ready(callback)) return
        if (typed.isBlank()) {
            callback.onError("请先输入内容")
            return
        }
        request(suggestSystem, typed, callback)
    }

    private fun ready(callback: Callback): Boolean {
        if (!prefs.aiEnabled) {
            callback.onError("AI 未启用，请在设置中开启")
            return false
        }
        if (prefs.aiApiKey.isBlank() && !isLocalEndpoint(prefs.aiEndpoint)) {
            callback.onError("未填写 API 密钥")
            return false
        }
        return true
    }

    private fun request(system: String, user: String, callback: Callback) {
        worker.execute {
            var error: String? = null
            var result: String? = null
            try {
                val body = buildBody(system, user)
                val conn = URL(prefs.aiEndpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                val timeoutMs = prefs.aiTimeoutSec * 1000
                conn.connectTimeout = minOf(15000, timeoutMs)
                conn.readTimeout = timeoutMs
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Authorization", "Bearer " + prefs.aiApiKey)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code in 200..299) {
                    val content = extractContent(text)
                    if (content.isNullOrBlank()) error = "AI 返回内容为空" else result = content
                } else {
                    val snippet = text.replace(Regex("\\s+"), " ").take(120)
                    val hint = when (code) {
                        401, 403 -> "API 密钥无效，或无权访问该模型"
                        404 -> "接口地址有误（请检查是否缺少 /v1/chat/completions）"
                        429 -> "请求过于频繁，请稍后重试"
                        in 500..599 -> "服务端错误，请稍后重试"
                        else -> "HTTP $code"
                    }
                    error = if (snippet.isEmpty()) hint else "$hint｜$snippet"
                }
                conn.disconnect()
            } catch (e: java.net.SocketTimeoutException) {
                error = "请求超时（可在设置中增大超时时间）"
            } catch (e: java.net.UnknownHostException) {
                error = "无法解析接口域名，请检查地址与网络"
            } catch (e: javax.net.ssl.SSLException) {
                error = "TLS 握手失败，本地模型请改用 http://"
            } catch (e: Exception) {
                error = e.message ?: "网络错误"
            }
            main.post {
                val r = result
                if (r != null) callback.onResult(r.trim()) else callback.onError(error ?: "请求失败")
            }
        }
    }

    private fun buildBody(system: String, user: String): String {
        val sb = StringBuilder(256)
        sb.append("{\"model\":\"").append(escape(prefs.aiModel))
        val temp = prefs.aiTemperature / 100.0
        sb.append("\",\"temperature\":").append(temp).append(",\"max_tokens\":512,\"messages\":[")
        sb.append("{\"role\":\"system\",\"content\":\"").append(escape(system)).append("\"},")
        sb.append("{\"role\":\"user\",\"content\":\"").append(escape(user)).append("\"}]}")
        return sb.toString()
    }

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append(' ') else sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** 从响应里取出第一个非空的 message.content */
    private fun extractContent(json: String): String? {
        val key = "\"content\":\""
        var from = 0
        while (true) {
            val idx = json.indexOf(key, from)
            if (idx < 0) return null
            var i = idx + key.length
            val sb = StringBuilder()
            var closed = false
            while (i < json.length) {
                val c = json[i]
                if (c == '\\' && i + 1 < json.length) {
                    i++
                    when (json[i]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        else -> sb.append(json[i])
                    }
                    i++
                    continue
                }
                if (c == '"') {
                    closed = true
                    break
                }
                sb.append(c)
                i++
            }
            if (closed && sb.isNotBlank()) return sb.toString()
            from = i + 1
        }
    }
}
