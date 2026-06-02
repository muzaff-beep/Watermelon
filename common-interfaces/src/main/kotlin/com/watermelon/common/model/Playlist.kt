package com.watermelon.common.model

/**
 * A user playlist. Referenced by [com.watermelon.common.repository.PlaylistRepository];
 * [items] holds the ordered media URIs that belong to the playlist.
 */
data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val items: List<String> = emptyList()
)
