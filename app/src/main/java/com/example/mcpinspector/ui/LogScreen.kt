package com.example.mcpinspector.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.mcpinspector.log.LogEntry
import com.example.mcpinspector.log.maskHeaderValue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val prettyJson = Json { prettyPrint = true }
private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    entries: List<LogEntry>,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Napló") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                    }
                },
                actions = {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Napló ürítése")
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("A napló üres.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(entries.asReversed(), key = { it.id }) { entry ->
                    LogEntryItem(entry)
                }
            }
        }
    }
}

private fun prettyOrRaw(text: String): String {
    if (text.isBlank()) return "(üres)"
    return runCatching { prettyJson.encodeToString(Json.parseToJsonElement(text)) }.getOrDefault(text)
}

@Composable
private fun LogEntryItem(entry: LogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val exchange = entry.exchange

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                Text("${entry.serverName} · ${exchange.method}", style = MaterialTheme.typography.titleSmall)
                Text(timeFormat.format(Date(exchange.timestampMillis)), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "HTTP ${exchange.httpStatus}",
                style = MaterialTheme.typography.bodySmall,
                color = if (exchange.httpStatus in 200..299) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )

            if (expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    Text("Kérés fejlécek", style = MaterialTheme.typography.labelMedium)
                    exchange.requestHeaders.forEach { (k, v) ->
                        Text("$k: ${maskHeaderValue(k, v)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Kérés body", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    Text(prettyOrRaw(exchange.requestBody), style = MaterialTheme.typography.bodySmall)

                    Text("Válasz fejlécek", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    exchange.responseHeaders.forEach { (k, v) ->
                        Text("$k: ${maskHeaderValue(k, v)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Válasz body", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    Text(prettyOrRaw(exchange.responseBody), style = MaterialTheme.typography.bodySmall)

                    IconButton(onClick = {
                        val full = buildString {
                            appendLine("${entry.serverName} · ${exchange.method} · HTTP ${exchange.httpStatus}")
                            appendLine()
                            appendLine("Request:")
                            appendLine(prettyOrRaw(exchange.requestBody))
                            appendLine()
                            appendLine("Response:")
                            appendLine(prettyOrRaw(exchange.responseBody))
                        }
                        clipboard.setText(AnnotatedString(full))
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Másolás")
                    }
                }
            }
        }
    }
}
