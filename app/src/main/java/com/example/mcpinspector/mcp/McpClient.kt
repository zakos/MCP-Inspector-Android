package com.example.mcpinspector.mcp

import com.example.mcpinspector.data.ServerProfile
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** One raw HTTP request/response pair, kept for the log screen. */
data class McpExchange(
    val method: String,
    val requestHeaders: Map<String, String>,
    val requestBody: String,
    val httpStatus: Int,
    val responseHeaders: Map<String, String>,
    val responseBody: String,
    val timestampMillis: Long = System.currentTimeMillis(),
)

/**
 * A minimal hand-rolled MCP Streamable HTTP client: `initialize`, `tools/list`, `tools/call`,
 * plus the `notifications/initialized` notification. Every exchange is reported via
 * [onExchange] so the UI can show the raw protocol traffic, not just parsed results.
 */
class McpClient(
    private val profile: ServerProfile,
    private val onExchange: (McpExchange) -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO)

    private var sessionId: String? = null

    var capabilities: JsonObject? = null
        private set
    var serverInfo: JsonObject? = null
        private set

    suspend fun initialize(): JsonRpcResponse {
        val params = buildJsonObject {
            put("protocolVersion", PROTOCOL_VERSION)
            putJsonObject("capabilities") {}
            putJsonObject("clientInfo") {
                put("name", "MCP Inspector")
                put("version", "0.1.0")
            }
        }
        val response = send("initialize", params)
        response.result?.let { result ->
            val initResult = runCatching { json.decodeFromJsonElement(InitializeResult.serializer(), result) }.getOrNull()
            capabilities = initResult?.capabilities
            serverInfo = initResult?.serverInfo
        }
        if (response.error == null) {
            sendNotification("notifications/initialized", null)
        }
        return response
    }

    suspend fun listTools(): ToolsListOutcome {
        val response = send("tools/list", buildJsonObject {})
        response.error?.let { return ToolsListOutcome.Error(it) }
        val result = response.result ?: return ToolsListOutcome.Success(emptyList())
        val parsed = runCatching { json.decodeFromJsonElement(ToolsListResult.serializer(), result) }.getOrNull()
            ?: return ToolsListOutcome.Error(JsonRpcError(code = -1, message = "Nem sikerült értelmezni a tools/list választ"))
        return ToolsListOutcome.Success(parsed.tools)
    }

    suspend fun callTool(name: String, arguments: JsonObject): JsonRpcResponse {
        val params = buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        }
        return send("tools/call", params)
    }

    fun close() {
        http.close()
    }

    private suspend fun send(method: String, params: JsonElement?): JsonRpcResponse {
        val id = nextRequestId()
        val bodyText = json.encodeToString(JsonRpcRequest(id = id, method = method, params = params))
        val reqHeaders = buildRequestHeaders()

        val httpResponse = postRpc(bodyText, reqHeaders)
        val responseBody = runCatching { httpResponse.bodyAsText() }.getOrDefault("")
        recordExchange(method, reqHeaders, bodyText, httpResponse, responseBody)

        return parseResponse(httpResponse, responseBody, id)
    }

    private suspend fun sendNotification(method: String, params: JsonElement?) {
        val bodyText = json.encodeToString(JsonRpcNotification(method = method, params = params))
        val reqHeaders = buildRequestHeaders()

        val httpResponse = postRpc(bodyText, reqHeaders)
        val responseBody = runCatching { httpResponse.bodyAsText() }.getOrDefault("")
        recordExchange(method, reqHeaders, bodyText, httpResponse, responseBody)
    }

    private suspend fun postRpc(bodyText: String, reqHeaders: Map<String, String>): HttpResponse =
        http.post(profile.url) {
            reqHeaders.forEach { (key, value) ->
                if (!key.equals(HttpHeaders.ContentType, ignoreCase = true)) headers.append(key, value)
            }
            contentType(ContentType.Application.Json)
            setBody(bodyText)
        }

    private fun recordExchange(
        method: String,
        reqHeaders: Map<String, String>,
        bodyText: String,
        httpResponse: HttpResponse,
        responseBody: String,
    ) {
        httpResponse.headers["Mcp-Session-Id"]?.let { sessionId = it }
        val responseHeaders = httpResponse.headers.entries().associate { it.key to it.value.joinToString(", ") }
        onExchange(
            McpExchange(
                method = method,
                requestHeaders = reqHeaders,
                requestBody = bodyText,
                httpStatus = httpResponse.status.value,
                responseHeaders = responseHeaders,
                responseBody = responseBody,
            )
        )
    }

    private fun buildRequestHeaders(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        result[HttpHeaders.ContentType] = "application/json"
        result[HttpHeaders.Accept] = "application/json, text/event-stream"
        sessionId?.let { result["Mcp-Session-Id"] = it }
        result["MCP-Protocol-Version"] = PROTOCOL_VERSION
        result.putAll(profile.headers)
        return result
    }

    /** Handles both plain-JSON and SSE responses, and surfaces HTTP-level errors as a JSON-RPC error. */
    private fun parseResponse(httpResponse: HttpResponse, body: String, requestId: JsonElement): JsonRpcResponse {
        val responseContentType = httpResponse.contentType()
        val payload: JsonElement? = if (responseContentType?.match(ContentType.Text.EventStream) == true) {
            val payloads = parseSseJsonPayloads(body, json)
            payloads.firstOrNull { (it as? JsonObject)?.get("id") == requestId } ?: payloads.lastOrNull()
        } else {
            runCatching { json.parseToJsonElement(body) }.getOrNull()
        }

        if (!httpResponse.status.isSuccess() && payload == null) {
            return JsonRpcResponse(
                id = requestId,
                error = JsonRpcError(code = httpResponse.status.value, message = "HTTP ${httpResponse.status.value}: $body"),
            )
        }

        if (payload == null) {
            return JsonRpcResponse(
                id = requestId,
                error = JsonRpcError(code = -1, message = "Üres vagy nem értelmezhető válasz"),
            )
        }

        return runCatching { json.decodeFromJsonElement(JsonRpcResponse.serializer(), payload) }
            .getOrElse {
                JsonRpcResponse(id = requestId, error = JsonRpcError(code = -1, message = "Nem sikerült értelmezni a választ: ${it.message}"))
            }
    }

    companion object {
        const val PROTOCOL_VERSION = "2025-06-18"
    }
}

sealed interface ToolsListOutcome {
    data class Success(val tools: List<ToolDescriptor>) : ToolsListOutcome
    data class Error(val error: JsonRpcError) : ToolsListOutcome
}
