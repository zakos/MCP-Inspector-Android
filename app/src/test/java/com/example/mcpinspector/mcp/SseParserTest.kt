package com.example.mcpinspector.mcp

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {

    @Test
    fun `single event with single data line`() {
        val raw = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}\n\n"

        val events = parseSseEvents(raw)

        assertEquals(1, events.size)
        assertEquals("message", events[0].event)
        assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}", events[0].data)
    }

    @Test
    fun `multiple data lines are joined with newline`() {
        val raw = "event: message\ndata: line one\ndata: line two\n\n"

        val events = parseSseEvents(raw)

        assertEquals(1, events.size)
        assertEquals("line one\nline two", events[0].data)
    }

    @Test
    fun `multiple events in one stream are all dispatched`() {
        val raw = buildString {
            append("event: message\ndata: {\"id\":1}\n\n")
            append("event: message\ndata: {\"id\":2}\n\n")
        }

        val events = parseSseEvents(raw)

        assertEquals(2, events.size)
        assertEquals("{\"id\":1}", events[0].data)
        assertEquals("{\"id\":2}", events[1].data)
    }

    @Test
    fun `missing trailing blank line still dispatches the last event`() {
        val raw = "event: message\ndata: {\"id\":1}"

        val events = parseSseEvents(raw)

        assertEquals(1, events.size)
        assertEquals("{\"id\":1}", events[0].data)
    }

    @Test
    fun `comment lines and untyped events are handled`() {
        val raw = ": keep-alive\ndata: {\"id\":1}\n\n"

        val events = parseSseEvents(raw)

        assertEquals(1, events.size)
        assertEquals(null, events[0].event)
        assertEquals("{\"id\":1}", events[0].data)
    }

    @Test
    fun `blank lines between events without data produce nothing`() {
        val raw = "\n\n\n"

        val events = parseSseEvents(raw)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `id field is captured`() {
        val raw = "id: abc\ndata: {\"id\":1}\n\n"

        val events = parseSseEvents(raw)

        assertEquals("abc", events[0].id)
    }

    @Test
    fun `json payloads are parsed and malformed ones are skipped`() {
        val raw = buildString {
            append("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"ok\":true}}\n\n")
            append("event: message\ndata: not json\n\n")
        }

        val payloads = parseSseJsonPayloads(raw)

        assertEquals(1, payloads.size)
        val obj = payloads[0].jsonObject
        assertEquals("2.0", obj["jsonrpc"]!!.jsonPrimitive.content)
    }
}
