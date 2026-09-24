package com.xiaoluolyg.beegent

import android.os.Bundle
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Honey = Color(0xFFFFD452)
private val Ink = Color(0xFF24231F)
private val Soft = Color(0xFFFFF7E5)
private val Page = Color(0xFFFFFCF5)
private val Muted = Color(0xFF77736B)

class MainActivity : ComponentActivity() {
    private lateinit var store: SwarmStore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val log = Diagnostics(this)
        store = SwarmStore(lifecycleScope, log)
        val attachments = Attachments(this, log)
        log.record("app.started", obj("reason" to "diagnostics enabled; text retained, credentials and binary redacted"))
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Ink, background = Page, surface = Color.White, onSurface = Ink)) {
            Beegent(this, store, attachments)
        } }
    }
    override fun onDestroy() { store.close(); super.onDestroy() }
}

@Composable
private fun Beegent(activity: ComponentActivity, store: SwarmStore, attachments: Attachments) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val session = store.current
    var address by remember { mutableStateOf("192.168.43.217") }
    var messagePort by remember { mutableStateOf("") }
    var downloadPort by remember { mutableStateOf("") }
    var connectionPanel by remember { mutableStateOf(false) }
    var logsPanel by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf("agent") }
    var profile by remember { mutableStateOf("work") }
    var style by remember { mutableStateOf("normal") }
    var draft by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<Pair<Attachment, String>?>(null) }
    var pendingSave by remember { mutableStateOf<Attachment?>(null) }
    var removeUnknown by remember { mutableStateOf<Queued?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file = pendingSave; pendingSave = null
        if (uri != null && file != null) scope.launch { try { attachments.save(file, uri); Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show() }
            catch (e: Exception) { store.error = "附件保存失败：${e.message}" } }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")) { uri ->
        if (uri != null) scope.launch { try { withContext(Dispatchers.IO) { activity.contentResolver.openOutputStream(uri, "wt")?.use { it.write(store.log.export().toByteArray()) } ?: throw Exception("无法写入文件") }; Toast.makeText(context, "通信日志已导出", Toast.LENGTH_SHORT).show() }
            catch (e: Exception) { store.error = "日志导出失败：${e.message}" } }
    }
    fun run(action: suspend () -> Unit) { scope.launch { try { action() } catch (e: Exception) { store.error = e.message ?: "操作失败" } } }
    fun attachmentAction(file: Attachment, action: String) {
        store.log.record("ui.attachment.tap", obj("sessionId" to session?.id, "fileId" to file.id, "action" to action))
        when (action) {
            "preview", "render" -> preview = file to action
            "save" -> { pendingSave = file; save.launch(file.name.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_").takeLast(120)) }
            else -> run { attachments.handoff(file, action == "share") }
        }
    }
    LaunchedEffect(session?.id, session?.draft) { draft = session?.draft ?: if (session == null) draft else "" }
    LaunchedEffect(drawer.currentValue) { if (drawer.currentValue == DrawerValue.Closed) store.clearList() }
    LaunchedEffect(store.socket.state.value) { if (store.socket.state.value == "closed") { draft = ""; address = "192.168.43.217"; messagePort = ""; downloadPort = "" } }

    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(modifier = Modifier.width(310.dp), drawerContainerColor = Page) {
            Column(Modifier.fillMaxHeight().padding(16.dp)) {
                Text("会话", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { scope.launch { drawer.close(); store.leave(); draft = ""; role = "agent"; profile = "work"; style = "normal" } },
                    enabled = store.socket.state.value == "open" && !store.creating, colors = ButtonDefaults.buttonColors(containerColor = Honey, contentColor = Ink), modifier = Modifier.fillMaxWidth()) { Text("＋ 新建会话") }
                Spacer(Modifier.height(8.dp))
                when {
                    store.socket.state.value != "open" -> Text("请先连接电脑", color = Muted)
                    store.listLoading && store.remote.isEmpty() -> CircularProgressIndicator()
                }
                if (store.listError.isNotEmpty()) Text(store.listError, color = Ink)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(store.remote, key = { it.s("session_id") }) { info ->
                        Column(Modifier.fillMaxWidth().clickable { scope.launch { drawer.close(); run { store.openSession(info) } } }.padding(vertical = 10.dp)) {
                            Text(info.s("title").ifEmpty { "未命名会话" }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(if (info.optBoolean("is_processing")) "状态待确认" else Mode.parse(info.s("mode"), info.s("work_mode")).wire(), color = Muted, fontSize = 11.sp)
                        }
                        HorizontalDivider()
                    }
                    if (store.remote.size < store.listTotal) item {
                        TextButton(onClick = { run { store.listSessions(true) } }, enabled = !store.listLoading) { Text(if (store.listLoading) "正在加载…" else "加载更多会话") }
                    }
                }
                TextButton(onClick = { logsPanel = true }) { Text("通信日志") }
            }
        }
    }) {
        Column(Modifier.fillMaxSize().background(Page).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scope.launch { drawer.open(); run { store.listSessions() } } }) { Text("☰", color = Ink, fontSize = 22.sp) }
                Text(session?.title ?: "beegent", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { connectionPanel = true }) {
                    Text(if (store.socket.state.value == "open") "● 已连接 ›" else if (store.socket.state.value == "connecting") "◌ 连接中…" else "○ 未连接 ›", color = Ink, fontSize = 12.sp)
                }
            }
            if (store.error.isNotEmpty()) Text(store.error, Modifier.fillMaxWidth().background(Soft).padding(10.dp), fontSize = 12.sp, color = Ink)
            if (store.globalNotice.isNotEmpty()) Text(store.globalNotice, Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = Muted)
            if (session == null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ModeButton(if (role == "agent") "Agent · 单代理" else "Team · 多代理", Modifier.weight(1f)) { role = if (role == "agent") "team" else "agent" }
                    ModeButton(if (profile == "work") "Work · 通用" else "Code · 编程", Modifier.weight(1f)) { profile = if (profile == "work") "code" else "work" }
                    ModeButton(if (style == "normal") "Normal · 直接" else "Plan · 计划", Modifier.weight(1f)) { style = if (style == "normal") "plan" else "normal" }
                }
            }
            if (session != null && session.todos.isNotEmpty()) TodoPanel(session)
            if (session != null && session.queue.isNotEmpty()) QueuePanel(session, store, onError = { store.error = it }, onRemoveUnknown = { removeUnknown = it })
            val listState = rememberLazyListState()
            var following by remember(session?.id) { mutableStateOf(true) }
            val rows = session?.rows ?: emptyList()
            LaunchedEffect(rows.size, rows.lastOrNull()?.text, following) {
                if (following && rows.isNotEmpty()) listState.animateScrollToItem(rows.size + if (session?.historyNext != null) 1 else 0)
            }
            LaunchedEffect(listState.isScrollInProgress) {
                if (listState.isScrollInProgress && listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index != listState.layoutInfo.totalItemsCount - 1) following = false
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (session?.historyNext != null) item {
                    TextButton(onClick = { run { store.olderHistory() } }, enabled = !session.historyLoading, modifier = Modifier.fillMaxWidth()) { Text(if (session.historyLoading) "正在加载…" else "查看更早消息") }
                }
                if (rows.isEmpty()) item { Text(if (store.socket.state.value == "open") "输入消息，开始行动" else "连接电脑上的 Swarm，开始对话", color = Muted, modifier = Modifier.fillMaxWidth().padding(top = 80.dp)) }
                items(rows, key = { it.id }) { row -> MessageCard(row, session?.id ?: "", attachments, onAnswer = { qid, answers -> run { store.answer(qid, answers) } }, onAttachment = ::attachmentAction) }
            }
            if (session?.notice?.isNotEmpty() == true) Text(session.notice, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = draft, onValueChange = { draft = it; session?.draft = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息…") }, maxLines = 4, shape = RoundedCornerShape(18.dp))
                if (session != null && session.state !in listOf("idle", "error")) {
                    Button(onClick = { run { store.stop() } }, enabled = store.socket.state.value == "open" && session.state != "stopping",
                        colors = ButtonDefaults.buttonColors(containerColor = Honey, contentColor = Ink)) { Text("停止") }
                }
                Button(onClick = { val value = draft; following = true; run { store.sendDraft(value, Mode(role, profile, style)); draft = store.current?.draft ?: "" } },
                    enabled = draft.isNotBlank() && store.socket.state.value == "open" && !store.creating,
                    colors = ButtonDefaults.buttonColors(containerColor = Honey, contentColor = Ink)) {
                    Text(if (session != null && session.state !in listOf("idle", "error")) "排队" else "发送")
                }
            }
        }
    }
    if (connectionPanel) AlertDialog(onDismissRequest = { connectionPanel = false }, title = { Text("连接你的 Swarm") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("手机与电脑连接同一局域网", color = Muted, fontSize = 12.sp)
            OutlinedTextField(address, { address = it }, label = { Text("服务器 IP") }, enabled = store.socket.state.value == "closed", singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(messagePort, { messagePort = it }, Modifier.weight(1f), label = { Text("消息端口") }, placeholder = { Text("29000") }, enabled = store.socket.state.value == "closed", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(downloadPort, { downloadPort = it }, Modifier.weight(1f), label = { Text("下载端口") }, placeholder = { Text("25173") }, enabled = store.socket.state.value == "closed", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        }
    }, confirmButton = { Button(onClick = {
        if (store.socket.state.value == "open") { store.disconnect(); connectionPanel = false }
        else run { store.connect(ConnectionSettings.parse(address, messagePort, downloadPort)); connectionPanel = false }
    }, enabled = store.socket.state.value != "connecting", colors = ButtonDefaults.buttonColors(containerColor = Honey, contentColor = Ink)) { Text(if (store.socket.state.value == "open") "断开连接" else "连接") } }, dismissButton = { TextButton(onClick = { connectionPanel = false }) { Text("关闭") } })
    if (logsPanel) AlertDialog(onDismissRequest = { logsPanel = false }, title = { Text("通信日志") }, text = { Text(store.log.summary() + "\n日志保存在本机，导出时会脱敏。", color = Muted) },
        confirmButton = { TextButton(onClick = { export.launch("beegent-communication-${System.currentTimeMillis()}.jsonl") }) { Text("导出") } },
        dismissButton = { Row { TextButton(onClick = { run { withContext(Dispatchers.IO) { store.log.clear() }; logsPanel = false } }) { Text("清空") }; TextButton(onClick = { logsPanel = false }) { Text("关闭") } } })
    removeUnknown?.let { task -> AlertDialog(onDismissRequest = { removeUnknown = null }, title = { Text("移除待确认消息") }, text = { Text("电脑可能已经收到这条补充。移除只清理手机队列，不会撤回；请勿再次发送相同内容。") },
        confirmButton = { TextButton(onClick = { store.removeQueued(task.id); removeUnknown = null }) { Text("移除") } }, dismissButton = { TextButton(onClick = { removeUnknown = null }) { Text("保留") } }) }
    preview?.let { (file, action) -> Preview(file, action, attachments, onClose = { preview = null }, onAction = ::attachmentAction) }
}

@Composable private fun ModeButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 3.dp), colors = ButtonDefaults.buttonColors(containerColor = Honey, contentColor = Ink)) { Text(label, fontSize = 10.sp, maxLines = 1) }
}

