package com.xiaoluolyg.beegent

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal fun id(): String = "jwb-${System.currentTimeMillis().toString(36)}-${UUID.randomUUID().toString().take(8)}"
internal fun obj(vararg values: Pair<String, Any?>): JSONObject = JSONObject().apply { values.forEach { put(it.first, it.second) } }
internal fun JSONObject.s(key: String): String = optString(key, "").takeUnless { it == "null" } ?: ""
internal fun JSONObject.o(key: String): JSONObject = optJSONObject(key) ?: JSONObject()
internal fun JSONObject.a(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
internal fun usage(event: String): Boolean = Regex("^(context|chat)\\.usage([._]|$)").containsMatchIn(event)

internal data class ConnectionSettings(val ip: String, val messagePort: Int, val downloadPort: Int) {
    val endpoint: String = "ws://$ip:$messagePort/ws"
    val downloadOrigin: String = "http://$ip:$downloadPort"
    companion object {
        fun parse(ip: String, messagePort: String, downloadPort: String): ConnectionSettings {
            val address = ip.trim().split('.')
            require(address.size == 4 && address.all { it.matches(Regex("[0-9]{1,3}")) && it.toInt() <= 255 }) {
                "请输入电脑的 IPv4 地址"
            }
            fun port(text: String, fallback: Int): Int {
                if (text.isBlank()) return fallback
                require(text.matches(Regex("[0-9]+")) && (text.toIntOrNull() ?: 0) in 1..65535) { "端口必须为 1–65535" }
                return text.toInt()
            }
            return ConnectionSettings(address.joinToString(".") { it.toInt().toString() }, port(messagePort.trim(), 29000), port(downloadPort.trim(), 25173))
        }
    }
}

internal class RequestFailure(message: String, val definitive: Boolean = false) : Exception(message)

internal class Diagnostics(context: Context) {
    private val directory = File(context.filesDir, "diagnostics").apply { mkdirs() }
    private val run = id()
    private val sequence = AtomicInteger()
    private val written = AtomicInteger()
    private val dropped = AtomicInteger()
    private val queuedBytes = AtomicInteger()
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(1024))
    private fun file(index: Int) = File(directory, if (index == 0) "communication.jsonl" else "communication.$index.jsonl")
    private fun scrub(value: Any?, key: String = "", depth: Int = 0): Any? {
        if (Regex("token$|password|passwd|secret|api[_-]?key|access[_-]?key|authorization|cookie|credential", RegexOption.IGNORE_CASE).containsMatchIn(key)) return "[REDACTED]"
        if (value is JSONObject) return JSONObject().apply { value.keys().forEach { name -> put(name, scrub(value.opt(name), name, depth + 1)) } }
        if (value is JSONArray) return JSONArray().apply { (0 until value.length()).forEach { put(scrub(value.opt(it), key, depth + 1)) } }
        if (value !is String) return value
        if (Regex("base64|binary|file_bytes", RegexOption.IGNORE_CASE).containsMatchIn(key)) return "[BINARY OMITTED: ${value.length} characters]"
        if (depth < 3 && value.trimStart().startsWith("{")) return try { scrub(JSONObject(value), key, depth + 1) } catch (_: Exception) { clean(value) }
        if (depth < 3 && value.trimStart().startsWith("[")) return try { scrub(JSONArray(value), key, depth + 1) } catch (_: Exception) { clean(value) }
        return clean(value)
    }
    private fun clean(value: String): String = value
        .replace(Regex("data:[^\\s,;]+(?:;[^,\\s]*)?;base64,[A-Za-z0-9+/=_-]+", RegexOption.IGNORE_CASE), "[DATA URI OMITTED]")
        .replace(Regex("([?&][^=&#\\s]*(?:token|secret|api[_-]?key|signature|credential|password)[^=&#\\s]*=)[^&#\\s\\\"']+", RegexOption.IGNORE_CASE), "$1[REDACTED]")
        .replace(Regex("((?:Bearer|Basic)\\s+)[A-Za-z0-9._~+/=-]+", RegexOption.IGNORE_CASE), "$1[REDACTED]")
        .replace(Regex("(https?://)[^\\s/@]+:[^\\s/@]+@", RegexOption.IGNORE_CASE), "$1[REDACTED]@")
        .replace(Regex("\\bsk-[A-Za-z0-9_-]{12,}\\b"), "[REDACTED]")
        .replace(Regex("((?:api[_-]?key|password|secret|access[_-]?token)\\s*[:=]\\s*)[^\\s,;]+", RegexOption.IGNORE_CASE), "$1[REDACTED]")
        .replace(Regex("[A-Za-z0-9+/=_-]{4096,}"), "[LONG ENCODED VALUE OMITTED]")
    fun record(event: String, details: JSONObject = JSONObject(), level: String = "info") {
        try {
            val entry = obj("version" to 1, "time" to java.time.Instant.now().toString(), "runId" to run,
                "sequence" to sequence.incrementAndGet(), "level" to level, "event" to event, "details" to scrub(details))
            var line = entry.toString()
            if (line.length > 128 * 1024) line = obj("version" to 1, "event" to event, "truncated" to true, "characters" to line.length, "preview" to line.take(120 * 1024)).toString()
            val bytes = line.toByteArray(Charsets.UTF_8).size
            if (queuedBytes.addAndGet(bytes) > 2 * 1024 * 1024) { queuedBytes.addAndGet(-bytes); dropped.incrementAndGet(); return }
            try { executor.execute {
                try {
                    val current = file(0)
                    if (current.length() + line.toByteArray().size > 4 * 1024 * 1024) {
                        file(2).delete()
                        if (file(1).exists() && !file(1).renameTo(file(2))) throw Exception("日志轮换失败")
                        if (current.exists() && !current.renameTo(file(1))) throw Exception("日志轮换失败")
                    }
                    current.appendText(line + "\n")
                    written.incrementAndGet()
                } catch (_: Exception) { dropped.incrementAndGet() }
                finally { queuedBytes.addAndGet(-bytes) }
            } } catch (_: java.util.concurrent.RejectedExecutionException) { queuedBytes.addAndGet(-bytes); dropped.incrementAndGet() }
        } catch (_: Exception) { dropped.incrementAndGet() }
    }
    fun summary() = "记录中 · 本次启动已写入 ${written.get()} 条" + if (dropped.get() > 0) " · 丢弃 ${dropped.get()} 条" else ""
    fun export(): String { executor.submit {}.get(); return (2 downTo 0).joinToString("") { file(it).takeIf(File::exists)?.readText() ?: "" } }
    fun clear() { executor.submit { (0..2).forEach { file(it).delete() }; sequence.set(0); written.set(0); dropped.set(0) }.get() }
    fun close() { executor.shutdown() }
}

