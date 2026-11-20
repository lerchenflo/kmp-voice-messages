package io.github.lerchenflo.voicemessages

import io.github.lerchenflo.voicemessages.internal.decodeMp4Aac
import io.github.lerchenflo.voicemessages.internal.isMp4File
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent

actual class VoicePlayer actual constructor() {

    @Volatile
    private var clip: Clip? = null

    // Clip.isRunning only turns true once the line really plays, a few ms after start(), so a
    // progress poll right after play() would take the audio as finished. Track the state here
    // instead: set on play/resume, cleared on pause/stop and when the clip reaches its end.
    @Volatile
    private var playing: Boolean = false

    // Set by pause() only: the desktop has no other app that takes the audio away
    @Volatile
    private var paused: Boolean = false

    actual val positionMs: Long
        get() = clip?.let { it.microsecondPosition / 1_000 } ?: 0L

    actual val durationMs: Long
        get() = clip?.let { it.microsecondLength / 1_000 } ?: 0L

    actual val isPlaying: Boolean
        get() = playing

    actual val isPaused: Boolean
        get() = paused

    actual fun play(filePath: String, startPositionMs: Long) {
        stop()
        val file = File(filePath)
        val newClip = AudioSystem.getClip()
        try {
            if (isMp4File(file)) {
                // AAC in MP4 (.m4a), recorded on Android or iOS
                val decoded = decodeMp4Aac(file)
                newClip.open(decoded.format, decoded.data, 0, decoded.data.size)
            } else {
                // WAV, recorded on the desktop
                AudioSystem.getAudioInputStream(file).use { newClip.open(it) }
            }
        } catch (e: Exception) {
            newClip.close()
            throw e
        }
        newClip.addLineListener { event ->
            // STOP also fires on pause(); only the end of the audio ends playback here
            if (event.type == LineEvent.Type.STOP && newClip.framePosition >= newClip.frameLength) {
                playing = false
                paused = false
            }
        }
        if (startPositionMs > 0) newClip.microsecondPosition = startPositionMs * 1_000
        clip = newClip
        playing = true
        newClip.start()
    }

    actual fun pause() {
        val currentClip = clip ?: return
        if (playing && currentClip.framePosition < currentClip.frameLength) paused = true
        playing = false
        if (currentClip.isRunning) currentClip.stop()
    }

    actual fun resume() {
        val currentClip = clip ?: return
        if (currentClip.framePosition >= currentClip.frameLength) return
        playing = true
        paused = false
        if (!currentClip.isRunning) currentClip.start()
    }

    actual fun seekTo(positionMs: Long) {
        clip?.microsecondPosition = positionMs * 1_000
    }

    actual fun stop() {
        playing = false
        paused = false
        val currentClip = clip ?: return
        currentClip.stop()
        currentClip.close()
        clip = null
    }
}
