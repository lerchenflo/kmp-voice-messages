package io.github.lerchenflo.voicemessages

import android.media.MediaPlayer
import io.github.lerchenflo.voicemessages.internal.VoiceMessageAudioFocus
import io.github.lerchenflo.voicemessages.internal.voiceMessageAudioAttributes

actual class VoicePlayer actual constructor() {

    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    @Volatile
    private var paused: Boolean = false

    private val audioFocus = VoiceMessageAudioFocus(onLoss = ::pauseForFocusLoss)

    actual val positionMs: Long
        get() = mediaPlayer?.let { runCatching { it.currentPosition.toLong() }.getOrDefault(0L) } ?: 0L

    actual val durationMs: Long
        get() = mediaPlayer?.let { runCatching { it.duration.toLong() }.getOrDefault(0L) } ?: 0L

    actual val isPlaying: Boolean
        get() = mediaPlayer?.let { runCatching { it.isPlaying }.getOrDefault(false) } ?: false

    actual val isPaused: Boolean
        get() = paused

    actual fun play(filePath: String) {
        stop()
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(voiceMessageAudioAttributes)
            player.setDataSource(filePath)
            player.prepare()
        } catch (e: Exception) {
            player.release()
            throw e
        }
        player.setOnCompletionListener { onPlaybackEnded(it) }
        player.setOnErrorListener { erroredPlayer, _, _ ->
            onPlaybackEnded(erroredPlayer)
            false
        }
        mediaPlayer = player

        if (!audioFocus.request()) {
            stop()
            throw IllegalStateException(AUDIO_FOCUS_DENIED)
        }
        try {
            player.start()
        } catch (e: Exception) {
            stop()
            throw e
        }
    }

    actual fun pause() {
        val player = mediaPlayer ?: return
        if (player.isPlayingSafe()) {
            player.pause()
            paused = true
        }
        audioFocus.abandon()
    }

    actual fun resume() {
        val player = mediaPlayer ?: return
        if (player.isPlayingSafe()) return
        // Stays paused when another app holds the audio, for example during a phone call
        check(audioFocus.request()) { AUDIO_FOCUS_DENIED }
        player.start()
        paused = false
    }

    actual fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
    }

    actual fun stop() {
        val player = mediaPlayer
        mediaPlayer = null
        paused = false
        if (player != null) {
            runCatching { player.stop() }
            player.release()
        }
        audioFocus.abandon()
    }

    // Another app took the audio: pause and hand the audio back. Playback does not continue by
    // itself when the audio comes back, the user resumes it
    private fun pauseForFocusLoss() {
        val player = mediaPlayer
        if (player != null && player.isPlayingSafe()) {
            player.pause()
            paused = true
        }
        audioFocus.abandon()
    }

    // End of the voice message or a playback error: not resumable any more, hand the audio back
    private fun onPlaybackEnded(player: MediaPlayer) {
        if (player !== mediaPlayer) return
        paused = false
        audioFocus.abandon()
    }

    private fun MediaPlayer.isPlayingSafe(): Boolean = runCatching { isPlaying }.getOrDefault(false)

    private companion object {
        const val AUDIO_FOCUS_DENIED = "Audio focus denied: another app holds the audio, for example a phone call"
    }
}