internal class SwarmSocket(private val log: Diagnostics) {
    val state = mutableStateOf("closed")
    var onState: (String, String) -> Unit = { _, _ -> }
    var onEvent: (String, JSONObject, JSONObject) -> Unit = { _, _, _ -> }
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var socket: WebSocket? = null
    private var generation = 0
    private var opening: CompletableDeferred<Unit>? = null
    private data class Pending(val method: String, val session: String, val result: CompletableDeferred<JSONObject>, val runtime: Boolean, val started: Long)
    private val pending = mutableMapOf<String, Pending>()
    private fun state(value: String, reason: String = "") { state.value = value; log.record("ws.state", obj("state" to value, "reason" to reason)); onState(value, reason) }
    suspend fun connect(endpoint: String) {
        if (state.value == "open") return
        opening?.let { return it.await() }
        val attempt = CompletableDeferred<Unit>()
        opening = attempt
        val current = ++generation
        state("connecting")
        log.record("ws.connect.start", obj("endpoint" to endpoint))
        socket = client.newWebSocket(Request.Builder().url(endpoint).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { main.post {
                if (current == generation) { state("open"); attempt.complete(Unit) }
            } }
            override fun onMessage(webSocket: WebSocket, text: String) { main.post {
                if (current != generation) return@post
                val frame = try { JSONObject(text) } catch (_: Exception) {
                    log.record("ws.receive.invalid", obj("characters" to text.length), "error"); return@post
                }
                val type = frame.s("type")
                if (type !in listOf("event", "res") || frame.has("payload") && frame.opt("payload") !is JSONObject ||
                    type == "res" && (frame.s("id").isEmpty() || frame.opt("ok") !is Boolean) ||
                    type == "event" && frame.s("event").isEmpty()) {
                    log.record("ws.receive.invalid", obj("characters" to text.length, "reason" to "invalid frame"), "error"); return@post
                }
                val payload = frame.o("payload")
                val rid = frame.s("id").ifEmpty { payload.s("request_id") }
                log.record("ws.receive", obj("frame" to frame, "requestId" to rid, "characters" to text.length))
                if (type == "res") {
                    val item = pending[rid] ?: return@post
                    if (frame.optBoolean("ok") && item.runtime) return@post
                    pending.remove(rid)
                    if (frame.optBoolean("ok")) item.result.complete(payload)
                    else item.result.completeExceptionally(RequestFailure(frame.s("error").ifEmpty { "请求被拒绝" }, true))
                } else {
                    var event = frame.s("event")
                    if (event == "chat.final" && payload.s("event_type") == "runtime.accepted") event = "runtime.accepted"
                    val item = pending[payload.s("request_id")]
                    if (item?.runtime == true && (event == "runtime.accepted" || event == "chat.error")) {
                        pending.remove(payload.s("request_id"))
                        if (event == "runtime.accepted") item.result.complete(payload)
                        else item.result.completeExceptionally(RequestFailure(payload.s("error").ifEmpty { "补充被拒绝" }, payload.s("code") != "SESSION_INPUT_DELIVERY_UNKNOWN"))
                    }
                    if (event != "connection.ack" && event != "connect.ack") try { onEvent(event, payload, frame) }
                    catch (e: Exception) { log.record("ws.receive.handler_error", obj("event" to event, "error" to e.message), "error") }
                }
            } }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { main.post {
                if (current == generation) { if (!attempt.isCompleted) attempt.completeExceptionally(t); close("连接失败：${t.message ?: "网络不可达"}") }
            } }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { main.post {
                if (current == generation) close(reason.ifBlank { "连接已断开" })
            } }
        })
        try { withTimeout(10_000) { attempt.await() } }
        catch (e: Exception) { close(e.message ?: "连接失败"); throw e }
        finally { if (opening === attempt) opening = null }
    }
    suspend fun request(method: String, params: JSONObject, rid: String = id(), runtime: Boolean = false): JSONObject {
        val webSocket = socket ?: throw RequestFailure("尚未连接电脑", true)
        if (state.value != "open") throw RequestFailure("尚未连接电脑", true)
        val result = CompletableDeferred<JSONObject>()
        pending[rid] = Pending(method, params.s("session_id"), result, runtime, System.currentTimeMillis())
        val frame = obj("type" to "req", "id" to rid, "method" to method, "params" to params)
        log.record("ws.send", obj("frame" to frame, "requestId" to rid, "method" to method))
        if (!webSocket.send(frame.toString())) { pending.remove(rid); throw RequestFailure("发送中断，结果待确认") }
        return try { withTimeout(30_000) { result.await() } }
        catch (e: Exception) { if (e is kotlinx.coroutines.TimeoutCancellationException) throw RequestFailure("服务端响应超时，结果待确认，请勿重复发送") else throw e }
        finally { pending.remove(rid) }
    }
    fun close(reason: String = "连接已断开") {
        generation++
        socket?.close(1000, reason.take(100)); socket = null
        opening?.takeUnless { it.isCompleted }?.completeExceptionally(RequestFailure(reason))
        opening = null
        pending.values.forEach { it.result.completeExceptionally(RequestFailure(reason)) }; pending.clear()
        if (state.value != "closed") state("closed", reason)
    }
}
