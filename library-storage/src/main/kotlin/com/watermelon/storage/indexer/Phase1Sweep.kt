package com.watermelon.storage.indexer

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import com.watermelon.common.model.FolderNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 1 — fast, cheap MediaStore cursor scan. Builds the folder tree from the `BUCKET`
 * columns and emits it immediately so the UI can render before Phase 2 metadata extraction
 * completes (Manifest §5.2). Runs on [Dispatchers.IO].
 */
class Phase1Sweep(private val contentResolver: ContentResolver) {

    /** Lightweight result row from the sweep — just enough to group into folders. */
    data class SweepRow(
        val uri: String,
        val displayName: String,
        val bucketId: String,
        val bucketName: String,
        val relativePath: String
    )

    suspend fun sweep(): List<FolderNode> = withContext(Dispatchers.IO) {
        val rows = queryVideos()
        buildFolderTree(rows)
    }

    private fun queryVideos(): List<SweepRow> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.RELATIVE_PATH
        )
        val out = ArrayList<SweepRow>()
        contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                out += SweepRow(
                    uri = uri,
                    displayName = cursor.getString(nameCol) ?: "",
                    bucketId = cursor.getString(bucketIdCol) ?: "",
                    bucketName = cursor.getString(bucketNameCol) ?: "Unknown",
                    relativePath = cursor.getString(pathCol) ?: ""
                )
            }
        }
        return out
    }

    /** Group flat rows into a one-level folder tree (empty directories are hidden). */
    private fun buildFolderTree(rows: List<SweepRow>): List<FolderNode> =
        rows.groupBy { it.relativePath.ifEmpty { it.bucketName } }
            .filterValues { it.isNotEmpty() }
            .map { (path, items) ->
                FolderNode(
                    path = path,
                    displayName = items.first().bucketName,
                    itemCount = items.size,
                    children = emptyList()
                )
            }
            .sortedBy { it.displayName.lowercase() }
}
