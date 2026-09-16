package com.example.mcpinspector.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mcpinspector.data.ServerProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    profiles: List<ServerProfile>,
    onOpen: (ServerProfile) -> Unit,
    onSave: (ServerProfile) -> Unit,
    onDelete: (ServerProfile) -> Unit,
    onOpenLog: () -> Unit,
) {
    var editingProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var isAdding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MCP Inspector") },
                actions = {
                    IconButton(onClick = onOpenLog) {
                        Icon(Icons.Filled.History, contentDescription = "Napló")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { isAdding = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Új szerver")
            }
        },
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Még nincs mentett szerver. Adj hozzá egyet a + gombbal.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(profiles, key = { it.id }) { profile ->
                    ServerListItem(
                        profile = profile,
                        onClick = { onOpen(profile) },
                        onEdit = { editingProfile = profile },
                        onDelete = { onDelete(profile) },
                    )
                }
            }
        }
    }

    if (isAdding) {
        ProfileEditorDialog(
            initial = null,
            onDismiss = { isAdding = false },
            onSave = { onSave(it); isAdding = false },
        )
    }
    editingProfile?.let { profile ->
        ProfileEditorDialog(
            initial = profile,
            onDismiss = { editingProfile = null },
            onSave = { onSave(it); editingProfile = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerListItem(
    profile: ServerProfile,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium)
            Text(profile.url, style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Szerkesztés") }, onClick = { menuOpen = false; onEdit() })
            DropdownMenuItem(text = { Text("Törlés") }, onClick = { menuOpen = false; onDelete() })
        }
    }
}

private class HeaderRow(key: String = "", value: String = "") {
    var key by mutableStateOf(key)
    var value by mutableStateOf(value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditorDialog(
    initial: ServerProfile?,
    onDismiss: () -> Unit,
    onSave: (ServerProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    val headerRows = remember {
        androidx.compose.runtime.mutableStateListOf<HeaderRow>().apply {
            initial?.headers?.forEach { (k, v) -> add(HeaderRow(k, v)) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Új szerver" else "Szerver szerkesztése") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Név") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "HTTP fejlécek",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                headerRows.forEachIndexed { index, row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        OutlinedTextField(
                            value = row.key,
                            onValueChange = { row.key = it },
                            label = { Text("Fejléc") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = row.value,
                            onValueChange = { row.value = it },
                            label = { Text("Érték") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                        )
                        IconButton(onClick = { headerRows.removeAt(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Fejléc törlése")
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    TextButton(onClick = { headerRows.add(HeaderRow()) }) {
                        Text("+ Fejléc")
                    }
                    TextButton(onClick = { headerRows.add(HeaderRow("Authorization", "Bearer ")) }) {
                        Text("Bearer token hozzáadása")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = {
                    val headers = headerRows
                        .filter { it.key.isNotBlank() }
                        .associate { it.key to it.value }
                    onSave(
                        ServerProfile(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            url = url.trim(),
                            headers = headers,
                        )
                    )
                },
            ) { Text("Mentés") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Mégse") }
        },
    )
}
