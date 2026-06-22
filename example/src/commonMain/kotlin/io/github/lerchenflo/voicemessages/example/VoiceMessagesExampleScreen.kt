package io.github.lerchenflo.voicemessages.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.lerchenflo.voicemessages.ui.VoiceMessagePlayer
import io.github.lerchenflo.voicemessages.ui.VoiceMessageRecorder

/**
 * A minimal screen demonstrating kmp-voice-messages end to end: record a message with
 * [VoiceMessageRecorder], then play any recorded message back with [VoiceMessagePlayer].
 */
@Composable
fun VoiceMessagesExampleScreen() {
    val messages = remember { mutableStateListOf<ByteArray>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Text(text = "kmp-voice-messages example", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        VoiceMessageRecorder(onRecorded = { bytes -> messages.add(bytes) })

        Spacer(modifier = Modifier.height(16.dp))

        if (messages.isEmpty()) {
            Text(
                text = "No voice messages yet. Tap the button above to record one.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn {
                items(messages) { bytes ->
                    VoiceMessagePlayer(
                        bytes = bytes,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
