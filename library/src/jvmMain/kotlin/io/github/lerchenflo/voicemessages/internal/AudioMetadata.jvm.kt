package io.github.lerchenflo.voicemessages.internal

import java.io.File
import javax.sound.sampled.AudioSystem

internal actual fun probeDurationMs(filePath: String): Long {
    val file = File(filePath)
    return try {
        // Java Sound can't read the AAC (.m4a) messages recorded on Android and iOS
        if (isMp4File(file)) mp4DurationMs(file) else javaSoundDurationMs(file)
    } catch (e: Exception) {
        0L
    }
}

private fun javaSoundDurationMs(file: File): Long {
    val format = AudioSystem.getAudioFileFormat(file)
    val frameLength = format.frameLength
    val frameRate = format.format.frameRate
    return if (frameLength != AudioSystem.NOT_SPECIFIED && frameRate > 0f) {
        ((frameLength / frameRate) * 1_000).toLong()
    } else {
        0L
    }
}
