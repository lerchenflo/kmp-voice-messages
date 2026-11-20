package io.github.lerchenflo.voicemessages

/**
 * Plays a voice message back from a file on disk.
 *
 * Other apps' audio (music, podcasts) pauses while a voice message plays and continues once it
 * no longer plays: on Android through transient audio focus, on iOS through the shared
 * AVAudioSession. When another app takes the audio (a phone call, a video, a navigation prompt,
 * another music app), the voice message pauses ([isPaused] turns true) and stays paused until
 * [resume] is called.
 */
expect class VoicePlayer() {

    /** Current playback position, in milliseconds. 0 if nothing is playing. */
    val positionMs: Long

    /** Duration of the voice message currently loaded, in milliseconds. 0 if nothing is loaded. */
    val durationMs: Long

    /**
     * Whether playback is currently in progress. False while paused as well as after the end of
     * the voice message or [stop]; read [isPaused] to tell them apart.
     */
    val isPlaying: Boolean

    /**
     * Whether a voice message is loaded and paused, so [resume] continues it at [positionMs].
     * True after [pause], and on Android and iOS also after the system paused playback because
     * another app took the audio. False while playing, after the voice message reached its end,
     * after [stop], after a failed [play] and before the first [play].
     */
    val isPaused: Boolean

    /**
     * Starts playing the voice message at [filePath] from the beginning.
     *
     * @throws IllegalStateException if the audio can't be taken from another app, for example
     * during a phone call (Android: audio focus denied, iOS: audio session not activated).
     * Nothing plays then.
     */
    fun play(filePath: String)

    /** Pauses playback and hands the audio back to other apps. No-op if not currently playing. */
    fun pause()

    /**
     * Resumes playback from the paused position and takes the audio from other apps again.
     * No-op if currently playing.
     *
     * @throws IllegalStateException if the audio can't be taken from another app, for example
     * during a phone call. The voice message then stays paused.
     */
    fun resume()

    /** Seeks to [positionMs] in the currently loaded voice message. */
    fun seekTo(positionMs: Long)

    /** Stops playback, releases the underlying player and hands the audio back to other apps. */
    fun stop()
}
