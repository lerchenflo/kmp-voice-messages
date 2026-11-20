package io.github.lerchenflo.voicemessages

/**
 * Why playing or recording a voice message failed. Thrown by [VoicePlayer] and [VoiceRecorder], and
 * returned as `Result.failure` by [VoicePlaybackController] and [VoiceRecordingController], so the
 * app decides whether to show an error.
 *
 * All of them are [IllegalStateException]s.
 */
sealed class VoiceMessageException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause) {

    /**
     * Another app holds the audio, for example during a phone call: Android denied the audio
     * focus, or iOS could not activate the audio session. Nothing plays or records.
     */
    class AudioBusy(message: String, cause: Throwable? = null) : VoiceMessageException(message, cause)

    /** The voice message file at [filePath] does not exist. */
    class FileMissing(val filePath: String) : VoiceMessageException("Voice message file not found: $filePath")

    /** Playing or recording failed for another reason, see [cause]. */
    class Failed(message: String, cause: Throwable? = null) : VoiceMessageException(message, cause)
}

/** Keeps a [VoiceMessageException] as it is and wraps every other error in [VoiceMessageException.Failed]. */
internal fun Throwable.toVoiceMessageException(message: String): VoiceMessageException =
    this as? VoiceMessageException ?: VoiceMessageException.Failed("$message: ${this.message}", this)
