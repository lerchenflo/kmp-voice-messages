package io.github.lerchenflo.voicemessages.internal

internal actual fun appFilesDirectory(desktopAppName: String): String {
    val context = VoiceMessagesAppContext.context
        ?: error("No application context: the library's VoiceMessagesInitProvider was removed from the app manifest")
    return context.filesDir.absolutePath
}
