package com.watermelon.storage.repository

import android.content.ContentValues
import com.watermelon.common.model.Playlist
import com.watermelon.common.repository.PlaylistRepository
import com.watermelon.storage.db.WatermelonDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * [PlaylistRepository] backed by the Playlists + PlaylistItems tables (Handover §2.2).
 */
class PlaylistRepositoryImpl(
    private val database: WatermelonDatabase
) : PlaylistRepository {

    private val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())

    init { reload() }

    override fun observePlaylists(): Flow<List<Playlist>> = playlistsFlow.asStateFlow()

    override suspend fun createPlaylist(name: String): Playlist = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val playlist = Playlist(UUID.randomUUID().toString(), name, now, now, emptyList())
        database.writableDatabase.insert("Playlists", null, ContentValues().apply {
            put("playlistId", playlist.id)
            put("name", playlist.name)
            put("createdAt", playlist.createdAt)
            put("updatedAt", playlist.updatedAt)
        })
        reload()
        playlist
    }

    override suspend fun addToPlaylist(playlistId: String, mediaUri: String) =
        withContext(Dispatchers.IO) {
            val db = database.writableDatabase
            val nextOrder = db.rawQuery(
                "SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM PlaylistItems WHERE playlistId = ?",
                arrayOf(playlistId)
            ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            db.insertWithOnConflict(
                "PlaylistItems", null, ContentValues().apply {
                    put("playlistId", playlistId)
                    put("mediaId", mediaUri)
                    put("sortOrder", nextOrder)
                },
                android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
            )
            touch(playlistId)
            reload()
        }

    override suspend fun removeFromPlaylist(playlistId: String, mediaUri: String) =
        withContext(Dispatchers.IO) {
            database.writableDatabase.delete(
                "PlaylistItems", "playlistId = ? AND mediaId = ?", arrayOf(playlistId, mediaUri)
            )
            touch(playlistId)
            reload()
        }

    private fun touch(playlistId: String) {
        database.writableDatabase.update(
            "Playlists",
            ContentValues().apply { put("updatedAt", System.currentTimeMillis()) },
            "playlistId = ?", arrayOf(playlistId)
        )
    }

    private fun reload() {
        val db = database.readableDatabase
        val result = ArrayList<Playlist>()
        db.query("Playlists", null, null, null, null, null, "createdAt ASC").use { c ->
            while (c.moveToNext()) {
                val id = c.getString(c.getColumnIndexOrThrow("playlistId"))
                result += Playlist(
                    id = id,
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt")),
                    updatedAt = c.getLong(c.getColumnIndexOrThrow("updatedAt")),
                    items = loadItems(id)
                )
            }
        }
        playlistsFlow.value = result
    }

    private fun loadItems(playlistId: String): List<String> {
        val items = ArrayList<String>()
        database.readableDatabase.query(
            "PlaylistItems", arrayOf("mediaId"), "playlistId = ?", arrayOf(playlistId),
            null, null, "sortOrder ASC"
        ).use { c -> while (c.moveToNext()) items += c.getString(0) }
        return items
    }
}