@Composable private fun TodoPanel(session: Session) {
    var expanded by remember(session.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        val running = session.todos.firstOrNull { it.status == "in_progress" }
        val title = "任务进度 ${session.todos.count { it.status == "completed" }}/${session.todos.size}" + (running?.let { " · ${it.active}" } ?: "")
        TextButton(onClick = { expanded = !expanded }, colors = ButtonDefaults.textButtonColors(contentColor = Ink), modifier = Modifier.fillMaxWidth().background(Honey, RoundedCornerShape(10.dp))) { Text((if (expanded) "⌄ " else "› ") + title, maxLines = 1) }
        if (expanded) Column(Modifier.fillMaxWidth().background(Soft).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            session.todos.forEach { item -> Text((when (item.status) { "completed" -> "✓ 已完成"; "in_progress" -> "◌ 进行中"; "cancelled" -> "− 已取消"; else -> "○ 待开始" }) + "  " + if (item.status == "in_progress") item.active else item.content, fontSize = 12.sp) }
        }
    }
}

@Composable private fun QueuePanel(session: Session, store: SwarmStore, onError: (String) -> Unit, onRemoveUnknown: (Queued) -> Unit) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("消息队列 · ${session.queue.size}" + if (session.queuePaused) " · 已暂停" else "", Modifier.weight(1f), fontSize = 12.sp)
            TextButton(onClick = store::toggleQueue) { Text(if (session.queuePaused) "恢复队列" else "暂停队列", fontSize = 11.sp) }
        }
        Column(Modifier.heightIn(max = 170.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            session.queue.forEachIndexed { index, task ->
                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp)).padding(8.dp)) {
                    Text(task.content, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                    Text((when (task.status) { "sending" -> "等待接收确认"; "failed" -> "补充未成功"; "unknown" -> "结果待确认，请勿重发"; else -> "等待发送" }) + if (task.error.isNotEmpty()) " · ${task.error}" else "", color = Muted, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (session.mode.role == "agent") SmallAction("补充", store.canSupplement(session) && task.status in listOf("queued", "failed")) { scope.launch { try { store.supplement(task.id) } catch (e: Exception) { onError(e.message ?: "补充失败") } } }
                        if (task.status == "failed") SmallAction("重新排队") { store.retryQueued(task.id) }
                        SmallAction("编辑", task.status in listOf("queued", "failed")) { try { store.editQueued(task.id) } catch (e: Exception) { onError(e.message ?: "编辑失败") } }
                        SmallAction("移除", task.status != "sending") { if (task.status == "unknown") onRemoveUnknown(task) else store.removeQueued(task.id) }
                        SmallAction("↑", index > 0) { store.moveQueued(task.id, -1) }
                    }
                }
            }
        }
    }
}

