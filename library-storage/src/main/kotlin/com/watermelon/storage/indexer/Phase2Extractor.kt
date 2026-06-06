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
 * row-by-row on a low-priority background thread (Manifest §5.2 / Teams §4).
 *
 * Two-query upsert strategy (preserves [firstSeenAt] and [lastPlayedAt]):
 *  1. INSERT OR IGNORE — writes all fields including [firstSeenAt] = now. No-op if the row
 *     already exists, so [firstSeenAt] is set exactly once (when the URI is first indexed).
 *  2. UPDATE — refreshes metadata fields only. Never touches [firstSeenAt] or [lastPlayedAt],
 *     so the ⭐ new-file badge is not reset by re-indexing.
 */
class Phase2Extractor(
    private val context: Context,
    private val database: WatermelonDatabase,
    private val dispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "watermelon-phase2").apply { priority = Thread.MIN_PRIORITY }
        }.asCoroutineDispatcher()
) {
    private val contentResolver: ContentResolver = context.contentResolver

    suspend fun extract(uris: List<String>) = withContext(dispatcher) {
        val db = database.writableDatabase
        val now = System.currentTimeMillis()
        for (uriString in uris) {
            val values = extractOne(uriString) ?: continue

            // 1. INSERT OR IGNORE: sets firstSeenAt only on new rows.
            db.execSQL(
                """INSERT OR IGNORE INTO MediaItems
                   (mediaId, fileSize, displayName, parentFolder,
                    durationMs, width, height, mimeType, firstSeenAt)
                   VALUES (?,?,?,?,?,?,?,?,?)""",
                arrayOf(
                    uriString,
                    values.getAsLong("fileSize"),
                    values.getAsString("displayName"),
                    values.getAsString("parentFolder"),
                    values.getAsLong("durationMs"),
                    values.getAsInteger("width"),
                    values.getAsInteger("height"),
                    values.getAsString("mimeType"),
                    now
                )
            )

            // 2. UPDATE: refresh metadata without touching firstSeenAt or lastPlayedAt.
            db.execSQL(
                """UPDATE MediaItems SET
                   fileSize=?, displayName=?, parentFolder=?,
                   durationMs=?, width=?, height=?, mimeType=?
                   WHERE mediaId=?""",
                arrayOf(
                    values.getAsLong("fileSize"),
                    values.getAsString("displayName"),
                    values.getAsString("parentFolder"),
                    values.getAsLong("durationMs"),
                    values.getAsInteger("width"),
                    values.getAsInteger("height"),
                    values.getAsString("mimeType"),
                    uriString
                )
            )
        }
    }

    private fun extractOne(uriString: String): ContentValues? = runCatching {
        val uri = Uri.parse(uriString)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            val (displayName, fileSize, parentFolder) = queryMediaStoreBasics(uri)
            ContentValues().apply {
                put("fileSize", fileSize)
                put("displayName", displayName)
                put("parentFolder", parentFolder)
                put("durationMs", duration)
                put("width", width)
                put("height", height)
                put("mimeType", mime)
            }
        } finally {
            retriever.release()
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
                return Triple(c.getString(0) ?: "", c.getLong(1), c.getString(2) ?: "")
            }
        }
        return Triple("", 0L, "")
    }
}
