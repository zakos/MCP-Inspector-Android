package com.example.mcpinspector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mcpinspector.data.ProfileStore
import com.example.mcpinspector.data.ServerProfile
import com.example.mcpinspector.log.LogStore
import com.example.mcpinspector.mcp.JsonRpcResponse
import com.example.mcpinspector.mcp.McpClient
import com.example.mcpinspector.mcp.ToolDescriptor
import com.example.mcpinspector.mcp.ToolsListOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Connecting : ConnectionStatus
    data object Connected : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}

data class ConnectionUiState(
    val status: ConnectionStatus = ConnectionStatus.Idle,
    val capabilities: JsonObject? = null,
    val serverInfo: JsonObject? = null,
    val tools: List<ToolDescriptor> = emptyList(),
)

data class ToolCallUiState(
    val isLoading: Boolean = false,
    val response: JsonRpcResponse? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val profileStore = ProfileStore(application)

    val profiles: StateFlow<List<ServerProfile>> = profileStore.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logEntries = LogStore.entries

    private var client: McpClient? = null

    private val _connection = MutableStateFlow(ConnectionUiState())
    val connection: StateFlow<ConnectionUiState> = _connection

    private val _toolCall = MutableStateFlow(ToolCallUiState())
    val toolCall: StateFlow<ToolCallUiState> = _toolCall

    fun saveProfile(profile: ServerProfile) {
        viewModelScope.launch { profileStore.upsert(profile) }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch { profileStore.delete(id) }
    }

    fun connect(profile: ServerProfile) {
        client?.close()
        _toolCall.value = ToolCallUiState()
        _connection.value = ConnectionUiState(status = ConnectionStatus.Connecting)

        val newClient = McpClient(profile) { exchange -> LogStore.record(profile.name, exchange) }
        client = newClient

        viewModelScope.launch {
            val initResponse = newClient.initialize()
            val initError = initResponse.error
            if (initError != null) {
                _connection.value = ConnectionUiState(status = ConnectionStatus.Error(initError.message))
                return@launch
            }

            when (val outcome = newClient.listTools()) {
                is ToolsListOutcome.Success -> _connection.value = ConnectionUiState(
                    status = ConnectionStatus.Connected,
                    capabilities = newClient.capabilities,
                    serverInfo = newClient.serverInfo,
                    tools = outcome.tools,
                )

                is ToolsListOutcome.Error -> _connection.value = ConnectionUiState(
                    status = ConnectionStatus.Error(outcome.error.message),
                    capabilities = newClient.capabilities,
                    serverInfo = newClient.serverInfo,
                )
            }
        }
    }

    fun callTool(name: String, arguments: JsonObject) {
        val activeClient = client ?: return
        _toolCall.value = ToolCallUiState(isLoading = true)
        viewModelScope.launch {
            val response = activeClient.callTool(name, arguments)
            _toolCall.value = ToolCallUiState(isLoading = false, response = response)
        }
    }

    fun clearLog() = LogStore.clear()

    override fun onCleared() {
        client?.close()
    }
}