@Composable private fun SmallAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp), modifier = Modifier.height(28.dp)) { Text(label, fontSize = 10.sp) }
}

@Composable private fun MessageCard(row: ChatRow, sessionId: String, attachments: Attachments, onAnswer: (String, List<Answer>) -> Unit, onAttachment: (Attachment, String) -> Unit) {
    var expanded by remember(row.id) { mutableStateOf(!row.collapsed) }
    Column(Modifier.fillMaxWidth().padding(start = if (row.role == "user") 40.dp else 0.dp, end = if (row.role == "user") 0.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (row.role == "user") "你" else if (row.kind == "notice") "收到消息" else "beegent", fontSize = 11.sp, color = Muted)
        if (row.kind == "steps") {
            TextButton(onClick = { expanded = !expanded }, colors = ButtonDefaults.textButtonColors(contentColor = Ink), modifier = Modifier.background(Honey, RoundedCornerShape(12.dp))) {
                Text("${if (expanded) "⌄" else "›"} 思考与工具 · ${row.steps.size} 步" + if (row.steps.any { it.status == "error" }) " · 有失败" else "", fontSize = 12.sp)
            }
            if (expanded) row.steps.forEach { step -> Column(Modifier.fillMaxWidth().background(Soft, RoundedCornerShape(8.dp)).padding(8.dp)) {
                Text((if (step.status == "running") "◌ " else if (step.status == "done") "✓ " else "! ") + step.title, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text(step.text.ifEmpty { "执行中…" }, color = Muted, fontSize = 12.sp)
            } }
        } else if (row.kind == "subagent" && row.agent != null) {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text("子代理 · ${row.agent.name} · ${row.agent.status}", fontWeight = FontWeight.Medium)
                if (row.agent.task.isNotEmpty()) Text(row.agent.task, color = Muted, maxLines = if (expanded) 30 else 2)
                Text(row.agent.latest.ifEmpty { "等待执行活动…" }, color = Muted, fontSize = 12.sp)
                TextButton(onClick = { expanded = !expanded }) { Text("${if (expanded) "⌄ 收起" else "› 执行记录"} · ${row.agent.activities.size}") }
                if (expanded) { if (row.agent.omitted > 0) Text("仅保留最近 200 条活动", fontSize = 11.sp); row.agent.activities.forEach { Text("${it.title}：${it.text}", fontSize = 12.sp, color = Muted) } }
            }
        } else {
            Column(Modifier.fillMaxWidth().background(if (row.role == "user") Soft else Color.White, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (row.kind) {
                    "interaction" -> row.interaction?.let { QuestionCard(it, onAnswer) }
                    "attachments" -> { if (row.text.isNotEmpty()) Text(row.text); row.attachments.forEach { AttachmentCard(it, attachments, onAttachment) } }
                    else -> if (row.role == "assistant" && row.kind == "text") MarkdownText(row.text) else Text(row.text.ifEmpty { "…" }, fontSize = 15.sp, color = Ink)
                }
            }
        }
        if (row.delivery.isNotEmpty()) Text(row.delivery, Modifier.fillMaxWidth(), color = Muted, fontSize = 11.sp)
    }
}

@Composable private fun QuestionCard(value: Interaction, onAnswer: (String, List<Answer>) -> Unit) {
    val selections = remember(value.id) { mutableStateListOf<Set<String>>().apply { addAll(value.questions.map { emptySet() }) } }
    val inputs = remember(value.id) { mutableStateListOf<String>().apply { addAll(value.questions.map { "" }) } }
    val editable = value.status in listOf("pending", "failed")
    Text(if (value.planKind == "plan_approval") "计划审批" else "等待你的回复", fontWeight = FontWeight.Bold)
    if (value.planContent.isNotEmpty()) MarkdownText(value.planContent)
    val structured = value.planKind == "plan_approval" && value.planActions.isNotEmpty() && value.questions.size == 1
    if (structured) {
        val feedback = inputs.firstOrNull() ?: ""
        OutlinedTextField(feedback, { if (inputs.isNotEmpty()) inputs[0] = it }, label = { Text("计划修改意见") }, enabled = editable, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val execute = value.planActions.firstOrNull { it.s("kind") == "execute" }
            val other = value.planActions.firstOrNull { it.s("kind") == if (feedback.isBlank()) "skip" else "revise" }
            listOfNotNull(execute, other).forEach { action -> Button(onClick = { onAnswer(value.id, listOf(Answer(value.questions.first().text, listOf(action.s("value")), if (action.s("kind") == "revise") feedback else "", value.questions.first().cardId))) }, enabled = editable, colors = ButtonDefaults.buttonColors(containerColor = Honey, contentColor = Ink)) { Text(action.s("label").ifEmpty { action.s("kind") }) } }
        }
    } else value.questions.forEachIndexed { index, question ->
        Text((if (question.header.isNotEmpty()) "${question.header} · " else "") + question.text)
        question.choices.forEach { choice -> TextButton(onClick = {
            val old = selections[index]; selections[index] = if (choice.value in old) old - choice.value else if (question.multiple) old + choice.value else setOf(choice.value)
        }, enabled = editable, modifier = Modifier.fillMaxWidth().background(Soft, RoundedCornerShape(8.dp))) { Text((if (choice.value in selections[index]) "● " else "○ ") + choice.label + if (choice.description.isNotEmpty()) " · ${choice.description}" else "", color = Ink) } }
        OutlinedTextField(inputs[index], { inputs[index] = it }, label = { Text(if (question.choices.isEmpty()) "填写回复" else "补充说明或其他回答") }, enabled = editable, modifier = Modifier.fillMaxWidth())
    }
    if (value.error.isNotEmpty()) Text(value.error, color = Ink, fontSize = 12.sp)
    if (!structured) Button(onClick = { onAnswer(value.id, value.questions.mapIndexed { i, question -> Answer(question.text, selections[i].toList(), inputs[i], question.cardId) }) },
        enabled = editable && value.questions.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Honey, contentColor = Ink), modifier = Modifier.fillMaxWidth()) {
        Text(if (value.status == "failed") "重新提交" else "提交回复")
    }
    if (!editable) Text(when (value.status) { "sending" -> "正在提交…"; "submitted" -> "已提交"; "unknown" -> "结果待确认，请在电脑端核实"; else -> "此确认已失效" }, color = Muted, fontSize = 12.sp)
}

