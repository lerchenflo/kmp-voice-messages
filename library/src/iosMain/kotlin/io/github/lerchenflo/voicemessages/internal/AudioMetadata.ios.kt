package io.github.lerchenflo.voicemessages.internal

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVURLAsset
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
internal actual fun probeDurationMs(filePath: String): Long {
    // A blank path (a message whose file isn't downloaded yet) makes fileURLWithPath return nil,
    // which Kotlin turns into a NullPointerException
    if (filePath.isBlank() || !NSFileManager.defaultManager.fileExistsAtPath(filePath)) return 0L
    return try {
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(filePath), options = null)
        val seconds = CMTimeGetSeconds(asset.duration)
        if (seconds.isFinite() && seconds > 0) (seconds * 1_000).toLong() else 0L
    } catch (e: Exception) {
        0L
    }
}
