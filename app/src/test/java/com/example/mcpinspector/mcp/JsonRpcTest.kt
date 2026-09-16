package com.example.mcpinspector.mcp

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test: a JSON-RPC 2.0 request/notification MUST include `"jsonrpc":"2.0"`.
 * Since that field's value always equals its declared default, a [Json] instance without
 * `encodeDefaults = true` silently drops it - real servers then reject the request as invalid.
 */
class JsonRpcTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `encoded request always includes the jsonrpc version field`() {
        val request = JsonRpcRequest(id = JsonPrimitive(1), method = "initialize", params = null)

        val encoded = json.encodeToString(request)

        assertTrue(encoded.contains("\"jsonrpc\":\"2.0\""))
    }

    @Test
    fun `encoded notification always includes the jsonrpc version field`() {
        val notification = JsonRpcNotification(method = "notifications/initialized", params = null)

        val encoded = json.encodeToString(notification)

        assertTrue(encoded.contains("\"jsonrpc\":\"2.0\""))
    }
}
