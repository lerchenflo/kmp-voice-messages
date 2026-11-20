package io.github.lerchenflo.voicemessages

import android.media.AudioManager
import android.media.MediaRecorder
import io.github.lerchenflo.voicemessages.internal.VoiceMessageAudioFocus
import io.github.lerchenflo.voicemessages.internal.inputLevelFromPeak

actual class VoiceRecorder actual constructor() {

    private var mediaRecorder: MediaRecorder? = null
    private var recording: Boolean = false

    // Exclusive: other apps pause (so their music isn't recorded) and notification sounds stay
    // quiet. A focus loss while recording (incoming call, other music app) keeps the recording
    // running; the app's own stop() ends it
    private val audioFocus = VoiceMessageAudioFocus(focusGain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)

    actual val isRecording: Boolean
        get() = recording

    actual val inputLevel: Float
        get() {
            val recorder = mediaRecorder?.takeIf { recording } ?: return 0f
            // Peak since the previous call, 0..32767
            val amplitude = runCatching { recorder.maxAmplitude }.getOrDefault(0)
            return inputLevelFromPeak(amplitude / 32_767f)
        }

    actual suspend fun start(filePath: String) {
        // Taken before the microphone starts, so the other app's music has stopped as early as possible
        if (!audioFocus.request()) {
            throw VoiceMessageException.AudioBusy("Audio focus denied: another app holds the audio, for example a phone call")
        }

        @Suppress("DEPRECATION") // No-arg constructor works on all supported API levels.
        val recorder = MediaRecorder()
        try {
            // VOICE_COMMUNICATION enables the device's built-in echo cancellation/noise
            // suppression/AGC, intended for voice calls rather than general audio capture.
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            // Tuned for voice rather than music: mono, 16kHz (well above speech bandwidth),
            // 64kbps (the standard "sweet spot" for mono voice at this sample rate).
            recorder.setAudioEncodingBitRate(64_000)
            recorder.setAudioSamplingRate(16_000)
            recorder.setAudioChannels(1)
            recorder.setOutputFile(filePath)
            recorder.prepare()
            recorder.start()
        } catch (e: Exception) {
            recorder.release()
            audioFocus.abandon()
            throw e
        }
        mediaRecorder = recorder
        recording = true
    }

    actual fun stop() {
        val recorder = mediaRecorder
        mediaRecorder = null
        recording = false
        if (recorder != null) {
            runCatching { recorder.stop() }
            recorder.release()
        }
        audioFocus.abandon()
    }
}
