package io.github.lerchenflo.voicemessages.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.lerchenflo.voicemessages.VoicePlaybackController
import io.github.lerchenflo.voicemessages.VoicePlaybackState
import io.github.lerchenflo.voicemessages.formatVoiceMessageTime
import io.github.lerchenflo.voicemessages.internal.probeDurationMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Screen reader texts of [VoiceMessagePlayer], from the app's own string resources.
 *
 * @property play description of the play button.
 * @property pause description of the pause button.
 * @property position description of the position slider.
 */
@Immutable
data class VoiceMessagePlayerTexts(
    val play: String,
    val pause: String,
    val position: String,
)

/**
 * A voice message row: play/pause button, position slider and the time as "m:ss/\nm:ss"
 * (current/total). Shows the progress of [playback] when it has [id] selected; before the first
 * play the length is read from the file header (or taken from [durationMs]).
 *
 * Dragging or tapping the slider pauses the message and seeks on release ([onSeek], wire it to
 * [VoicePlaybackController.seekTo]); for a message that is not the selected one this selects it
 * paused at that point.
 *
 * @param id the message, as used with [VoicePlaybackController.play].
 * @param filePath absolute path of the file, only read here for its length.
 * @param playback [VoicePlaybackController.state]. Collected inside, so the progress ticks
 * recompose only this row.
 * @param color button, played track, thumb and text; the rest of the track uses it at 30 %.
 * Pass a `MaterialTheme.colorScheme` role, for example `onPrimaryContainer` in the own bubble.
 * @param durationMs known length, for example a freshly measured recording; 0 reads it from the file.
 * @param formatTime formats the two times.
 */
@Composable
fun VoiceMessagePlayer(
    id: String,
    filePath: String,
    playback: StateFlow<VoicePlaybackState>,
    texts: VoiceMessagePlayerTexts,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    durationMs: Long = 0L,
    formatTime: (Long) -> String = ::formatVoiceMessageTime,
) {
    val state by playback.collectAsState()
    val isSelected = state.id == id
    val isPlaying = isSelected && state.isPlaying

    var fileDurationMs by remember(filePath) { mutableLongStateOf(durationMs) }
    LaunchedEffect(filePath, durationMs) {
        fileDurationMs = if (durationMs > 0) durationMs else withContext(Dispatchers.IO) { probeDurationMs(filePath) }
    }
    val duration = if (isSelected && state.durationMs > 0) state.durationMs else fileDurationMs
    val position = if (isSelected) state.positionMs else 0L

    // Position under the finger while dragging, null otherwise
    var dragPositionMs by remember(id) { mutableStateOf<Float?>(null) }
    val shownPositionMs = (dragPositionMs ?: position.toFloat()).coerceIn(0f, duration.toFloat().coerceAtLeast(0f))

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { if (isPlaying) onPause() else onPlay() }) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) texts.pause else texts.play,
                tint = color,
            )
        }

        Spacer(Modifier.width(4.dp))

        Slider(
            value = shownPositionMs,
            onValueChange = {
                if (dragPositionMs == null && isPlaying) onPause()
                dragPositionMs = it
            },
            onValueChangeFinished = {
                dragPositionMs?.let { onSeek(it.toLong()) }
                dragPositionMs = null
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.3f),
            ),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = texts.position },
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = "${formatTime(shownPositionMs.toLong())}/\n${formatTime(duration)}",
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
