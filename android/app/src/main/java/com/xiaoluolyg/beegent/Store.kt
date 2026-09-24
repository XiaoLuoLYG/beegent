package com.xiaoluolyg.beegent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

internal data class HistoryPage(val records: List<JSONObject>, val next: JSONObject?, val snapshot: String, val snapshotEnd: Long)

internal class History(private val socket: SwarmSocket, private val scope: CoroutineScope, private val log: Diagnostics) {
    private data class Pending(val session: String, val rid: String, val cursor: String?, val page: Int, val protocol: String, val expectedTotal: Int?,
        val result: CompletableDeferred<HistoryPage>, val done: CompletableDeferred<Unit>, val records: MutableList<JSONObject> = mutableListOf(),
        val parts: MutableMap<String, MutableMap<Int, JSONObject>> = mutableMapOf(), var abandoned: Boolean = false)
    private val pending = mutableMapOf<String, Pending>()
    private var protocol = "auto"
    fun abandon() { pending.values.forEach { it.abandoned = true; it.records.clear(); it.parts.clear() } }
    fun cancel() { protocol = "auto"; pending.values.toList().forEach { fail(it, Exception("连接已断开，历史加载已取消")) } }
    suspend fun fetch(session: String, next: JSONObject? = null): HistoryPage {
        pending[session]?.takeIf { it.abandoned }?.done?.await()
        require(session !in pending) { "正在加载此会话" }
        val mode = next?.s("protocol")?.ifEmpty { protocol } ?: protocol
        if (protocol != "auto" && mode != protocol) throw Exception("历史分页协议已变化，请重新打开会话")
        val cursor = next?.s("cursor")?.takeIf { it.isNotEmpty() }
        val page = next?.optInt("pageIdx")?.takeIf { it > 0 } ?: 1
        if (next != null && (mode == "cursor" && cursor == null || mode == "page" && page < 2)) throw Exception("历史分页标识无效")
        val rid = id()
        val item = Pending(session, rid, cursor, page, mode, next?.optInt("totalPages"), CompletableDeferred(), CompletableDeferred())
        pending[session] = item
        val params = obj("session_id" to session)
        if (mode != "page") { params.put("cursor", cursor ?: JSONObject.NULL); params.put("limit", 50) }
        if (mode != "cursor") params.put("page_idx", page)
        scope.launch { try { socket.request("history.get", params, rid) } catch (e: Exception) { fail(item, e) } }
        return try { withTimeout(30_000) { item.result.await() } }
        catch (e: Exception) { fail(item, e); throw e }
    }
    private fun fail(item: Pending, error: Exception) {
        if (pending[item.session] !== item) return
        pending.remove(item.session)
        item.result.completeExceptionally(error); item.done.complete(Unit)
        log.record("history.failed", obj("sessionId" to item.session, "requestId" to item.rid, "error" to error.message), "warn")
    }
    fun accept(event: String, p: JSONObject): Boolean {
        if (event in listOf("chat.error", "execution.error")) {
            val item = pending.values.firstOrNull { it.rid == p.s("request_id") } ?: return false
            fail(item, Exception(p.s("error").ifEmpty { p.s("message") }.ifEmpty { "历史读取失败" })); return true
        }
        if (event != "history.message") return false
        val item = pending[p.s("session_id")] ?: return true
        if (p.s("subagent_id").isNotEmpty() || p.s("request_id").let { it.isNotEmpty() && it != item.rid } ||
            p.has("cursor") && !p.isNull("cursor") && p.s("cursor") != item.cursor ||
            p.has("page_idx") && !p.isNull("page_idx") && p.optInt("page_idx") != item.page ||
            item.cursor != null && !p.has("cursor") && p.s("request_id").isEmpty()) return true
        val terminal = p.s("status") == "done" || p.s("content") == "done" || p.s("status") == "error"
        if (item.abandoned) { if (terminal) fail(item, Exception("已离开会话")); return true }
        try {
            if (p.s("status") == "error") throw Exception(p.s("error").ifEmpty { "历史读取失败" })
            if (terminal) {
                if (item.parts.isNotEmpty()) throw Exception("历史消息分片不完整，请重试")
                val mode: String; val next: JSONObject?; val snapshot: String; val end: Long
                if (p.has("has_more") && !p.isNull("has_more")) {
                    if (p.opt("has_more") !is Boolean) throw Exception("历史分页信息不完整")
                    if (item.protocol == "page") throw Exception("历史分页协议已变化，请重新打开会话")
                    mode = "cursor"; val more = p.optBoolean("has_more"); val token = p.s("next_cursor")
                    if (more && (token.isEmpty() || token == item.cursor)) throw Exception("历史分页信息不完整或游标未前进")
                    next = if (more) obj("protocol" to "cursor", "cursor" to token) else null
                    snapshot = p.s("snapshot_id"); end = p.optLong("snapshot_end")
                } else {
                    if (item.protocol == "cursor") throw Exception("历史分页信息不完整")
                    mode = "page"; val index = p.optInt("page_idx"); val total = p.optInt("total_pages")
                    if (index != item.page || total < index || index < 1) throw Exception("历史页码信息不完整")
                    if (item.expectedTotal != null && item.expectedTotal != total) throw Exception("后端历史页数已变化，请重新打开会话")
                    next = if (index < total) obj("protocol" to "page", "pageIdx" to index + 1, "totalPages" to total) else null
                    snapshot = ""; end = 0
                }
                if (protocol != "auto" && protocol != mode) throw Exception("历史分页协议已变化，请重新连接")
                protocol = mode; pending.remove(item.session)
                log.record("history.page_loaded", obj("sessionId" to item.session, "requestId" to item.rid, "state" to mode, "count" to item.records.size))
                item.result.complete(HistoryPage(item.records.toList(), next, snapshot, end)); item.done.complete(Unit)
                return true
            }
            val value = p.opt("message") ?: p.opt("content")
            val record = when (value) { is JSONObject -> value; is String -> JSONObject(value); else -> throw Exception("历史消息格式无效") }
            if (usage(record.s("event_type"))) return true
            val part = record.optJSONObject("_part")
            if (part == null) item.records.add(record) else {
                val key = part.s("record_id"); val index = part.optInt("part_idx", -1); val count = part.optInt("total_parts")
                if (key.isEmpty() || count !in 1..1024 || index !in 0 until count) throw Exception("历史分片序号无效")
                val bucket = item.parts.getOrPut(key) { mutableMapOf() }; bucket[index] = record
                if (bucket.size == count) {
                    val first = bucket[0] ?: throw Exception("历史分片不一致")
                    val text = (0 until count).joinToString("") { chunk ->
                        val piece = bucket[chunk] ?: throw Exception("历史分片不一致")
                        if (piece.o("_part").optInt("total_parts") != count) throw Exception("历史分片不一致")
                        piece.s("content")
                    }
                    first.put("content", text); first.remove("_part"); item.records.add(first); item.parts.remove(key)
                }
            }
        } catch (e: Exception) { fail(item, e) }
        return true
    }
}

