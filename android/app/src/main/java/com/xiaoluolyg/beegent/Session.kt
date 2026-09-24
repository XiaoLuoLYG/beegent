package com.xiaoluolyg.beegent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

internal data class Mode(val role: String = "agent", val profile: String = "work", val style: String = "normal") {
    fun wire() = "$role.$profile.$style"
    companion object { fun parse(value: String, workMode: String = "") = Mode(
        if (value.startsWith("team")) "team" else "agent",
        if (value.contains(".code.") || workMode == "code") "code" else "work",
        if (value.endsWith(".plan")) "plan" else "normal") }
}
internal data class Queued(val id: String, val content: String, val status: String = "queued", val error: String = "", val requestId: String = "")
internal data class Todo(val id: String, val content: String, val active: String, val status: String)
internal data class Step(val id: String, val title: String, val text: String = "", val status: String = "running")
internal data class AgentActivity(val id: String, val title: String, val text: String, val task: String, val sequence: Int, val at: Long)
internal data class AgentInfo(val id: String, val name: String, val task: String = "", val status: String = "状态待确认",
    val latest: String = "", val revision: Int = -1, val updatedAt: Long = -1, val running: Boolean = false,
    val activities: List<AgentActivity> = emptyList(), val omitted: Int = 0)
internal data class Choice(val label: String, val value: String, val description: String)
internal data class Question(val text: String, val header: String, val choices: List<Choice>, val multiple: Boolean, val cardId: String)
internal data class Interaction(val id: String, val source: String, val questions: List<Question>, val planActions: List<JSONObject>,
    val approvalSchema: String, val evolution: JSONObject?, val planKind: String, val planContent: String, val planLanguage: String,
    val flow: JSONObject?, val status: String = "pending", val error: String = "", val replyId: String = "",
    val ended: Boolean = false, val executePlan: Boolean = false)
internal data class Attachment(val id: String, val name: String, val mime: String, val size: Long, val url: String, val base64: String) {
    val available get() = url.isNotEmpty() || base64.isNotEmpty()
    val image get() = mime.startsWith("image/", true) || name.matches(Regex(".*\\.(png|jpe?g|gif|webp|bmp|heic|heif|avif)$", RegexOption.IGNORE_CASE))
    val html get() = mime.substringBefore(';').trim().lowercase() in listOf("text/html", "application/xhtml+xml") || name.endsWith(".html", true) || name.endsWith(".htm", true)
    companion object {
        fun from(id: String, wire: JSONObject, origin: String): Attachment {
            val name = wire.s("name").ifEmpty { wire.s("filename") }.ifEmpty { wire.s("path").substringAfterLast('/').substringAfterLast('\\') }.ifEmpty { "附件" }
            val value = wire.s("download_url").ifEmpty { wire.s("url") }
            val url = if (value.startsWith("https://") || value.startsWith("http://")) value else if (value.startsWith('/') && !value.startsWith("//")) origin + value else ""
            return Attachment(id, name, wire.s("mime_type").ifEmpty { wire.s("mimeType") }.ifEmpty { "application/octet-stream" },
                listOf("size", "size_bytes", "sizeBytes").firstNotNullOfOrNull { wire.optLong(it).takeIf { size -> size > 0 } } ?: 0,
                url, wire.s("base64_data").ifEmpty { wire.s("base64Data") })
        }
    }
}
internal data class ChatRow(val id: String, val role: String, val text: String = "", val kind: String = "text",
    val delivery: String = "", val answerId: String = "", val steps: List<Step> = emptyList(), val collapsed: Boolean = false,
    val attachments: List<Attachment> = emptyList(), val interaction: Interaction? = null, val agent: AgentInfo? = null,
    val historical: Boolean = false)

