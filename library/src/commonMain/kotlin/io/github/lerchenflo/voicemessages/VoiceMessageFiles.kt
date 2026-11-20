package io.github.lerchenflo.voicemessages

import io.github.lerchenflo.voicemessages.internal.appFilesDirectory
import io.github.lerchenflo.voicemessages.internal.probeDurationMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The voice message files of an app, all in one [directory] and addressed by their bare file name.
 * Messages store only the file name, never an absolute path: on iOS the app's container path
 * changes with app updates. Every function also accepts an old absolute path and uses only its
 * last segment.
 *
 * The directory may hold other files of the app too; [deleteAll] only deletes files whose name
 * ends with [fileNameSuffix].
 *
 * The suspend functions do their file I/O on `Dispatchers.IO`.
 */
class VoiceMessageFiles(
    val directory: String,
    val fileNameSuffix: String = DEFAULT_FILE_NAME_SUFFIX,
) {

    /** Absolute path of the voice message [fileName]. */
    fun path(fileName: String): String = "$directory/${bareName(fileName)}"

    /** Whether the voice message [fileName] exists as a file. False for a blank name. */
    fun exists(fileName: String): Boolean {
        val name = fileName.substringAfterLast('/')
        if (name.isBlank()) return false
        return SystemFileSystem.metadataOrNull(Path(directory, name))?.isRegularFile == true
    }

    /** A new, unused file name for a recording: the current time in milliseconds plus [fileNameSuffix]. */
    @OptIn(ExperimentalTime::class)
    fun newFileName(): String = "${Clock.System.now().toEpochMilliseconds()}$fileNameSuffix"

    /** Writes [bytes] (for example a downloaded voice message) as [fileName] and returns its absolute path. */
    suspend fun save(fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        ensureDirectory()
        val path = path(fileName)
        SystemFileSystem.sink(Path(path)).buffered().use { it.write(bytes) }
        path
    }

    /** Reads the voice message [fileName]. Throws [VoiceMessageException.FileMissing] if it doesn't exist. */
    suspend fun readBytes(fileName: String): ByteArray = withContext(Dispatchers.IO) {
        if (!exists(fileName)) throw VoiceMessageException.FileMissing(path(fileName))
        SystemFileSystem.source(Path(path(fileName))).buffered().use { it.readByteArray() }
    }

    /** Deletes the voice message [fileName]. Returns false if it didn't exist. */
    suspend fun delete(fileName: String): Boolean = withContext(Dispatchers.IO) {
        if (!exists(fileName)) return@withContext false
        SystemFileSystem.delete(Path(path(fileName)), mustExist = false)
        true
    }

    /**
     * Deletes every voice message file in [directory] (name ends with [fileNameSuffix]), for
     * example on logout. Returns the number of deleted files.
     */
    suspend fun deleteAll(): Int = withContext(Dispatchers.IO) {
        val dir = Path(directory)
        if (SystemFileSystem.metadataOrNull(dir)?.isDirectory != true) return@withContext 0
        SystemFileSystem.list(dir)
            .filter { it.name.endsWith(fileNameSuffix) && SystemFileSystem.metadataOrNull(it)?.isRegularFile == true }
            .count { runCatching { SystemFileSystem.delete(it) }.isSuccess }
    }

    /** Length of the voice message [fileName] in milliseconds, read from its header. 0 if it can't be read. */
    suspend fun durationMs(fileName: String): Long = withContext(Dispatchers.IO) {
        if (!exists(fileName)) 0L else probeDurationMs(path(fileName))
    }

    internal fun ensureDirectory() {
        val dir = Path(directory)
        if (!SystemFileSystem.exists(dir)) SystemFileSystem.createDirectories(dir)
    }

    private fun bareName(fileName: String): String {
        val name = fileName.substringAfterLast('/')
        require(name.isNotBlank()) { "No file name in \"$fileName\"" }
        return name
    }

    companion object {
        /** File name ending of voice messages. */
        const val DEFAULT_FILE_NAME_SUFFIX: String = "_voicemsg.m4a"

        /**
         * The app's own persistent storage: `Context.filesDir` on Android, the Documents directory
         * on iOS, and on desktop the app data folder named [desktopAppName] (`%APPDATA%` on
         * Windows, `~/Library/Application Support` on macOS, `~/.local/share` on Linux).
         */
        fun appStorage(desktopAppName: String, fileNameSuffix: String = DEFAULT_FILE_NAME_SUFFIX): VoiceMessageFiles =
            VoiceMessageFiles(appFilesDirectory(desktopAppName), fileNameSuffix)
    }
}