internal data class Answer(val question: String, val selected: List<String>, val custom: String, val cardId: String)

internal class SwarmStore(private val scope: CoroutineScope, val log: Diagnostics) {
    val socket = SwarmSocket(log)
    private val history = History(socket, scope, log)
    var endpoint by mutableStateOf("")
    var origin by mutableStateOf("")
    var current by mutableStateOf<Session?>(null)
    val remote = mutableStateListOf<JSONObject>()
    var listTotal by mutableStateOf(0)
    var listLoading by mutableStateOf(false)
    var listError by mutableStateOf("")
    var creating by mutableStateOf(false)
    var error by mutableStateOf("")
    var globalNotice by mutableStateOf("")
    private var epoch = 0
    private var listGeneration = 0
    private val requests = mutableMapOf<String, String>()
    private val supplements = mutableMapOf<String, String>()
    private var stopTimer: Job? = null
    init {
        socket.onState = { state, reason -> if (state == "closed") { epoch++; history.cancel(); clearList(); leave(); error = reason } }
        socket.onEvent = { event, p, frame -> receive(event, p, frame) }
    }
    private fun receive(event: String, p: JSONObject, frame: JSONObject) {
        if (history.accept(event, p)) return
        if (event == "task.global_running") return
        if (usage(event)) return
        val session = current
        val owner = p.s("parent_session_id").ifEmpty { p.s("session_id") }.ifEmpty { requests[p.s("request_id")] ?: "" }
        log.record("message.route", obj("event" to event, "sessionId" to owner, "target" to if (session?.id == owner) "current_session" else "unloaded_or_global"))
        if (session == null || owner != session.id) {
            if (owner.isEmpty() && event !in listOf("connected", "heartbeat", "pong")) globalNotice = "收到服务通知：$event"
            return
        }
        val input = if (event == "chat.input_received") p.s("input_request_id") else p.s("request_id")
        supplements[input]?.let { taskId ->
            if (event in listOf("runtime.accepted", "chat.input_received")) acceptSupplement(session, taskId)
            if (event == "chat.error") failSupplement(session, taskId, p.s("error").ifEmpty { "补充失败" }, p.s("code") != "SESSION_INPUT_DELIVERY_UNKNOWN")
            if (event != "chat.input_received") return
        }
        session.apply(event, p, frame)
        if (event == "chat.error" || event == "execution.error") {
            session.rows.filter { it.interaction?.replyId == p.s("request_id") }.forEach { row -> session.changeInteraction(row.interaction!!.id) { it.copy(status = "failed", error = p.s("message").ifEmpty { "回复未完成" }, executePlan = false) } }
        }
        if (session.state != "stopping") stopTimer?.cancel()
        continuePlan(session)
        if (event == "chat.processing_status" && !p.optBoolean("is_processing")) drain(session)
    }
    fun clearList() { listGeneration++; remote.clear(); listTotal = 0; listLoading = false; listError = "" }
    fun leave() { history.abandon(); stopTimer?.cancel(); current?.clear(); current = null; requests.clear(); supplements.clear(); error = "" }
    suspend fun connect(settings: ConnectionSettings) {
        if (settings.endpoint != endpoint || settings.downloadOrigin != origin) {
            if (socket.state.value == "connecting") throw Exception("正在连接，请稍后再切换地址")
            socket.close(); leave(); endpoint = settings.endpoint; origin = settings.downloadOrigin
        }
        error = ""; globalNotice = ""; socket.connect(endpoint)
    }
    fun disconnect() { socket.close() }
    suspend fun listSessions(more: Boolean = false) {
        if (socket.state.value != "open" || listLoading) return
        val generation = listGeneration; val connection = epoch; val offset = if (more) remote.size else 0
        listLoading = true; listError = ""
        try {
            val result = socket.request("session.list", obj("limit" to 30, "offset" to offset))
            if (generation != listGeneration || connection != epoch) return
            val sessions = result.optJSONArray("sessions") ?: throw Exception("会话列表格式无效")
            if (!more) remote.clear()
            sessions.objects().forEach { item -> if (item.s("session_id").isNotEmpty() && remote.none { it.s("session_id") == item.s("session_id") }) remote.add(item) }
            listTotal = result.optInt("total", remote.size)
        } catch (e: Exception) { if (generation == listGeneration && connection == epoch) listError = e.message ?: "会话列表加载失败" }
        finally { if (generation == listGeneration && connection == epoch) listLoading = false }
    }
    suspend fun openSession(info: JSONObject) {
        require(socket.state.value == "open") { "请先连接电脑" }
        leave()
        val session = Session(info.s("session_id"), endpoint, origin, Mode.parse(info.s("mode"), info.s("work_mode")), info.s("title").ifEmpty { "未命名会话" })
        current = session; loadHistory(session, false)
    }
    suspend fun olderHistory() { current?.takeIf { it.historyNext != null }?.let { loadHistory(it, true) } }
    private suspend fun loadHistory(session: Session, older: Boolean) {
        if (session.historyLoading || socket.state.value != "open") return
        val connection = epoch; val revision = session.liveRevision; session.historyLoading = true; error = ""
        try {
            val metadata = socket.request("session.get_metadata", obj("session_id" to session.id))
            if (current !== session || connection != epoch) return
            val next = if (older) session.historyNext else null
            val page = history.fetch(session.id, next)
            if (current !== session || connection != epoch) return
            if (older && (page.snapshot != session.historySnapshot || page.snapshotEnd != session.historySnapshotEnd)) throw Exception("后端历史已变化，请重新打开会话")
            if (!older && !metadata.optBoolean("is_processing") && revision == session.liveRevision && !session.pendingInteraction && session.state in listOf("idle", "error", "unknown")) session.rows.clear()
            session.historyNext = page.next; session.historySnapshot = page.snapshot; session.historySnapshotEnd = page.snapshotEnd
            session.restore(if (older) page.records + session.historyRecords else page.records)
            session.title = metadata.s("title").ifEmpty { session.title }
            if (session.requestId.isEmpty()) {
                session.mode = Mode.parse(metadata.s("mode").ifEmpty { session.mode.wire() }, metadata.s("work_mode"))
                session.state = if (metadata.optBoolean("is_processing")) "unknown" else "idle"
                session.notice = if (metadata.optBoolean("is_processing")) "此会话正在其他客户端执行，可停止或稍后重新打开查看结果" else ""
            }
        } catch (e: Exception) { if (current === session && connection == epoch) { error = e.message ?: "历史加载失败"; if (!session.historyLoaded) session.notice = "历史加载失败，请重试" } }
        finally { if (current === session && connection == epoch) session.historyLoading = false }
    }
    suspend fun create(mode: Mode) {
        if (creating) return
        require(socket.state.value == "open") { "请先连接电脑" }
        val connection = epoch; val target = endpoint; creating = true; error = ""
        try {
            val payload = socket.request("session.create", obj("mode" to mode.wire(), "work_mode" to mode.profile, "create_token" to id()))
            val sid = payload.s("session_id").ifEmpty { payload.s("sessionId") }; if (sid.isEmpty()) throw Exception("服务端未返回会话 ID")
            if (connection != epoch || target != endpoint) throw Exception("连接已切换")
            leave(); current = Session(sid, endpoint, origin, mode)
        } finally { creating = false }
    }
    suspend fun sendDraft(draft: String, mode: Mode) {
        if (draft.isBlank()) return
        if (current == null) create(mode)
        val session = current ?: return
        session.draft = draft
        send()
    }
    suspend fun send() {
        val session = current ?: return; val content = session.draft.trim(); if (content.isEmpty()) return
        if (session.historyLoading || !session.historyLoaded && session.notice == "历史加载失败，请重试") throw Exception("请先完成历史加载")
        require(socket.state.value == "open") { "请先连接电脑" }
        session.draft = ""
        if (session.state !in listOf("idle", "error") || session.queue.isNotEmpty() || session.queuePaused) {
            session.queue.add(Queued(id(), content)); drain(session)
        } else sendContent(session, content)
    }
    private suspend fun sendContent(session: Session, content: String) {
        if (current !== session) return
        val rid = id(); requests[rid] = session.id; session.begin(rid, content)
        try { socket.request("chat.send", obj("session_id" to session.id, "mode" to session.mode.wire(), "work_mode" to session.mode.profile, "content" to content), rid); if (current === session) session.accepted(rid) }
        catch (e: Exception) { if (current === session) { session.queuePaused = true; session.failed(rid, e.message ?: "发送失败", (e as? RequestFailure)?.definitive == true) } }
    }
    private fun drain(session: Session) {
        if (current !== session || socket.state.value != "open" || session.state != "idle" || session.queuePaused || session.pendingInteraction || session.queue.any { it.status != "queued" }) return
        val next = session.queue.firstOrNull() ?: return; session.queue.removeAt(0)
        scope.launch { sendContent(session, next.content) }
    }
    fun toggleQueue() { current?.let { it.queuePaused = !it.queuePaused; if (!it.queuePaused && it.state == "error") it.state = "idle"; drain(it) } }
    fun removeQueued(qid: String) { current?.let { session -> session.queue.removeAll { it.id == qid && it.status != "sending" }; drain(session) } }
    fun editQueued(qid: String) { current?.let { session -> val item = session.queue.firstOrNull { it.id == qid && it.status in listOf("queued", "failed") } ?: return; require(session.draft.isBlank()) { "请先发送或清空输入框中的草稿" }; session.draft = item.content; session.queue.remove(item) } }
    fun moveQueued(qid: String, offset: Int) { current?.let { session -> val i = session.queue.indexOfFirst { it.id == qid }; val j = i + offset; if (i >= 0 && j in session.queue.indices && session.queue[i].status != "sending" && session.queue[j].status != "sending") { val task = session.queue[i]; session.queue[i] = session.queue[j]; session.queue[j] = task } } }
    fun retryQueued(qid: String) { current?.let { session -> val i = session.queue.indexOfFirst { it.id == qid && it.status == "failed" }; if (i >= 0) { session.queue[i] = session.queue[i].copy(status = "queued", error = ""); drain(session) } } }
    fun canSupplement(session: Session) = socket.state.value == "open" && session.mode.role == "agent" && session.state == "running" && session.executionId.isNotEmpty()
    suspend fun supplement(qid: String) {
        val session = current ?: return; require(canSupplement(session)) { "当前执行暂不支持补充，请保留在队列中" }
        val i = session.queue.indexOfFirst { it.id == qid && it.status in listOf("queued", "failed") }; if (i < 0) return
        val rid = id(); val task = session.queue[i]; session.queue[i] = task.copy(status = "sending", error = "", requestId = rid)
        requests[rid] = session.id; supplements[rid] = qid
        try { socket.request("chat.send", obj("session_id" to session.id, "mode" to session.mode.wire(), "work_mode" to session.mode.profile,
            "content" to task.content, "input_mode" to "steer", "expected_execution_id" to session.executionId), rid, true); acceptSupplement(session, qid) }
        catch (e: Exception) { failSupplement(session, qid, e.message ?: "补充失败", (e as? RequestFailure)?.definitive == true) }
    }
    private fun acceptSupplement(session: Session, qid: String) { val item = session.queue.firstOrNull { it.id == qid } ?: return; supplements.remove(item.requestId); session.queue.remove(item); drain(session) }
    private fun failSupplement(session: Session, qid: String, message: String, definitive: Boolean) { val i = session.queue.indexOfFirst { it.id == qid }; if (i < 0) return; val task = session.queue[i]; supplements.remove(task.requestId); session.queue[i] = task.copy(status = if (definitive) "failed" else "unknown", error = message); session.queuePaused = true }
    suspend fun stop() {
        val session = current ?: return; if (socket.state.value != "open" || session.state == "stopping") return
        session.queuePaused = true; val rid = id(); session.stopRequestId = rid; requests[rid] = session.id
        session.state = "stopping"; session.notice = "正在请求停止…"
        stopTimer?.cancel(); stopTimer = scope.launch { delay(30_000); if (current === session && session.state == "stopping" && session.stopRequestId == rid) { session.state = "unknown"; session.notice = "尚未收到停止确认，请在电脑端核实。" } }
        try { socket.request("chat.interrupt", obj("session_id" to session.id, "mode" to session.mode.wire(), "work_mode" to session.mode.profile, "intent" to "cancel"), rid) }
        catch (e: Exception) { if (current === session) { stopTimer?.cancel(); session.state = "unknown"; session.notice = e.message ?: "停止结果待确认" } }
    }
    suspend fun answer(qid: String, answers: List<Answer>) {
        val session = current ?: return; val interaction = session.interaction(qid) ?: throw Exception("此确认已失效")
        require(interaction.status in listOf("pending", "failed")) { "此确认已提交或已失效" }
        require(answers.size == interaction.questions.size && answers.isNotEmpty()) { "请回答全部问题" }
        val structured = interaction.planKind == "plan_approval" && interaction.planActions.isNotEmpty()
        if (structured && answers.size != 1) throw Exception("计划审批问题结构无效")
        val items = JSONArray()
        answers.forEachIndexed { i, answer ->
            val question = interaction.questions[i]
            require(answer.question == question.text && (answer.selected.isNotEmpty() || answer.custom.isNotBlank())) { "请选择选项或填写补充内容" }
            require(question.multiple || answer.selected.size <= 1) { "此问题只能选择一项" }
            if (structured) {
                val action = interaction.planActions.firstOrNull { it.s("value") == answer.selected.singleOrNull() }
                require(action != null && action.s("value") in listOf("plan_execute", "plan_skip", "plan_revise")) { "请选择服务端提供的计划操作" }
                require(!(action.s("value") == "plan_revise" || action.s("requires_input") == "yes") || answer.custom.isNotBlank()) { "请填写计划修改意见" }
                require(!(action.s("value") == "plan_skip" || action.s("requires_input") == "empty") || answer.custom.isBlank()) { "已有修改意见，请选择下一步" }
            } else require(answer.selected.all { value -> question.choices.any { it.value == value } }) { "选项已变化，请重新选择" }
            items.put(obj("question" to answer.question, "selected_options" to JSONArray(answer.selected), "custom_input" to answer.custom.trim(), "card_id" to question.cardId))
        }
        if (socket.state.value != "open") throw Exception("请先连接电脑")
        val params = obj("session_id" to session.id, "request_id" to interaction.id, "answers" to items, "source" to interaction.source)
        if (interaction.approvalSchema.isNotEmpty()) params.put("approval_schema", interaction.approvalSchema)
        interaction.evolution?.let { params.put("evolution_meta", it) }
        var method = "chat.user_answer"; var runtime = false
        if (interaction.source == "swarmflow_human") {
            val flow = interaction.flow ?: throw Exception("缺少流程回复标识")
            if (flow.s("run_id").isEmpty() || flow.s("correlation_id").isEmpty()) throw Exception("缺少流程回复标识")
            method = "chat.swarmflow_reply"; params.remove("request_id"); params.remove("answers"); params.remove("source")
            params.remove("approval_schema"); params.remove("evolution_meta")
            params.put("run_id", flow.s("run_id")); params.put("correlation_id", flow.s("correlation_id")); params.put("answer", answers.joinToString("\n") { it.custom.ifBlank { it.selected.joinToString("、") } })
        } else if (interaction.source in listOf("permission_interrupt", "confirm_interrupt", "ask_user_interrupt", "evolution_interrupt") || interaction.source == "skill_evolution_approval" && interaction.evolution?.s("approval_transport") == "interrupt") {
            method = "chat.send"; params.put("query", ""); params.put("mode", session.mode.wire())
            if (interaction.planKind.isNotEmpty()) params.put("plan_approval_kind", interaction.planKind)
            if (interaction.planContent.isNotEmpty()) params.put("plan_content", interaction.planContent)
            if (interaction.planLanguage.isNotEmpty()) params.put("plan_language", interaction.planLanguage)
            runtime = interaction.source == "permission_interrupt" && answers.first().cardId.isNotEmpty()
        } else if (interaction.source in listOf("auto_harness", "activate_confirm")) throw Exception("此工作流暂需在电脑端确认")
        val rid = id(); requests[rid] = session.id; session.replyOwners.add(rid)
        val execute = structured && answers.any { "plan_execute" in it.selected }
        session.changeInteraction(qid) { it.copy(replyId = rid, status = "sending", error = "", ended = false, executePlan = execute) }
        try {
            socket.request(method, params, rid, runtime)
            if (current !== session || session.interaction(qid)?.status != "sending") return
            session.changeInteraction(qid) { it.copy(status = "submitted") }
            if (execute) session.mode = session.mode.copy(style = "normal")
            session.state = if (session.pendingInteraction) "waiting" else if (session.interaction(qid)?.ended == true) "idle" else "running"
            session.notice = ""; continuePlan(session); drain(session)
        } catch (e: Exception) {
            if (current !== session || session.interaction(qid)?.status == "expired") return
            val definitive = (e as? RequestFailure)?.definitive == true
            session.changeInteraction(qid) { it.copy(status = if (definitive) "failed" else "unknown", error = e.message ?: "回复失败", executePlan = false) }
            session.queuePaused = true; session.state = if (definitive) "waiting" else "unknown"
            session.notice = if (definitive) "回复失败，可在卡片上重试" else "回复是否送达尚不确定，请在电脑端核实，避免重复审批"
        }
    }
    private fun continuePlan(session: Session) {
        if (current !== session || session.pendingInteraction || socket.state.value != "open" || session.state == "stopping") return
        session.rows.filter { it.interaction?.let { value -> value.executePlan && value.status == "submitted" && value.ended } == true }.forEach { row ->
            session.changeInteraction(row.interaction!!.id) { it.copy(executePlan = false) }; session.mode = session.mode.copy(style = "normal"); session.state = "idle"
            scope.launch { sendContent(session, "请执行已批准的计划。") }
        }
    }
    fun close() { socket.close(); log.close() }
}
