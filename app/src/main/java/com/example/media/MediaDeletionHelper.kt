package com.example.media

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest

object MediaDeletionHelper {
    /**
     * Prepares deletion for media URIs.
     * On Android 11+ (API 30+), returns an IntentSenderRequest so the caller can launch
     * the system confirmation dialog ("Allow app to delete X photos?").
     * On Android 10 and below, deletes directly via ContentResolver and returns null.
     */
    fun createDeleteRequestOrDeleteDirectly(
        context: Context,
        contentUris: List<String>
    ): IntentSenderRequest? {
        if (contentUris.isEmpty()) return null
        val resolver: ContentResolver = context.contentResolver
        val uriList = contentUris.mapNotNull {
            runCatching { Uri.parse(it) }.getOrNull()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent: PendingIntent? = runCatching {
                MediaStore.createDeleteRequest(resolver, uriList)
            }.getOrNull()
            if (pendingIntent != null) {
                return IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            }
        }
        // Direct delete on Android 10 and below or fallback
        for (uri in uriList) {
            runCatching {
                resolver.delete(uri, null, null)
            }
        }
        return null
    }
}
