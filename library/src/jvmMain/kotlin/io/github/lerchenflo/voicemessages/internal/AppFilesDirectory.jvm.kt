package io.github.lerchenflo.voicemessages.internal

import java.io.File

internal actual fun appFilesDirectory(desktopAppName: String): String {
    val os = System.getProperty("os.name").lowercase()
    val userHome = System.getProperty("user.home")
    val directory = when {
        os.contains("win") -> File(System.getenv("APPDATA") ?: userHome, desktopAppName)
        os.contains("mac") -> File(userHome, "Library/Application Support/$desktopAppName")
        else -> File(userHome, ".local/share/$desktopAppName")
    }
    directory.mkdirs()
    return directory.absolutePath
}
