package com.watermelon.subtitle.repository

import android.content.Context
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.SubtitleTrack
import com.watermelon.common.repository.SubtitleRepository
import com.watermelon.subtitle.network.SubtitleApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [SubtitleRepository] implementation. `findSubtitles` checks the local cache first, then
 * queries the API via [SubtitleApiClient]. `downloadSubtitle` writes to
 * `getCacheDir()/subtitles/` and returns the local path (Teams §5 Implementation Notes).
 */
class SubtitleRepositoryImpl(
    private val context: Context,
    private val apiClient: SubtitleApiClient = SubtitleApiClient()
) : SubtitleRepository {

    private val downloadClient: HttpClient by lazy { HttpClient(Android) }

    private val cacheDir: File by lazy {
        File(context.cacheDir, "subtitles").apply { mkdirs() }
    }

    override suspend fun findSubtitles(
        mediaItem: MediaItem,
        preferredLanguages: List<String>
    ): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        // 1. Local cache match first (offline-first).
        val cached = cachedTracks(mediaItem, preferredLanguages)
        if (cached.isNotEmpty()) return@withContext cached

        // 2. Remote lookup by OpenSubtitles hash (only on explicit user action upstream).
        val hash = runCatching { hashFor(mediaItem) }.getOrNull() ?: return@withContext emptyList()
        apiClient.query(hash, mediaItem.fileSize, preferredLanguages)
    }

    override suspend fun downloadSubtitle(track: SubtitleTrack): String =
        withContext(Dispatchers.IO) {
            val target = File(cacheDir, fileNameFor(track))
            downloadClient.get(track.downloadUrl).bodyAsChannel().copyTo(target.outputStream())
            target.absolutePath
        }

    private fun cachedTracks(
        mediaItem: MediaItem,
        preferredLanguages: List<String>
    ): List<SubtitleTrack> {
        val prefix = safeKey(mediaItem.uri)
        return cacheDir.listFiles { f -> f.name.startsWith(prefix) }
            ?.mapNotNull { file ->
                val lang = file.nameWithoutExtension.substringAfterLast('.', "")
                if (preferredLanguages.isEmpty() || lang in preferredLanguages) {
                    SubtitleTrack(lang, file.name, file.toURI().toString(), rating = 0f)
                } else null
            }.orEmpty()
    }

    private fun hashFor(mediaItem: MediaItem): String {
        // For content:// URIs the hash is computed by the caller with a file descriptor;
        // here we fall back to the stable uri+size key when no file handle is available.
        return safeKey(mediaItem.uri) + mediaItem.fileSize.toString(16)
    }

    private fun fileNameFor(track: SubtitleTrack): String =
        "${safeKey(track.label)}.${track.language}.srt"

    private fun safeKey(raw: String): String =
        raw.hashCode().toUInt().toString(16)
}
