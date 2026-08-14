package com.example.media

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.example.core.enum.MediaType
import com.example.core.model.SystemMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreDataSource(private val context: Context) {
    private val contentResolver: ContentResolver = context.contentResolver

    suspend fun queryAll(): List<SystemMedia> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SystemMedia>()
        result.addAll(queryImages(since = 0L))
        result.addAll(queryVideos(since = 0L))
        result.sortedBy { it.dateAdded }
    }

    suspend fun queryLatest(sinceAddedAt: Long): List<SystemMedia> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SystemMedia>()
        result.addAll(queryImages(since = sinceAddedAt))
        result.addAll(queryVideos(since = sinceAddedAt))
        result.sortedBy { it.dateAdded }
    }

    suspend fun queryByUri(uri: Uri): SystemMedia? = withContext(Dispatchers.IO) {
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return@withContext null
        val isVideo = uri.toString().contains("video", ignoreCase = true)
        val targetTable = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE

        val projection = getProjection()
        val selection = "${MediaStore.MediaColumns._ID} = ?"
        val selectionArgs = arrayOf(id.toString())

        contentResolver.query(targetTable, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                parseSystemMedia(cursor, mediaType, targetTable)
            } else null
        }
    }

    suspend fun exists(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use {
                it.moveToFirst()
            } ?: false
        }.getOrDefault(false)
    }

    suspend fun loadThumbnail(uri: Uri, width: Int = 256, height: Int = 256): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(uri, Size(width, height), null)
            } else {
                null
            }
        }.getOrNull()
    }

    fun createDeleteRequest(uris: List<Uri>): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(contentResolver, uris)
        } else {
            null
        }
    }

    private fun queryImages(since: Long): List<SystemMedia> {
        val list = mutableListOf<SystemMedia>()
        val projection = getProjection()
        val selection = if (since > 0) "${MediaStore.Images.Media.DATE_ADDED} > ?" else null
        val selectionArgs = if (since > 0) arrayOf(since.toString()) else null
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} ASC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                parseSystemMedia(cursor, MediaType.IMAGE, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)?.let {
                    list.add(it)
                }
            }
        }
        return list
    }

    private fun queryVideos(since: Long): List<SystemMedia> {
        val list = mutableListOf<SystemMedia>()
        val projection = getProjection()
        val selection = if (since > 0) "${MediaStore.Video.Media.DATE_ADDED} > ?" else null
        val selectionArgs = if (since > 0) arrayOf(since.toString()) else null
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} ASC"

        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                parseSystemMedia(cursor, MediaType.VIDEO, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)?.let {
                    list.add(it)
                }
            }
        }
        return list
    }

    private fun getProjection(): Array<String> {
        val base = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            base.add(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        }
        return base.toTypedArray()
    }

    private fun parseSystemMedia(
        cursor: Cursor,
        mediaType: MediaType,
        contentUriBase: Uri
    ): SystemMedia? {
        val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
        if (idIndex == -1) return null

        val id = cursor.getLong(idIndex)
        val contentUri = ContentUris.withAppendedId(contentUriBase, id).toString()

        val displayName = cursor.getStringOrNull(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeType = cursor.getStringOrNull(MediaStore.MediaColumns.MIME_TYPE)
        val relativePath = cursor.getStringOrNull(MediaStore.MediaColumns.RELATIVE_PATH)
        val bucketName = cursor.getStringOrNull(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        val width = cursor.getIntOrNull(MediaStore.MediaColumns.WIDTH)
        val height = cursor.getIntOrNull(MediaStore.MediaColumns.HEIGHT)
        val sizeBytes = cursor.getLongOrNull(MediaStore.MediaColumns.SIZE)
        val dateTaken = cursor.getLongOrNull(MediaStore.MediaColumns.DATE_TAKEN)
        val dateAdded = cursor.getLongOrNull(MediaStore.MediaColumns.DATE_ADDED) ?: (System.currentTimeMillis() / 1000)

        val ownerPackage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getStringOrNull(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        } else null

        // Convert date_added in seconds to ms if needed, and dateTaken is in ms
        val normalizedDateAdded = if (dateAdded < 10000000000L) dateAdded * 1000 else dateAdded
        val normalizedDateTaken = dateTaken?.let { if (it < 10000000000L) it * 1000 else it } ?: normalizedDateAdded

        return SystemMedia(
            mediaStoreId = id,
            contentUri = contentUri,
            mediaType = mediaType,
            mimeType = mimeType,
            displayName = displayName,
            ownerPackage = ownerPackage,
            relativePath = relativePath,
            bucketName = bucketName,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            dateTaken = normalizedDateTaken,
            dateAdded = normalizedDateAdded
        )
    }

    private fun Cursor.getStringOrNull(column: String): String? {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else null
    }

    private fun Cursor.getIntOrNull(column: String): Int? {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getInt(idx) else null
    }

    private fun Cursor.getLongOrNull(column: String): Long? {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getLong(idx) else null
    }
}
