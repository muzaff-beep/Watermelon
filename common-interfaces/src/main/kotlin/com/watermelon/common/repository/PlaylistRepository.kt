package com.watermelon.common.repository

import com.watermelon.common.model.Playlist
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String): Playlist
    suspend fun addToPlaylist(playlistId: String, mediaUri: String)
    suspend fun removeFromPlaylist(playlistId: String, mediaUri: String)
}
