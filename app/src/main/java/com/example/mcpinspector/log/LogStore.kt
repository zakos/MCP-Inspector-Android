package com.example.mcpinspector.log

import com.example.mcpinspector.mcp.McpExchange
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val serverName: String,
    val exchange: McpExchange,
)

/**
 * In-memory request/response log for the current app session. Not persisted: it exists so the
 * user can inspect raw MCP traffic while the app is open, not as a durable audit trail.
 */
object LogStore {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    fun record(serverName: String, exchange: McpExchange) {
        _entries.update { it + LogEntry(serverName = serverName, exchange = exchange) }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}

private val SENSITIVE_HEADER_NAMES = setOf("authorization", "cookie", "set-cookie")

/** Masks header values (bearer tokens, cookies, ...) for on-screen display. */
fun maskHeaderValue(name: String, value: String): String {
    if (name.lowercase() !in SENSITIVE_HEADER_NAMES) return value
    if (value.length <= 8) return "••••••••"
    return "${value.take(4)}…${value.takeLast(4)}"
}
