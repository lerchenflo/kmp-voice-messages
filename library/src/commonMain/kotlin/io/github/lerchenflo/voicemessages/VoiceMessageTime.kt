package io.github.lerchenflo.voicemessages

/** Formats a time of a voice message as `m:ss`, e.g. 65 000 ms as "1:05". */
fun formatVoiceMessageTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
