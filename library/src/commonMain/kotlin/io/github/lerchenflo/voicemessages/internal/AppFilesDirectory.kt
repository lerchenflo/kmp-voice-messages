package io.github.lerchenflo.voicemessages.internal

/**
 * Absolute path of the app's own persistent file storage. [desktopAppName] names the app data
 * folder on desktop and is ignored on Android and iOS.
 */
internal expect fun appFilesDirectory(desktopAppName: String): String
