package io.github.lerchenflo.voicemessages

/**
 * Records a voice message to a file on disk.
 *
 * Each platform records into its own native audio container (AAC/m4a on Android & iOS, WAV
 * on desktop).
 *
 * Other apps' audio (music, podcasts) pauses while a recording runs, so it doesn't end up in the
 * recording, and may continue once the recording is stopped or fails to start: on Android through
 * transient exclusive audio focus, on iOS through the shared AVAudioSession (category Record).
 *
 * On Android, the caller is responsible for requesting the `RECORD_AUDIO` permission before
 * calling [start]. On iOS, `NSMicrophoneUsageDescription` must be set in `Info.plist`.
 */
expect class VoiceRecorder() {

    /** Whether a recording is currently in progress. */
    val isRecording: Boolean

    /**
     * Current microphone input level while recording, from 0 (silence) to 1 (loud), for a level
     * meter or a moving recording visualizer. The scale is linear in decibels from -60 dBFS (0) to
     * full scale (1), so normal speech moves it over most of the range. It is the peak of the most
     * recent audio (on Android the peak since the previous read, so read it from one place only,
     * every 50 to 100 ms). 0 when no recording runs.
     */
    val inputLevel: Float

    /**
     * Starts recording into [filePath], overwriting it if it already exists.
     *
     * @throws IllegalStateException on Android if the audio focus is denied, for example during a
     * phone call (iOS: if the audio session can't be activated). Nothing records then.
     */
    suspend fun start(filePath: String)

    /**
     * Stops the current recording, finalizing the file, and hands the audio back to other apps.
     * No-op if not currently recording.
     */
    fun stop()
}
