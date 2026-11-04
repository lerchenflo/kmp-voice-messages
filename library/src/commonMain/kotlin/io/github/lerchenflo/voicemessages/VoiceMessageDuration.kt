package io.github.lerchenflo.voicemessages

import io.github.lerchenflo.voicemessages.internal.probeDurationMs

/**
 * Length of the voice message file at [filePath] in milliseconds, or 0 if it can't be read.
 * On desktop this reads WAV and AAC in MP4 (.m4a, as recorded on Android and iOS).
 * Blocking file I/O: call it off the main thread.
 */
fun voiceMessageDurationMs(filePath: String): Long = probeDurationMs(filePath)
