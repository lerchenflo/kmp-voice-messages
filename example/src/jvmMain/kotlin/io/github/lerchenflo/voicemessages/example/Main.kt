package io.github.lerchenflo.voicemessages.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "kmp-voice-messages example") {
        MaterialTheme {
            Surface {
                VoiceMessagesExampleScreen()
            }
        }
    }
}
