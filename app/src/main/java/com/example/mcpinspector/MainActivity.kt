package com.example.mcpinspector

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mcpinspector.ui.AppViewModel
import com.example.mcpinspector.ui.LogScreen
import com.example.mcpinspector.ui.ServerDetailScreen
import com.example.mcpinspector.ui.ServerListScreen
import com.example.mcpinspector.ui.ToolScreen
import com.example.mcpinspector.ui.theme.McpInspectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            McpInspectorTheme {
                McpInspectorApp()
            }
        }
    }
}

@Composable
private fun McpInspectorApp(viewModel: AppViewModel = viewModel()) {
    val navController = rememberNavController()
    val profiles by viewModel.profiles.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val toolCall by viewModel.toolCall.collectAsState()
    val logEntries by viewModel.logEntries.collectAsState()

    NavHost(navController = navController, startDestination = "servers") {
        composable("servers") {
            ServerListScreen(
                profiles = profiles,
                onOpen = { profile -> navController.navigate("detail/${profile.id}") },
                onSave = { viewModel.saveProfile(it) },
                onDelete = { viewModel.deleteProfile(it.id) },
                onOpenLog = { navController.navigate("log") },
            )
        }
        composable("detail/{profileId}") { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")
            val profile = profiles.firstOrNull { it.id == profileId }
            if (profile != null) {
                ServerDetailScreen(
                    profile = profile,
                    connection = connection,
                    onConnect = { viewModel.connect(profile) },
                    onOpenTool = { tool -> navController.navigate("tool/${Uri.encode(tool.name)}") },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable("tool/{toolName}") { backStackEntry ->
            val toolName = backStackEntry.arguments?.getString("toolName")?.let { Uri.decode(it) }
            val tool = connection.tools.firstOrNull { it.name == toolName }
            if (tool != null) {
                ToolScreen(
                    tool = tool,
                    toolCallState = toolCall,
                    onCall = { args -> viewModel.callTool(tool.name, args) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable("log") {
            LogScreen(
                entries = logEntries,
                onClear = { viewModel.clearLog() },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
