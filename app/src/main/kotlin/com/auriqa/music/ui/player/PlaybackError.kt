package com.auriqo.music.ui.player

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auriqo.music.BuildConfig
import com.auriqo.music.R
import com.auriqo.music.playback.diagnostics.PlaybackDebugReportContext
import com.auriqo.music.playback.diagnostics.PlaybackDebugReportFormatter
import com.auriqo.music.playback.diagnostics.PlaybackFailure
import com.auriqo.music.playback.diagnostics.PlaybackRedactor

@Composable
fun PlaybackError(
    failure: PlaybackFailure,
    retry: () -> Unit,
) {
    var showDetails by remember(failure.traceId) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val report = remember(failure) {
        PlaybackDebugReportFormatter.format(
            failure = failure,
            context = PlaybackDebugReportContext(
                appVersion = BuildConfig.VERSION_NAME,
                sourceRevision = BuildConfig.SOURCE_REVISION,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE ?: "unknown",
                api = Build.VERSION.SDK_INT,
                abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
                quality = null,
                localOrRemote = failure.cacheStatus,
                networkType = failure.networkType,
            ),
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.error_playback_failed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = failure.humanMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Code: ${failure.stableCode}",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Trace: ${failure.traceId}",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        if (showDetails) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = buildString {
                    append("stage=${failure.stage}\n")
                    append("category=${failure.category}\n")
                    append("Media3=${failure.media3CodeName ?: "unknown"} (${failure.media3Code ?: "unknown"})\n")
                    append("HTTP=${failure.httpStatus ?: "unknown"}\n")
                    append("HTTP host=${failure.http?.host ?: "unknown"}\n")
                    append("HTTP message=${failure.http?.responseMessage ?: "unknown"}\n")
                    append("playability=${PlaybackRedactor.sanitizeScalar(failure.playabilityStatus)}\n")
                    append("attempt=${failure.attempt}/${failure.maxAttempts}\n")
                    append(failure.technicalMessage)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = retry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    painter = painterResource(R.drawable.replay),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.retry))
            }
            OutlinedButton(onClick = { showDetails = !showDetails }) {
                Text(text = if (showDetails) "Hide" else "Details")
            }
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(report)) }) {
                Text(text = "Copy debug report")
            }
        }
    }
}
