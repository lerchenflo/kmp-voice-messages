package io.github.lerchenflo.voicemessages.internal

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Voice messages are speech played on the media stream (speaker, headphones, Bluetooth), not a call,
 * an alarm or a notification. CONTENT_TYPE_SPEECH also stops Android from just lowering the volume
 * of a voice message when a navigation prompt plays: the player is told to pause instead.
 */
internal val voiceMessageAudioAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/**
 * Transient audio focus for one voice message: other apps pause their audio (no ducking) while it
 * is held and continue once it is abandoned. [focusGain] is AUDIOFOCUS_GAIN_TRANSIENT for playback
 * and AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE for recording (no notification sounds either). [onLoss]
 * runs on the main thread when another app takes the audio (call, video, navigation prompt, other
 * music app).
 */
internal class VoiceMessageAudioFocus(
    private val focusGain: Int,
    private val onLoss: () -> Unit = {},
) {

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onLoss()
        }
    }

    // AudioFocusRequest exists from API 26 on; typed as Any so API 24 and 25 never load the class
    private var focusRequest: Any? = null

    @Volatile
    private var held = false

    private val audioManager: AudioManager?
        get() = VoiceMessagesAppContext.context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * Requests the audio focus. Returns false when Android denies it, for example during a phone
     * call. Without an application context (the library's init provider was removed from the app
     * manifest) there is no AudioManager: playback then goes on without audio focus, as before 1.0.2.
     */
    fun request(): Boolean {
        if (held) return true
        val manager = audioManager ?: return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.requestAudioFocus(focusRequest())
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, focusGain)
        }
        held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return held
    }

    /** Hands the audio back, so the app that played before continues. No-op when not held. */
    fun abandon() {
        if (!held) return
        held = false
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.abandonAudioFocusRequest(focusRequest())
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(listener)
        }
    }

    // One request object for request and abandon: Android matches the abandon call by this object
    private fun focusRequest(): AudioFocusRequest {
        (focusRequest as? AudioFocusRequest)?.let { return it }
        return AudioFocusRequest.Builder(focusGain)
            .setAudioAttributes(voiceMessageAudioAttributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper()))
            .build()
            .also { focusRequest = it }
    }
}
