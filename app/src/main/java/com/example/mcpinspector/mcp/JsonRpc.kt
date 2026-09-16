package com.example.mcpinspector.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A JSON-RPC 2.0 request that expects a response (carries an [id]). */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement,
    val method: String,
    val params: JsonElement? = null,
)

/** A JSON-RPC 2.0 notification: same shape as a request but with no `id`, so no response is sent. */
@Serializable
data class JsonRpcNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

/** A single entry from a `tools/list` response. */
@Serializable
data class ToolDescriptor(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonObject? = null,
)

@Serializable
data class ToolsListResult(
    val tools: List<ToolDescriptor> = emptyList(),
    val nextCursor: String? = null,
)

/** Result of `initialize`: the fields the inspector cares about, everything else stays raw. */
@Serializable
data class InitializeResult(
    val protocolVersion: String? = null,
    val capabilities: JsonObject? = null,
    val serverInfo: JsonObject? = null,
)

private var idCounter = 0

/** Generates a fresh JSON-RPC request id, unique within this process. */
fun nextRequestId(): JsonElement = JsonPrimitive(++idCounter)