internal class Session(val id: String, val endpoint: String, val origin: String, initialMode: Mode, initialTitle: String = "新对话") {
    var title by mutableStateOf(initialTitle)
    var mode by mutableStateOf(initialMode)
    var state by mutableStateOf("idle")
    var notice by mutableStateOf("")
    var draft by mutableStateOf("")
    var requestId = ""
    var stopRequestId = ""
    var executionId = ""
    var historyLoading by mutableStateOf(false)
    var historyLoaded by mutableStateOf(false)
    var historyNext: JSONObject? = null
    var historySnapshot = ""
    var historySnapshotEnd = 0L
    var historyRecords = emptyList<JSONObject>()
    var liveRevision = 0
    var queuePaused by mutableStateOf(false)
    val rows = mutableStateListOf<ChatRow>()
    val todos = mutableStateListOf<Todo>()
    val queue = mutableStateListOf<Queued>()
    val replyOwners = mutableSetOf<String>()
    private val seen = mutableSetOf<String>()
    private val phases = mutableMapOf<String, String>()
    private val retired = mutableSetOf<String>()
    private val bodySeen = mutableSetOf<String>()
    private val epochs = mutableMapOf<String, Int>()
    private val attachmentIds = mutableSetOf<String>()
    private val agentSeen = mutableSetOf<String>()
    private var counter = 0
    val pendingInteraction get() = rows.any { it.interaction?.status in listOf("pending", "sending", "failed", "unknown") }
    fun row(id: String) = rows.firstOrNull { it.id == id }
    fun update(id: String, change: (ChatRow) -> ChatRow) { val i = rows.indexOfFirst { it.id == id }; if (i >= 0) rows[i] = change(rows[i]) }
    fun add(row: ChatRow) { rows.add(row) }
    fun clear() { rows.clear(); todos.clear(); queue.clear(); historyRecords = emptyList(); historyNext = null; draft = "" }
    fun begin(rid: String, content: String) {
        liveRevision++; requestId = rid; stopRequestId = ""; executionId = ""; state = "sending"; notice = ""
        if (rows.isEmpty()) title = content.take(22)
        add(ChatRow(rid, "user", content, delivery = "发送中"))
    }
    fun accepted(rid: String) { update(rid) { it.copy(delivery = if (state == "idle") "" else "已受理") }; if (rid == requestId && state == "sending") state = "running" }
    fun failed(rid: String, message: String, definitive: Boolean) {
        update(rid) { it.copy(delivery = if (definitive) "发送失败" else "结果待确认") }
        if (rid == requestId) { state = if (definitive) "error" else "unknown"; notice = message }
    }
    fun interaction(id: String): Interaction? = row("question-$id")?.interaction
    fun changeInteraction(id: String, change: (Interaction) -> Interaction) = update("question-$id") { row -> row.copy(interaction = row.interaction?.let(change)) }
    fun expire() {
        rows.indices.forEach { index ->
            val row = rows[index]
            rows[index] = when {
                row.interaction?.status in listOf("pending", "sending", "failed", "unknown") -> row.copy(interaction = row.interaction?.copy(status = "expired", executePlan = false))
                row.kind == "steps" -> row.copy(steps = row.steps.map { if (it.status == "running") it.copy(status = "stopped") else it })
                row.agent?.running == true -> row.copy(agent = row.agent.copy(status = "状态待确认", running = false))
                else -> row
            }
        }
    }
    fun restore(records: List<JSONObject>) {
        val unique = linkedMapOf<String, JSONObject>()
        records.filterNot { usage(it.s("event_type")) }.forEach {
            unique[listOf(it.s("id").ifEmpty { it.s("request_id") }, it.s("event_type").ifEmpty { it.s("role") }, it.optLong("timestamp"), it.s("content")).joinToString("|")] = it
        }
        historyRecords = unique.values.sortedBy { it.optLong("timestamp") }
        val live = rows.flatMap { listOf(it.id, it.answerId) }.filter(String::isNotBlank).toSet()
        val replay = Session(id, endpoint, origin, mode, title)
        var turn = "history"
        historyRecords.forEachIndexed { index, record ->
            val request = record.s("request_id").ifEmpty { turn }
            if (record.s("role") in listOf("user", "human")) {
                turn = request
                if (request !in live) replay.add(ChatRow("history-user-$index", "user", record.s("content"), historical = true))
            } else if (request !in live) {
                val event = record.s("event_type").ifEmpty { "chat.final" }
                if (event !in listOf("chat.processing_status", "runtime.accepted", "chat.output_phase")) {
                    val payload = JSONObject(record.toString()).put("request_id", request).put("session_id", id)
                    if (event == "chat.final") payload.put("segment_id", "history-$index")
                    replay.apply(event, payload, JSONObject())
                }
            }
        }
        replay.expire()
        rows.removeAll { it.historical }
        rows.addAll(0, replay.rows.map { it.copy(id = "history:${it.id}", historical = true, collapsed = it.kind == "steps" || it.collapsed) })
        historyLoaded = true
    }
    fun apply(event: String, p: JSONObject, frame: JSONObject) {
        if (usage(event)) return
        val request = p.s("turn_request_id").ifEmpty { p.s("request_id") }.ifEmpty { requestId }
        if (frame.has("seq")) {
            val key = "${frame.s("stream_id").ifEmpty { request }}:${frame.optInt("seq")}:$event"
            if (!seen.add(key)) return
            if (seen.size > 2048) seen.clear()
        }
        if (event in listOf("todo.updated", "todo.update")) {
            if (p.s("subagent_id").isNotEmpty() || p.s("parent_session_id").isNotEmpty() && p.s("session_id") != id) return
            if (p.has("todos")) { todos.clear(); val values = linkedMapOf<String, Todo>(); p.a("todos").objects().forEach { item ->
                val tid = item.s("id"); val content = item.s("content")
                if (tid.isNotEmpty() && content.isNotEmpty() && item.s("status") != "deleted") values[tid] = Todo(tid, content, item.s("activeForm").ifEmpty { content }, item.s("status").takeIf { it in listOf("pending", "in_progress", "completed", "cancelled") } ?: "pending")
            }; todos.addAll(values.values) }
            return
        }
        liveRevision++
        if (event == "chat.subtask_update" || event == "chat.subagent_activity") { subagent(event, p); return }
        if (event == "chat.ask_user_question" || event == "plan.approval_required") { question(request, p); state = "waiting"; notice = "请在对话中的确认卡片上回复"; return }
        if (event in listOf("chat.reasoning", "chat.tool_call", "chat.tool_result", "chat.tool_update")) { tool(event, request, p); return }
        if (event in listOf("chat.file", "chat.media")) { attachment(request, p); return }
        if (event == "chat.output_phase") { p.s("output_phase_id").takeIf(String::isNotEmpty)?.let { phases[request]?.let { old -> if (old != it) retired.add("$request:$old") }; phases[request] = it }; if (request == requestId) executionId = p.s("execution_id").ifEmpty { executionId }; return }
        if (event == "chat.input_received" && p.s("input_request_id").isNotEmpty() && p.has("content")) {
            val input = p.s("input_request_id"); if (row(input) != null) return
            phases[request]?.let { retired.add("$request:$it") }; phases[request] = "after-$input"
            add(ChatRow(input, "user", p.s("content"), delivery = "已补充")); return
        }
        if (event == "chat.delta" || event == "chat.final") {
            if (p.s("role").isNotEmpty() && p.s("role") != "assistant") return
            val text = p.s("content"); if (text.isEmpty()) return
            val phase = p.s("segment_id").ifEmpty { p.s("output_phase_id") }.ifEmpty { phases[request] ?: "default" }
            if (p.optBoolean("output_suppressed") || p.s("output_phase_id").isNotEmpty() && "$request:${p.s("output_phase_id")}" in retired) return
            val group = "steps-$request-${epochs[request] ?: 0}"
            if (request !in bodySeen) { bodySeen.add(request); update(group) { it.copy(steps = it.steps.map { step -> if (step.id.startsWith("reasoning-")) step.copy(status = "done") else step }, collapsed = true) } }
            val key = "$request:$phase:${epochs[request] ?: 0}"
            if (row(key) == null) add(ChatRow(key, "assistant", answerId = request))
            update(key) { it.copy(text = if (event == "chat.delta") it.text + text else text) }
            return
        }
        if (request.isNotEmpty() && requestId.isNotEmpty() && request !in listOf(requestId, stopRequestId) && request !in replyOwners) { fallback(event, request, p); return }
        if (p.s("execution_id").isNotEmpty() && request == requestId) executionId = p.s("execution_id")
        when (event) {
            "chat.processing_status" -> if (p.optBoolean("is_processing")) { if (state != "stopping") { state = if (pendingInteraction) "waiting" else "running"; notice = "" } } else {
                rows.filter { it.interaction?.replyId == request }.forEach { changeInteraction(it.interaction!!.id) { value -> value.copy(ended = true) } }
                if (state != "waiting") { state = "idle"; notice = ""; executionId = "" }
                update(requestId) { it.copy(delivery = "") }
            }
            "runtime.accepted" -> accepted(requestId)
            "chat.interrupt_result" -> if (p.optBoolean("success") || p.has("has_active_task") && !p.optBoolean("has_active_task")) { state = "idle"; executionId = ""; notice = "已停止"; expire() } else { state = "unknown"; notice = p.s("message").ifEmpty { "停止未成功，请在电脑端确认" } }
            "chat.error", "execution.error" -> { state = "error"; queuePaused = true; executionId = ""; notice = p.s("message").ifEmpty { p.s("error") }.ifEmpty { "任务执行失败" }; expire() }
            "plan.mode_exited" -> mode = mode.copy(style = "normal")
            "chat.notice" -> notice = p.s("content").ifEmpty { p.s("message") }
            else -> fallback(event, request, p)
        }
    }
    private fun question(request: String, p: JSONObject) {
        val qid = p.s("request_id").ifEmpty { request }; if (row("question-$qid") != null) return
        val questions = p.a("questions").objects().filter { it.s("question").isNotEmpty() }.map { q -> Question(q.s("question"), q.s("header"),
            q.a("options").objects().map { Choice(it.s("label"), it.s("value").ifEmpty { it.s("label") }, it.s("description")) }, q.optBoolean("multi_select"), q.s("card_id")) }
        val interaction = Interaction(qid, p.s("source"), questions, p.a("plan_actions").objects(), p.s("approval_schema"),
            p.optJSONObject("evolution_meta") ?: p.optJSONObject("_evolution_meta"), p.s("plan_approval_kind"), p.s("plan_content"), p.s("plan_language"), p.optJSONObject("swarmflow_meta"),
            error = if (questions.isEmpty()) "已收到确认请求，但缺少问题结构，请在电脑端处理或停止任务。" else "")
        add(ChatRow("question-$qid", "assistant", interaction.planContent, "interaction", answerId = request, interaction = interaction))
    }
    private fun tool(event: String, request: String, p: JSONObject) {
        val value = p.optJSONObject("tool_call") ?: p.optJSONObject("tool_result") ?: p.optJSONObject("tool_update") ?: p
        val toolId = value.s("id").ifEmpty { value.s("tool_call_id") }.ifEmpty { value.s("toolCallId") }.ifEmpty { p.s("tool_call_id") }
        if (event != "chat.reasoning" && toolId.isEmpty()) { fallback(event, request, p); return }
        if (request in bodySeen && row("steps-$request-${epochs[request] ?: 0}")?.steps?.none { it.id == toolId } == true) { bodySeen.remove(request); epochs[request] = (epochs[request] ?: 0) + 1 }
        val key = "steps-$request-${epochs[request] ?: 0}"
        if (row(key) == null) add(ChatRow(key, "assistant", "执行步骤", "steps", answerId = request))
        update(key) { row ->
            val steps = row.steps.toMutableList()
            if (event == "chat.reasoning") {
                val sid = "reasoning-$key"; val i = steps.indexOfFirst { it.id == sid }
                if (i < 0) steps.add(Step(sid, "思考过程", p.s("content"))) else steps[i] = steps[i].copy(text = steps[i].text + p.s("content"))
            } else {
                steps.indices.forEach { if (steps[it].id.startsWith("reasoning-") && steps[it].status == "running") steps[it] = steps[it].copy(status = "done") }
                val i = steps.indexOfFirst { it.id == toolId }
                val status = value.s("status").ifEmpty { p.s("status") }
                val failed = value.has("error") || value.has("success") && !value.optBoolean("success") || status in listOf("error", "failed", "timeout", "timed_out")
                val done = event == "chat.tool_result" && status !in listOf("pending", "running") || status in listOf("completed", "success")
                val text = brief(value.opt("result") ?: value.opt("data") ?: value.opt("rendered_result") ?: value.opt("output") ?: value.opt("error") ?: value.opt("description") ?: value.opt("arguments"))
                val step = (if (i < 0) Step(toolId, value.s("name").ifEmpty { value.s("tool_name") }.ifEmpty { "工具执行" }) else steps[i]).let { it.copy(text = text.ifEmpty { it.text }, status = if (failed) "error" else if (done) "done" else it.status) }
                if (i < 0) steps.add(step) else steps[i] = step
            }
            row.copy(steps = steps, collapsed = request in bodySeen)
        }
    }
    private fun attachment(request: String, p: JSONObject) {
        val files = p.a("files").objects().ifEmpty { p.a("media_items").objects() }.toMutableList()
        if (p.s("audio_base64").isNotEmpty()) files.add(obj("name" to "语音", "mime_type" to p.s("audio_mime").ifEmpty { "audio/wav" }, "base64_data" to p.s("audio_base64")))
        if (files.isEmpty()) { fallback("chat.file", request, p); return }
        val rowId = "attachments-$request-${++counter}"
        val attachments = files.mapIndexedNotNull { index, file ->
            val identity = "$request|${file.s("download_url").ifEmpty { file.s("url") }}|${file.s("name").ifEmpty { file.s("filename") }}|${file.s("base64_data").take(64)}"
            if (!attachmentIds.add(identity)) null else Attachment.from("$rowId-$index", file, origin)
        }
        if (attachments.isNotEmpty()) add(ChatRow(rowId, "assistant", p.s("content"), "attachments", answerId = request, attachments = attachments))
    }
    private fun subagent(event: String, p: JSONObject) {
        val aid = p.s("subagent_id"); if (aid.isEmpty() || p.s("parent_session_id").let { it.isNotEmpty() && it != id }) return
        val key = "subagent-$aid"; if (row(key) == null) add(ChatRow(key, "assistant", kind = "subagent", agent = AgentInfo(aid, aid)))
        update(key) { row ->
            val agent = row.agent!!
            if (event == "chat.subtask_update") {
                val revision = p.optInt("revision"); val at = p.optLong("updated_at", p.optLong("created_at"))
                if (revision < agent.revision || revision == agent.revision && at <= agent.updatedAt) row else {
                    val outcome = p.s("turn_outcome").ifEmpty { p.s("closed_reason") }.ifEmpty { p.s("status") }
                    val status = when {
                        p.s("lifecycle") == "closed" || p.optBoolean("needs_resume") || p.s("status") == "closed" -> "已关闭"
                        p.s("status") in listOf("running", "starting", "pending", "pending_init") -> "执行中"
                        p.s("status") == "idle" -> "空闲"
                        outcome in listOf("failed", "error") -> "失败"
                        outcome in listOf("cancelled", "canceled") -> "已取消"
                        outcome == "completed" -> "已完成"
                        else -> "状态待确认"
                    }
                    row.copy(agent = agent.copy(name = p.s("display_name").ifEmpty { p.s("description") }.ifEmpty { agent.name },
                        task = p.s("task_description").ifEmpty { agent.task }, status = status, running = status == "执行中", revision = revision, updatedAt = at))
                }
            } else {
                val activityId = "$aid|${p.s("task_id")}|${p.s("activity_id").ifEmpty { p.s("seq").ifEmpty { p.s("sequence") } }}"
                if (!agentSeen.add(activityId)) row else {
                    if (agentSeen.size > 2048) agentSeen.clear()
                    val title = when (p.s("kind")) { "thinking" -> "思考"; "tool_call" -> "调用工具"; "tool_result" -> "工具结果"; "error" -> "错误"; "truncated" -> "记录已截断"; else -> p.s("kind").ifEmpty { "活动" } } + p.s("tool_name").let { if (it.isEmpty()) "" else " · $it" }
                    val activity = AgentActivity(activityId, title, brief(p.s("summary").ifEmpty { "收到执行活动" }), p.s("task_id"), p.optInt("seq", p.optInt("sequence")), p.optLong("at_ms"))
                    val activities = (agent.activities + activity).sortedWith(compareBy<AgentActivity> { it.at }.thenBy { it.sequence }).takeLast(200)
                    row.copy(agent = agent.copy(activities = activities, omitted = agent.omitted + if (agent.activities.size + 1 > 200) 1 else 0, latest = "$title：${activity.text}"))
                }
            }
        }
    }
    private fun fallback(event: String, request: String, p: JSONObject) {
        if (event in listOf("runtime.accepted", "chat.processing_status", "chat.output_phase", "connected", "heartbeat", "pong") || usage(event)) return
        val key = "notice-$request-$event"
        val text = "$event\n${brief(p.opt("content") ?: p.opt("message") ?: p.opt("error") ?: "已收到此类型消息，当前版本暂无专用展示。")}"
        if (row(key) == null) add(ChatRow(key, "system", text, "notice")) else update(key) { it.copy(text = text) }
    }
}

internal fun brief(value: Any?): String = when (value) { null -> ""; is String -> value; else -> value.toString() }
    .take(12_000).replace(Regex("data:[^\\s]+;base64,[A-Za-z0-9+/=]+"), "[二进制内容]")
    .replace(Regex("([?&](?:token|download_token|api_key)=)[^\\s&\\\"]+", RegexOption.IGNORE_CASE), "$1[隐藏]")
