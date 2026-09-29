package com.zaaam.vmusix.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class YtTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSec: Long,
)

sealed interface YtResult<out T> {
    data class Ok<T>(val value: T) : YtResult<T>
    data class Fail(val message: String) : YtResult<Nothing>
}

private const val FAIL_MSG = "Gagal ambil data dari YouTube, coba lagi"

/**
 * PRD-003/004/012: satu-satunya titik yang boleh memanggil NewPipeExtractor.
 * Semua call ditangkap sampai level Throwable (Exception DAN Error — parser
 * rapuh saat YouTube berubah, dan Error lolos dari catch Exception biasa).
 */
@Singleton
class YouTubeRepository @Inject constructor() {
    private fun service() = NewPipe.getService(ServiceList.YouTube.serviceId)

    /** Thumbnail HD yang selalu ada untuk video (pola Zmusic). */
    private fun hdThumb(videoId: String) = "https://img.youtube.com/vi/$videoId/sddefault.jpg"

    private fun StreamInfoItem.toYtTrack(): YtTrack? {
        return try {
            val videoId = Regex("v=([A-Za-z0-9_-]{11})").find(url ?: return null)?.groupValues?.get(1)
                ?: return null
            YtTrack(
                videoId = videoId,
                title = name?.ifBlank { "Unknown" } ?: "Unknown",
                artist = uploaderName?.removeSuffix(" - Topic")?.ifBlank { "YouTube" } ?: "YouTube",
                thumbnailUrl = hdThumb(videoId),
                durationSec = try {
                    duration
                } catch (_: Throwable) {
                    -1L
                },
            )
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    /**
     * Trending ala Zmusic: kiosk Trending YouTube dulu, gagal -> discovery
     * via 3 query search paralel. Home selalu ada isi walau DB lokal kosong.
     */
    suspend fun trending(): YtResult<List<YtTrack>> = withContext(Dispatchers.IO) {
        try {
            val list = kioskTrending()
            if (list.isNotEmpty()) return@withContext YtResult.Ok(list)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w("VmusiXYT", "kiosk trending gagal, fallback search", e)
        }
        try {
            val list = discoveryFromSearch()
            if (list.isNotEmpty()) YtResult.Ok(list) else YtResult.Fail(FAIL_MSG)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            YtResult.Fail(FAIL_MSG)
        }
    }

    private suspend fun kioskTrending(): List<YtTrack> {
        val svc = service()
        val kioskList = svc.kioskList
        val kioskId = if ("Trending" in kioskList.availableKiosks) "Trending" else kioskList.defaultKioskId
        val extractor = kioskList.getExtractorById(kioskId, null)
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .filter {
                try {
                    it.duration in 30..900
                } catch (_: Throwable) {
                    false
                }
            }
            .mapNotNull { it.toYtTrack() }
            .distinctBy { it.videoId }
            .take(50)
    }

    private suspend fun discoveryFromSearch(): List<YtTrack> = coroutineScope {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val queries = listOf("lagu populer $year", "top hits indonesia", "musik trending terbaru")
        queries.map { q ->
            async {
                try {
                    musicSearch(q)
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.w("VmusiXYT", "discovery query $q gagal", e)
                    emptyList()
                }
            }
        }.awaitAll().flatten().distinctBy { it.videoId }.take(30)
    }

    /** Search musik bersih: filter MUSIC_SONGS seperti Zmusic. */
    private suspend fun musicSearch(query: String): List<YtTrack> {
        val svc = service()
        val handler = svc.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            "",
        )
        val info = SearchInfo.getInfo(svc, handler)
        coroutineContext.ensureActive()
        return info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toYtTrack() }
            .distinctBy { it.videoId }
    }

    suspend fun search(query: String): YtResult<List<YtTrack>> = withContext(Dispatchers.IO) {
        try {
            YtResult.Ok(musicSearch(query))
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            YtResult.Fail(FAIL_MSG)
        }
    }

    /** Resolve stream audio terbaik. URL YT itu time-limited: resolve lazy saat mau play. */
    suspend fun resolveAudioUrl(videoId: String): YtResult<String> = withContext(Dispatchers.IO) {
        try {
            val svc = service()
            val url = if (videoId.startsWith("http")) videoId else "https://www.youtube.com/watch?v=$videoId"
            val info = StreamInfo.getInfo(svc, url)
            coroutineContext.ensureActive()
            val audio = info.audioStreams.maxByOrNull {
                try {
                    it.bitrate
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    0
                }
            } ?: return@withContext YtResult.Fail("Stream audio tidak tersedia (mungkin age-restricted / private / region-block)")
            val streamUrl = try {
                audio.url
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ""
            }
            if (streamUrl.isNullOrBlank()) {
                YtResult.Fail("Stream audio tidak tersedia (mungkin age-restricted / private / region-block)")
            } else {
                YtResult.Ok(streamUrl)
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            YtResult.Fail(FAIL_MSG)
        }
    }
}
