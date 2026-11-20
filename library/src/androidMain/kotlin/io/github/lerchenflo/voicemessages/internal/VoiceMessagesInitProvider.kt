package io.github.lerchenflo.voicemessages.internal

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

/** The application context, set by [VoiceMessagesInitProvider] when the app process starts. */
internal object VoiceMessagesAppContext {
    @Volatile
    var context: Context? = null
}

/**
 * Registered in the library manifest, so Android creates it before the app's own code runs. It only
 * keeps the application context ([VoiceMessagesAppContext]); VoicePlayer needs it for the
 * AudioManager but is built with a no-argument constructor in shared code.
 */
internal class VoiceMessagesInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        VoiceMessagesAppContext.context = context?.applicationContext
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
