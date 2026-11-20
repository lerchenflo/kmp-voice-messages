package io.github.lerchenflo.voicemessages.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.lerchenflo.voicemessages.VoicePlaybackController
import io.github.lerchenflo.voicemessages.VoicePlaybackState
import io.github.lerchenflo.voicemessages.VoiceRecordingController
import io.github.lerchenflo.voicemessages.VoiceRecordingState
import io.github.lerchenflo.voicemessages.formatVoiceMessageTime
import kotlinx.coroutines.flow.StateFlow

/**
 * Screen reader texts of [VoiceMessageRecordingBar], from the app's own string resources.
 *
 * @property recording description of the live level bars while recording.
 * @property discard description of the delete button.
 * @property player texts of the draft player shown once the recording stopped.
 */
@Immutable
data class VoiceMessageRecordingBarTexts(
    val recording: String,
    val discard: String,
    val player: VoiceMessagePlayerTexts,
)

/**
 * The input row of a voice message: a 56 dp rounded row on `primaryContainer` at 30 %.
 *
 * - While recording: a dot in the `error` colour, the elapsed time, live level bars from [levels]
 *   (new levels on the right, older ones move left) and, after 80 % of [maxDurationMs], the
 *   remaining time in the `error` colour.
 * - Once stopped: a [VoiceMessagePlayer] for the draft ([VoiceRecordingController.DRAFT_PLAYBACK_ID]).
 * - Always: a delete button in the `error` colour ([onDiscard]).
 *
 * Shows nothing for [VoiceRecordingState.Idle]. The record/stop/send button stays in the app.
 *
 * @param state [VoiceRecordingController.state].
 * @param levels [VoiceRecordingController.levels]; read only while drawing, so the live bars don't
 * recompose the row.
 * @param playback [VoicePlaybackController.state], for the draft player.
 * @param onPlay plays the draft, e.g. `playback.play(VoiceRecordingController.DRAFT_PLAYBACK_ID, recorded.filePath)`.
 */
@Composable
fun VoiceMessageRecordingBar(
    state: VoiceRecordingState,
    levels: StateFlow<List<Float>>,
    playback: StateFlow<VoicePlaybackState>,
    maxDurationMs: Long,
    texts: VoiceMessageRecordingBarTexts,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    formatTime: (Long) -> String = ::formatVoiceMessageTime,
) {
    if (state == VoiceRecordingState.Idle) return

    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            is VoiceRecordingState.Recording -> {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatTime(state.elapsedMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                VoiceMessageLevelBars(
                    levels = levels,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .padding(horizontal = 12.dp)
                        .semantics { contentDescription = texts.recording },
                )
                if (state.elapsedMs > maxDurationMs * 0.8) {
                    Text(
                        text = formatTime(maxDurationMs - state.elapsedMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is VoiceRecordingState.Recorded -> VoiceMessagePlayer(
                id = VoiceRecordingController.DRAFT_PLAYBACK_ID,
                filePath = state.filePath,
                playback = playback,
                texts = texts.player,
                onPlay = onPlay,
                onPause = onPause,
                onSeek = onSeek,
                color = MaterialTheme.colorScheme.onSurface,
                durationMs = state.durationMs,
                formatTime = formatTime,
                modifier = Modifier.weight(1f),
            )

            VoiceRecordingState.Idle -> Unit
        }

        IconButton(onClick = onDiscard) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = texts.discard,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Live level bars: as many 3 dp bars as fit the width, the newest level on the right, older ones
 * moving left. A bar is between 3 dp and the full height, by its level (0..1).
 *
 * @param levels for example [VoiceRecordingController.levels], oldest first.
 */
@Composable
fun VoiceMessageLevelBars(
    levels: StateFlow<List<Float>>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val values by levels.collectAsState()
    Canvas(modifier = modifier.fillMaxWidth()) {
        val barWidth = 3.dp.toPx()
        val step = barWidth + 2.dp.toPx()
        val minHeight = 3.dp.toPx()
        val barCount = ((size.width + step - barWidth) / step).toInt().coerceAtLeast(0)
        // Read here, in the draw phase: a new level only redraws the bars
        val shown = values.takeLast(barCount)
        for (index in 0 until barCount) {
            // index 0 is the right-most (newest) bar; missing levels draw as silence
            val level = shown.getOrNull(shown.size - 1 - index) ?: 0f
            val barHeight = minHeight + level.coerceIn(0f, 1f) * (size.height - minHeight)
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - barWidth - index * step, (size.height - barHeight) / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}
