package com.zaaam.vmusix.domain.model

import android.net.Uri

/** Abstraksi playback hybrid (02_ARCHITECTURE §3). Queue/NowPlaying tidak peduli source. */
sealed interface PlaybackSource {
    data class Local(val uri: Uri) : PlaybackSource
    data class YouTube(val videoId: String) : PlaybackSource
}

data class Track(
    val key: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val artwork: String? = null,
    val source: PlaybackSource,
)

data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int = 0,
)

data class QueueItem(
    val track: Track,
    val position: Int,
)

/** Adaptasi dari Zmusic (LyricsRepository) — lirik via LRCLIB/lrcmux, gratis & legal buat app pihak ketiga. */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

data class Lyrics(
    val plain: String,
    val synced: List<LyricLine> = emptyList(),
) {
    val hasSynced: Boolean get() = synced.isNotEmpty()
}
