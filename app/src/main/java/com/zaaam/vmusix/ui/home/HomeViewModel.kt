package com.zaaam.vmusix.ui.home

import android.net.Uri
import com.zaaam.vmusix.data.local.AppDatabase
import com.zaaam.vmusix.data.repository.MusicRepository
import com.zaaam.vmusix.data.repository.toTrack
import com.zaaam.vmusix.data.youtube.YtResult
import com.zaaam.vmusix.data.youtube.YtTrack
import com.zaaam.vmusix.domain.model.PlaybackSource
import com.zaaam.vmusix.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class HomeUiState(
    val recent: List<Track> = emptyList(),
    val newMusic: List<Track> = emptyList(),
    val trending: List<Track> = emptyList(),
    val trendingLoading: Boolean = false,
    val trendingError: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val db: AppDatabase,
    private val repo: MusicRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    fun refresh() {
        viewModelScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) { db.historyDao().recent(10) }
                val recent = rows.map {
                    val src = if (it.source == "youtube" && it.videoId != null) {
                        PlaybackSource.YouTube(it.videoId)
                    } else {
                        PlaybackSource.Local(Uri.parse(it.localUri ?: ""))
                    }
                    Track(it.trackKey, it.title, it.artist, "", 0L, it.artwork, src)
                }
                val added = withContext(Dispatchers.IO) { db.trackDao().recentlyAdded(10) }
                val newMusic = added.map { it.toTrack() }
                _state.value = _state.value.copy(recent = recent, newMusic = newMusic)
            } catch (e: Exception) {
                android.util.Log.w("VmusiXHome", "refresh gagal", e)
            }
        }
        loadTrending()
    }

    /** Trending YouTube (kiosk -> fallback query) ala Zmusic: Home hidup walau DB kosong. */
    fun loadTrending() {
        if (_state.value.trending.isNotEmpty() || _state.value.trendingLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(trendingLoading = true, trendingError = null)
            try {
                val result = withTimeout(25_000) { repo.trending() }
                when (result) {
                    is YtResult.Ok -> _state.value = _state.value.copy(
                        trending = result.value.map { it.asTrack() },
                        trendingLoading = false,
                    )
                    is YtResult.Fail -> _state.value = _state.value.copy(
                        trendingLoading = false,
                        trendingError = result.message,
                    )
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(trendingLoading = false, trendingError = "Gagal ambil data dari YouTube, coba lagi")
            }
        }
    }

    fun retryTrending() {
        _state.value = _state.value.copy(trending = emptyList())
        loadTrending()
    }

    private fun YtTrack.asTrack(): Track = Track(
        key = "yt:$videoId",
        title = title,
        artist = artist.ifBlank { "YouTube" },
        durationMs = if (durationSec > 0) durationSec * 1000 else 0L,
        artwork = thumbnailUrl.ifBlank { null },
        source = PlaybackSource.YouTube(videoId),
    )
}