@Composable private fun AttachmentCard(file: Attachment, attachments: Attachments, onAction: (Attachment, String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Soft, RoundedCornerShape(10.dp)).padding(10.dp)) {
        if (file.image && file.available) {
            val bitmap by produceState<Bitmap?>(null, file.id) { value = try { attachments.image(file, 960) } catch (_: Exception) { null } }
            if (bitmap != null) Image(bitmap!!.asImageBitmap(), file.name, modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clickable { onAction(file, "preview") }, contentScale = ContentScale.Fit)
            else Text("图片预览不可用，点击重试或保存", color = Muted, modifier = Modifier.clickable { onAction(file, "preview") })
        }
        Text("附件 · ${file.name}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Text(file.mime + if (file.size > 0) " · ${(file.size + 1023) / 1024} KB" else "", color = Muted, fontSize = 11.sp)
        if (file.available) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!file.image) SmallAction("打开") { onAction(file, "open") }
            SmallAction("保存") { onAction(file, "save") }
            SmallAction("转发") { onAction(file, "share") }
            if (file.html) SmallAction("渲染") { onAction(file, "render") }
        } else Text("已收到，但没有可下载内容，请在电脑端查看", color = Muted, fontSize = 12.sp)
    }
}

@Composable private fun Preview(file: Attachment, action: String, attachments: Attachments, onClose: () -> Unit, onAction: (Attachment, String) -> Unit) {
    var retry by remember(file.id) { mutableIntStateOf(0) }
    var failure by remember(file.id) { mutableStateOf("") }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Page).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("‹ 返回") }
                Text(file.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (action == "render") {
                val html by produceState<String?>(null, file.id, retry) { failure = ""; value = try { attachments.html(file) } catch (e: Exception) { failure = e.message ?: "HTML 加载失败"; null } }
                if (html == null) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (failure.isEmpty()) CircularProgressIndicator() else Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(failure); TextButton(onClick = { retry++ }) { Text("重新加载") } }
                } else {
                    val context = LocalContext.current
                    val web = remember(file.id) { WebView(context).apply {
                        settings.javaScriptEnabled = true; settings.domStorageEnabled = false; settings.allowFileAccess = false; settings.allowContentAccess = false
                        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE; settings.setSupportMultipleWindows(false)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                                request?.url?.scheme !in listOf("http", "https")
                        }
                    } }
                    DisposableEffect(web) { onDispose { web.stopLoading(); web.destroy() } }
                    LaunchedEffect(web, html) { web.loadDataWithBaseURL("https://preview.beegent.invalid/", html!!, "text/html", "UTF-8", null) }
                    AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
                }
            } else {
                val bitmap by produceState<Bitmap?>(null, file.id, retry) { failure = ""; value = try { attachments.image(file, 2560) } catch (e: Exception) { failure = e.message ?: "图片加载失败"; null } }
                var zoom by remember(file.id) { mutableFloatStateOf(1f) }
                var pan by remember(file.id) { mutableStateOf(Offset.Zero) }
                if (bitmap == null) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (failure.isEmpty()) CircularProgressIndicator() else Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(failure); TextButton(onClick = { retry++ }) { Text("重新加载") } }
                }
                else Image(bitmap!!.asImageBitmap(), file.name, contentScale = ContentScale.Fit, modifier = Modifier.weight(1f).fillMaxWidth()
                    .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }
                    .pointerInput(file.id) { detectTransformGestures { _, movement, scale, _ ->
                        zoom = (zoom * scale).coerceIn(1f, 5f); pan = if (zoom > 1f) pan + movement else Offset.Zero
                    } }
                    .pointerInput(file.id) { detectTapGestures(onDoubleTap = { zoom = if (zoom > 1f) 1f else 2f; pan = Offset.Zero }) })
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onAction(file, "save") }) { Text("↓ 保存") }
                TextButton(onClick = { onAction(file, "share") }) { Text("↗ 转发") }
            }
        }
    }
}

