package io.github.lerchenflo.voicemessages.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lerchenflo.voicemessages.VoiceMessageFiles
import io.github.lerchenflo.voicemessages.VoicePlaybackController
import io.github.lerchenflo.voicemessages.VoiceRecordingController
import io.github.lerchenflo.voicemessages.VoiceRecordingState
import io.github.lerchenflo.voicemessages.ui.VoiceMessagePlayer
import io.github.lerchenflo.voicemessages.ui.VoiceMessagePlayerTexts
import io.github.lerchenflo.voicemessages.ui.VoiceMessageRecordingBar
import io.github.lerchenflo.voicemessages.ui.VoiceMessageRecordingBarTexts
import kotlinx.coroutines.launch

private val playerTexts = VoiceMessagePlayerTexts(play = "Play", pause = "Pause", position = "Position")
private val recordingBarTexts = VoiceMessageRecordingBarTexts(
    recording = "Recording",
    discard = "Discard",
    player = playerTexts,
)

/**
 * A minimal chat-like screen: record with the button on the right, listen to the draft in the
 * recording bar, "send" it into the list and play the sent messages. In an app the two
 * controllers live in a ViewModel.
 */
@Composable
fun VoiceMessagesExampleScreen() {
    val files = remember { VoiceMessageFiles.appStorage(desktopAppName = "kmp-voice-messages-example") }
    val playback = remember { VoicePlaybackController() }
    val recording = remember { VoiceRecordingController(files, playback) }
    DisposableEffect(Unit) {
        onDispose {
            recording.release()
            playback.release()
        }
    }

    val scope = rememberCoroutineScope()
    val recordingState by recording.state.collectAsState()
    val sent = remember { mutableStateListOf<String>() }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Text(text = "kmp-voice-messages example", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(sent) { fileName ->
                val filePath = files.path(fileName)
                VoiceMessagePlayer(
                    id = fileName,
                    filePath = filePath,
                    playback = playback.state,
                    texts = playerTexts,
                    onPlay = { playback.play(fileName, filePath).onFailure { error = it.message } },
                    onPause = playback::pause,
                    onSeek = { playback.seekTo(fileName, it) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
        }

        error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val draft = recordingState
            if (draft == VoiceRecordingState.Idle) {
                Text(text = "Tap the microphone to record.", modifier = Modifier.weight(1f))
            } else {
                VoiceMessageRecordingBar(
                    state = draft,
                    levels = recording.levels,
                    playback = playback.state,
                    maxDurationMs = recording.maxDurationMs,
                    texts = recordingBarTexts,
                    onPlay = {
                        (draft as? VoiceRecordingState.Recorded)?.let {
                            playback.play(VoiceRecordingController.DRAFT_PLAYBACK_ID, it.filePath)
                                .onFailure { e -> error = e.message }
                        }
                    },
                    onPause = playback::pause,
                    onSeek = { playback.seekTo(VoiceRecordingController.DRAFT_PLAYBACK_ID, it) },
                    onDiscard = { scope.launch { recording.discard() } },
                    modifier = Modifier.weight(1f),
                )
            }

            FilledIconButton(
                onClick = {
                    error = null
                    scope.launch {
                        when (draft) {
                            VoiceRecordingState.Idle -> recording.start().onFailure { error = it.message }
                            is VoiceRecordingState.Recording -> recording.stop().onFailure { error = it.message }
                            is VoiceRecordingState.Recorded -> {
                                sent.add(draft.fileName)
                                recording.clear()
                            }
                        }
                    }
                },
            ) {
                Icon(
                    imageVector = when (draft) {
                        VoiceRecordingState.Idle -> Icons.Filled.Mic
                        is VoiceRecordingState.Recording -> Icons.Filled.Stop
                        is VoiceRecordingState.Recorded -> Icons.AutoMirrored.Filled.Send
                    },
                    contentDescription = when (draft) {
                        VoiceRecordingState.Idle -> "Record"
                        is VoiceRecordingState.Recording -> "Stop"
                        is VoiceRecordingState.Recorded -> "Send"
                    },
                )
            }
        }
    }
}
