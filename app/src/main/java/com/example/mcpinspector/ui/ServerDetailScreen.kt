package com.example.mcpinspector.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mcpinspector.data.ServerProfile
import com.example.mcpinspector.mcp.ToolDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val prettyJson = Json { prettyPrint = true }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailScreen(
    profile: ServerProfile,
    connection: ConnectionUiState,
    onConnect: () -> Unit,
    onOpenTool: (ToolDescriptor) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(profile.url, style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.Button(
                        onClick = onConnect,
                        modifier = Modifier.padding(top = 12.dp),
                        enabled = connection.status !is ConnectionStatus.Connecting,
                    ) {
                        Text(
                            when (connection.status) {
                                ConnectionStatus.Idle -> "Kapcsolódás"
                                ConnectionStatus.Connecting -> "Kapcsolódás…"
                                ConnectionStatus.Connected -> "Újrakapcsolódás"
                                is ConnectionStatus.Error -> "Újrapróbálkozás"
                            }
                        )
                    }
                }
            }

            when (val status = connection.status) {
                ConnectionStatus.Connecting -> item {
                    CircularProgressIndicator(Modifier.padding(16.dp))
                }

                is ConnectionStatus.Error -> item {
                    Card(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "Hiba: ${status.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                ConnectionStatus.Connected -> {
                    item {
                        CapabilitiesCard(connection.serverInfo, connection.capabilities)
                    }
                    item {
                        Text(
                            "Tool-ok (${connection.tools.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(connection.tools, key = { it.name }) { tool ->
                        ToolListItem(tool = tool, onClick = { onOpenTool(tool) })
                    }
                }

                ConnectionStatus.Idle -> Unit
            }
        }
    }
}

@Composable
private fun CapabilitiesCard(serverInfo: JsonObject?, capabilities: JsonObject?) {
    if (serverInfo == null && capabilities == null) return
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Kapcsolat adatai", style = MaterialTheme.typography.titleSmall)
            serverInfo?.let {
                Text(
                    "serverInfo:\n${prettyJson.encodeToString(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            capabilities?.let {
                Text(
                    "capabilities:\n${prettyJson.encodeToString(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolListItem(tool: ToolDescriptor, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(tool.name, style = MaterialTheme.typography.titleSmall)
            tool.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
