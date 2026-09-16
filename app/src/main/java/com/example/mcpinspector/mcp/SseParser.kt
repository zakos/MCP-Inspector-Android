package com.example.mcpinspector.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** One dispatched `text/event-stream` event. */
data class SseEvent(
    val event: String? = null,
    val id: String? = null,
    val data: String,
)

/**
 * Parses a full `text/event-stream` body into the events it contains.
 *
 * MCP Streamable HTTP responses can pack several `data:` lines into one event (joined with
 * `\n` per the SSE spec) and several events into one response body; both are handled here.
 * `retry:` lines and `:`-comments are ignored, and a stream missing its trailing blank line
 * still dispatches its last event.
 */
fun parseSseEvents(raw: String): List<SseEvent> {
    val events = mutableListOf<SseEvent>()
    var eventType: String? = null
    var eventId: String? = null
    val dataLines = mutableListOf<String>()

    fun dispatch() {
        if (dataLines.isNotEmpty()) {
            events += SseEvent(event = eventType, id = eventId, data = dataLines.joinToString("\n"))
        }
        eventType = null
        dataLines.clear()
    }

    for (rawLine in raw.split("\n")) {
        val line = rawLine.removeSuffix("\r")
        when {
            line.isEmpty() -> dispatch()
            line.startsWith(":") -> Unit
            line.startsWith("data:") -> dataLines += line.removePrefix("data:").removePrefix(" ")
            line.startsWith("event:") -> eventType = line.removePrefix("event:").removePrefix(" ")
            line.startsWith("id:") -> eventId = line.removePrefix("id:").removePrefix(" ")
            else -> Unit
        }
    }
    dispatch()

    return events
}

/** Parses every `message` (or untyped) event's `data:` payload as JSON, skipping malformed ones. */
fun parseSseJsonPayloads(raw: String, json: Json = Json { ignoreUnknownKeys = true }): List<JsonElement> =
    parseSseEvents(raw)
        .filter { it.event == null || it.event == "message" }
        .mapNotNull { event -> runCatching { json.parseToJsonElement(event.data) }.getOrNull() }
