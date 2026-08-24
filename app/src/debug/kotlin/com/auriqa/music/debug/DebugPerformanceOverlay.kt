package com.auriqo.music.debug

import android.content.Context
import androidx.core.content.edit
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.net.URLEncoder

internal const val DEBUG_HUD_KEY = "performance_hud_enabled"

internal object DebugPreferenceStore {
    private const val FILE = "auriqo_debug_preferences"

    fun getBoolean(context: Context, key: String, default: Boolean): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(key, default)

    fun setBoolean(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(key, value) }
    }
}

@Composable
fun DebugPerformanceOverlay(
    collector: PlaybackDebugCollector,
    navController: NavController,
) {
    val enabled by DebugRuntimeAccess.hudEnabled.collectAsState()
    if (!enabled) return

    val state by collector.state.collectAsState()
    val trace = state.activeTrace ?: return
    val elapsed = trace.tapToFirstAudioMs ?: trace.events.lastOrNull()?.elapsedMs ?: 0L
    val label = when {
        trace.recovered -> "RECOVERED"
        trace.classification == DebugPerformanceClass.PRELOADED -> "PRELOADED"
        trace.classification == DebugPerformanceClass.HOT -> "HIT"
        else -> trace.classification.name
    }
    val target = when (trace.classification) {
        DebugPerformanceClass.HOT -> 150L
        DebugPerformanceClass.PRELOADED -> 250L
        else -> 500L
    }
    val color = when {
        elapsed <= target -> Color(0xFF2E7D32)
        elapsed <= target * 2 -> Color(0xFFF9A825)
        else -> MaterialTheme.colorScheme.error
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .padding(top = 12.dp, end = 12.dp)
                .background(Color.Transparent)
                .clickable {
                    navController.navigate("settings/debug_center?traceId=${URLEncoder.encode(trace.traceId, "UTF-8")}")
                }
                .align(Alignment.TopEnd),
        ) {
            Surface(
                color = color.copy(alpha = 0.94f),
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 4.dp,
            ) {
                Text(
                    text = "${if (trace.slow) "⚠" else "▶"} ${elapsed}ms · $label",
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                )
            }
        }
    }
}
