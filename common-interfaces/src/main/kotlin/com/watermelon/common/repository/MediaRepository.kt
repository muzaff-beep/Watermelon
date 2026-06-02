package com.watermelon.common.repository

import com.watermelon.common.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    /** Full list of all indexed media, emitted on every change. */
    fun observeAllMedia(): Flow<List<MediaItem>>

    /** Single media item by its content URI. Returns null if not indexed. */
    suspend fun getByUri(uri: String): MediaItem?

    /** Trigger a re-index. Phase-1 immediate; Phase-2 background. */
    suspend fun refreshIndex()
}
