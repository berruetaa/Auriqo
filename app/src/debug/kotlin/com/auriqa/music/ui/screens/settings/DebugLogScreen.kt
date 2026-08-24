package com.auriqo.music.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.auriqo.music.utils.debug.DebugLogTree

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(navController: NavController) {
    val context = LocalContext.current
    val tree = DebugLogTree.getInstance()
    val logs by tree?.logs?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(emptyList())
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Logs") },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val text = logs.joinToString("\n") { entry ->
                            "${entry.formattedTime} ${entry.levelStr} ${entry.tag ?: "?"}: ${entry.fullMessage}"
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Auriqo Debug Logs", text))
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy logs")
                    }
                    IconButton(onClick = { tree?.clear() }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Clear logs")
                    }
                },
            )
        },
    ) { padding ->
        if (logs.isEmpty()) {
            Text("No logs captured", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(logs, key = { it.id }) { entry ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            "${entry.formattedTime} ${entry.levelStr} ${entry.tag ?: "?"}",
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        )
                        Text(entry.fullMessage, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
