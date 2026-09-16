package com.example.mcpinspector.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A saved MCP server connection: a base URL plus arbitrary HTTP headers
 * (bearer tokens, API keys, cookies, ...).
 */
@Serializable
data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)