@Composable private fun MarkdownText(value: String) {
    val lines = value.replace("\r\n", "\n").lines()
    var code = false
    lines.forEach { line ->
        if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) { code = !code; return@forEach }
        if (code) Text(line, Modifier.fillMaxWidth().background(Soft).padding(horizontal = 8.dp), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
        else if (line.isBlank()) Spacer(Modifier.height(6.dp))
        else {
            val heading = Regex("^(#{1,6})\\s+(.+)$").find(line)
            val text = heading?.groupValues?.get(2) ?: line.replace(Regex("^\\s*[-+*]\\s+"), "• ").replace(Regex("^\\s*>\\s?"), "")
            val annotated = remember(text) { buildAnnotatedString {
                val pattern = Regex("!?\\[([^]]+)]\\(([^\\s)]+)\\)|\\*\\*([^*]+)\\*\\*|`([^`]+)`|~~([^~]+)~~|\\*([^*]+)\\*")
                var cursor = 0
                pattern.findAll(text).forEach { match ->
                    append(text.substring(cursor, match.range.first)); val start = length
                    val group = match.groupValues
                    val content = when { group[1].isNotEmpty() -> if (match.value.startsWith("!")) "图片：${group[1]}" else group[1]; else -> group.drop(3).firstOrNull { it.isNotEmpty() } ?: match.value }
                    append(content)
                    when {
                        group[1].isNotEmpty() && group[2].startsWith("http") -> addLink(LinkAnnotation.Url(group[2]), start, length)
                        group[3].isNotEmpty() -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                        group[4].isNotEmpty() -> addStyle(SpanStyle(background = Soft), start, length)
                        group[5].isNotEmpty() -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, length)
                    }
                    cursor = match.range.last + 1
                }
                append(text.substring(cursor))
            } }
            Text(annotated, fontSize = if (heading != null) (19 - heading.groupValues[1].length).sp else 14.sp, lineHeight = 23.sp, fontWeight = if (heading != null) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}
