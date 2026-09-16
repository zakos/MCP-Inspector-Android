package com.example.mcpinspector.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.exposedDropdownSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mcpinspector.mcp.FormField
import com.example.mcpinspector.mcp.FormValidationResult
import com.example.mcpinspector.mcp.ToolDescriptor
import com.example.mcpinspector.mcp.buildFormFields
import com.example.mcpinspector.mcp.buildToolArguments
import com.example.mcpinspector.mcp.JsonRpcError
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

private val prettyJson = Json { prettyPrint = true }
private val compactJson = Json { prettyPrint = false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScreen(
    tool: ToolDescriptor,
    toolCallState: ToolCallUiState,
    onCall: (JsonObject) -> Unit,
    onBack: () -> Unit,
) {
    val fields = remember(tool) { buildFormFields(tool.inputSchema) }
    val values = remember(tool) {
        mutableStateMapOf<String, String>().apply {
            fields.forEach { field ->
                val default = when (field) {
                    is FormField.StringField -> field.default
                    is FormField.EnumField -> field.default
                    is FormField.NumberField -> field.default
                    is FormField.BooleanField -> field.default?.toString()
                    is FormField.RawJsonField -> field.default
                }
                if (default != null) put(field.key, default)
            }
        }
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tool.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            tool.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp))
            }

            fields.forEach { field ->
                FormFieldInput(
                    field = field,
                    value = values[field.key].orEmpty(),
                    onValueChange = { values[field.key] = it },
                )
            }

            validationError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }

            androidx.compose.material3.Button(
                onClick = {
                    when (val result = buildToolArguments(fields, values)) {
                        is FormValidationResult.Valid -> {
                            validationError = null
                            onCall(result.arguments)
                        }
                        is FormValidationResult.Invalid -> {
                            validationError = "Hibás vagy hiányzó mező(k): ${result.invalidKeys.joinToString(", ")}"
                        }
                    }
                },
                modifier = Modifier.padding(top = 16.dp),
                enabled = !toolCallState.isLoading,
            ) {
                Text("Hívás")
            }

            if (toolCallState.isLoading) {
                CircularProgressIndicator(Modifier.padding(top = 16.dp))
            }

            toolCallState.response?.let { response ->
                ResponsePanel(
                    resultJson = response.result,
                    errorJson = response.error?.let { err -> Json.encodeToJsonElement(JsonRpcError.serializer(), err) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormFieldInput(field: FormField, value: String, onValueChange: (String) -> Unit) {
    val label = field.key + if (field.required) " *" else ""

    when (field) {
        is FormField.StringField -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            supportingText = field.description?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )

        is FormField.NumberField -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            supportingText = field.description?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )

        is FormField.BooleanField -> Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Text(label, modifier = Modifier.padding(top = 12.dp))
            Switch(
                checked = value.toBooleanStrictOrNull() ?: false,
                onCheckedChange = { onValueChange(it.toString()) },
                colors = SwitchDefaults.colors(),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        is FormField.EnumField -> {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.exposedDropdownSize(),
                ) {
                    field.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { onValueChange(option); expanded = false },
                        )
                    }
                }
            }
        }

        is FormField.RawJsonField -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("$label (nyers JSON)") },
            supportingText = field.description?.let { { Text(it) } },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun ResponsePanel(resultJson: JsonElement?, errorJson: JsonElement?) {
    var showRaw by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val payload = errorJson ?: resultJson
    val text = payload?.let { if (showRaw) compactJson.encodeToString(it) else prettyJson.encodeToString(it) } ?: "(nincs adat)"

    Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (errorJson != null) "Hiba" else "Válasz",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(end = 8.dp),
                )
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "Formázott" else "Nyers")
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Másolás")
                }
            }
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (errorJson != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
