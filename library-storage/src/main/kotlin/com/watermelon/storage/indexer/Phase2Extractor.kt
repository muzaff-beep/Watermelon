package com.watermelon.storage.indexer

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.watermelon.storage.db.WatermelonDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Phase 2 — background codec/duration extraction via [MediaMetadataRetriever]. Updates SQLite
 * row-by-row on a low-priority background thread (Manifest §5.2 / Teams §4 Implementation
 * Notes). Heavy work stays off the main thread to preserve ANR safety.
 */
class Phase2Extractor(
    private val context: Context,
    private val database: WatermelonDatabase,
    private val dispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "watermelon-phase2").apply {
                priority = Thread.MIN_PRIORITY
            }
        }.asCoroutineDispatcher()
) {
    private val contentResolver: ContentResolver = context.contentResolver

    /** Extract metadata for each [uris] entry and upsert into MediaItems. */
    suspend fun extract(uris: List<String>) = withContext(dispatcher) {
        val db = database.writableDatabase
        for (uriString in uris) {
            val values = extractOne(uriString) ?: continue
            db.insertWithOnConflict(
                "MediaItems", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    private fun extractOne(uriString: String): ContentValues? = runCatching {
        val uri = Uri.parse(uriString)
        val retriever = MediaMetadataRetriever()
        retriever.use { r ->
            r.setDataSource(context, uri)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val mime = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            val (displayName, fileSize, parentFolder) = queryMediaStoreBasics(uri)
            ContentValues().apply {
                put("mediaId", uriString)
                put("fileSize", fileSize)
                put("displayName", displayName)
                put("parentFolder", parentFolder)
                put("durationMs", duration)
                put("width", width)
                put("height", height)
                put("mimeType", mime)
            }
        }
    }.getOrNull()

    private fun queryMediaStoreBasics(uri: Uri): Triple<String, Long, String> {
        val projection = arrayOf(
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0) ?: ""
                val size = c.getLong(1)
                val folder = c.getString(2) ?: ""
                return Triple(name, size, folder)
            }
        }
        return Triple("", 0L, "")
    }
}
