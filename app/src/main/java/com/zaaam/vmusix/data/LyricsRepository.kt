package com.zaaam.vmusix.data

import android.net.Uri
import android.util.Log
import com.zaaam.vmusix.domain.model.LyricLine
import com.zaaam.vmusix.domain.model.Lyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adaptasi dari Zmusic (com.zaaam.zmusic.data.LyricsRepository). Sumbernya
 * LRCLIB via mirror lrcmux.dev — API lirik gratis & memang didesain buat
 * dipakai app musik pihak ketiga (bukan scraping situs lirik berbayar).
 */
@Singleton
class LyricsRepository @Inject constructor() {

    companion object {
        private const val TAG = "VmusiXLyrics"
        private const val LYRICS_BASE_URL = "https://api.lrcmux.dev/compat/lrclib/api/get"
    }

    // OkHttpClient sendiri, sama kayak pola OkHttpDownloader — gak perlu shared instance.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Judul dari YouTube sering bawa "(Official Music Video)" dll yang bikin
    // LRCLIB gagal match walau lagunya sebenarnya ada.
    private val noiseParenRegex = Regex(
        """[\(\[][^\)\]]*(official|music\s*video|lyrics?|visualizer|audio|video|mv|hd|4k|remaster(ed)?|full\s*album|clip)[^\)\]]*[\)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val noiseTrailingRegex = Regex(
        """\s*[-–|]\s*(official\s*)?(music\s*video|lyrics?|audio|video)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val extraSpaceRegex = Regex("""\s{2,}""")

    private fun cleanForSearch(text: String): String =
        text.replace(noiseParenRegex, "").replace(noiseTrailingRegex, "").replace(extraSpaceRegex, " ").trim()

    suspend fun getLyrics(title: String, artist: String): Lyrics? = withContext(Dispatchers.IO) {
        val cleanedTitle = cleanForSearch(title).ifBlank { title }
        val cleanedArtist = cleanForSearch(artist).ifBlank { artist }
        fetchLyrics(cleanedTitle, cleanedArtist)
            ?: if (cleanedTitle != title || cleanedArtist != artist) fetchLyrics(title, artist) else null
    }

    private suspend fun fetchLyrics(title: String, artist: String): Lyrics? = withContext(Dispatchers.IO) {
        try {
            val url = "$LYRICS_BASE_URL?artist_name=${Uri.encode(artist)}&track_name=${Uri.encode(title)}"
            val request = Request.Builder().url(url).header("Lrclib-Client", "VmusiX Android v1.0").build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val plain = if (json.has("plainLyrics") && !json.isNull("plainLyrics")) json.getString("plainLyrics") else ""
                val syncedRaw = if (json.has("syncedLyrics") && !json.isNull("syncedLyrics")) json.getString("syncedLyrics") else ""
                if (plain.isBlank() && syncedRaw.isBlank()) return@withContext null
                Lyrics(plain = plain, synced = parseLrc(syncedRaw))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "getLyrics gagal untuk \"$title\" - \"$artist\"", e)
            null
        }
    }

    private val lrcPattern = Regex("""^\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")

    private fun parseLrc(lrc: String): List<LyricLine> {
        if (lrc.isBlank()) return emptyList()
        val result = mutableListOf<LyricLine>()
        lrc.lines().forEach { line ->
            val match = lrcPattern.find(line.trim()) ?: return@forEach
            val (min, sec, ms, text) = match.destructured
            val timeMs = min.toLong() * 60_000 + sec.toLong() * 1_000 + (if (ms.length == 2) ms.toLong() * 10 else ms.toLong())
            result.add(LyricLine(timeMs = timeMs, text = text.trim()))
        }
        return result.sortedBy { it.timeMs }
    }
}
