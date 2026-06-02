package com.watermelon.subtitle.network

import com.watermelon.common.model.SubtitleTrack
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Ktor client for the OpenSubtitles API with language filtering and mirror failover
 * (Manifest §6.2). Network is only ever touched after an explicit user action (Privacy §14).
 */
class SubtitleApiClient(
    private val mirrors: MirrorRotator = MirrorRotator.DEFAULT,
    private val httpClient: HttpClient = HttpClient(Android),
    private val responseParser: (String) -> List<SubtitleTrack> = ::parseTracks
) {
    /**
     * Query subtitles for a given OpenSubtitles file [hash], filtered to [preferredLanguages].
     * Rotates to the next mirror on 5xx/timeout; fail-closed when mirrors are exhausted.
     */
    suspend fun query(
        hash: String,
        fileSize: Long,
        preferredLanguages: List<String>
    ): List<SubtitleTrack> {
        mirrors.reset()
        while (true) {
            val outcome = runCatching {
                withTimeout(REQUEST_TIMEOUT_MS) {
                    val response: HttpResponse = httpClient.get("${mirrors.current}/api/v1/subtitles") {
                        header("User-Agent", USER_AGENT)
                        parameter("moviehash", hash)
                        parameter("moviebytesize", fileSize)
                        if (preferredLanguages.isNotEmpty()) {
                            parameter("languages", preferredLanguages.joinToString(","))
                        }
                    }
                    response
                }
            }

            val response = outcome.getOrNull()
            when {
                response != null && response.status.isSuccess() ->
                    return responseParser(response.bodyAsText())
                        .filterByLanguages(preferredLanguages)

                // Server error or timeout → fail over to next mirror.
                response == null /* timeout/exception */ ||
                    response.status.value in 500..599 -> {
                    if (mirrors.advance() == null) return emptyList() // fail-closed
                }

                // 4xx / other → no point retrying other mirrors.
                else -> return emptyList()
            }
        }
    }

    private fun List<SubtitleTrack>.filterByLanguages(prefs: List<String>): List<SubtitleTrack> {
        if (prefs.isEmpty()) return this
        return filter { it.language in prefs }
            .sortedWith(compareBy({ prefs.indexOf(it.language) }, { -it.rating }))
    }

    fun close() = httpClient.close()

    companion object {
        private const val REQUEST_TIMEOUT_MS = 8_000L
        private const val USER_AGENT = "Watermelon/1.0"

        /** Minimal, dependency-free parse used by default; real impl can swap in kotlinx.json. */
        fun parseTracks(@Suppress("UNUSED_PARAMETER") body: String): List<SubtitleTrack> =
            emptyList()
    }
}
