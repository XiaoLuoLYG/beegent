package com.xiaoluolyg.beegent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryReplayTest {
    @Test fun olderPageKeepsAlreadyLoadedMessages() {
        fun record(request: String, role: String, text: String, time: Int) =
            JSONObject().put("request_id", request).put("role", role).put("content", text)
                .put("event_type", if (role == "user") "chat.send" else "chat.final").put("timestamp", time)
        val recent = listOf(record("b", "user", "B", 3), record("b", "assistant", "reply B", 4))
        val older = listOf(record("a", "user", "A", 1), record("a", "assistant", "reply A", 2))
        val session = Session("s", "ws://host/ws", "http://host", Mode())
        session.restore(recent)
        session.restore(older + session.historyRecords)
        assertEquals(listOf("A", "reply A", "B", "reply B"), session.rows.filter { it.historical }.map { it.text })
    }
}
