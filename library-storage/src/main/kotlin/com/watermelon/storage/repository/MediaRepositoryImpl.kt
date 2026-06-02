package com.watermelon.storage.repository

import com.watermelon.common.model.MediaItem
import com.watermelon.common.repository.MediaRepository
import com.watermelon.storage.db.WatermelonDatabase
import com.watermelon.storage.indexer.MediaStoreIndexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * [MediaRepository] backed by the SQLite MediaItems table (Phase-2 cache) and the
 * [MediaStoreIndexer]. `observeAllMedia` re-emits whenever the index changes.
 */
class MediaRepositoryImpl(
    private val database: WatermelonDatabase,
    private val indexer: MediaStoreIndexer
) : MediaRepository {

    private val mediaFlow = MutableStateFlow<List<MediaItem>>(emptyList())

    override fun observeAllMedia(): Flow<List<MediaItem>> = mediaFlow.asStateFlow()

    override suspend fun getByUri(uri: String): MediaItem? = withContext(Dispatchers.IO) {
        database.readableDatabase.query(
            "MediaItems", null, "mediaId = ?", arrayOf(uri), null, null, null
        ).use { c -> if (c.moveToFirst()) c.toMediaItem() else null }
    }

    override suspend fun refreshIndex() {
        indexer.refresh(force = true)
        reloadCache()
    }

    private suspend fun reloadCache() = withContext(Dispatchers.IO) {
        val items = ArrayList<MediaItem>()
        database.readableDatabase.query(
            "MediaItems", null, null, null, null, null, "displayName ASC"
        ).use { c -> while (c.moveToNext()) items += c.toMediaItem() }
        mediaFlow.value = items
    }

    private fun android.database.Cursor.toMediaItem() = MediaItem(
        uri = getString(getColumnIndexOrThrow("mediaId")),
        fileSize = getLong(getColumnIndexOrThrow("fileSize")),
        displayName = getString(getColumnIndexOrThrow("displayName")),
        parentFolder = getString(getColumnIndexOrThrow("parentFolder")),
        durationMs = getLong(getColumnIndexOrThrow("durationMs")),
        width = getInt(getColumnIndexOrThrow("width")),
        height = getInt(getColumnIndexOrThrow("height")),
        mimeType = getString(getColumnIndexOrThrow("mimeType")) ?: ""
    )
}
