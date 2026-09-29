package com.zaaam.vmusix.ui.search

import com.zaaam.vmusix.data.repository.MusicRepository
import com.zaaam.vmusix.data.youtube.YtResult
import com.zaaam.vmusix.data.youtube.YtTrack
import com.zaaam.vmusix.domain.model.PlaybackSource
import com.zaaam.vmusix.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val local: List<Track> = emptyList(),
    val yt: List<YtTrack> = emptyList(),
    val ytLoading: Boolean = false,
    val ytError: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: MusicRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state
    private val queryFlow = MutableStateFlow("")
    /** Job YT terkontrol: cegah request menumpuk saat ketik cepat. */
    private var ytJob: Job? = null

    init {
        // Preset genre dari nav-arg: survive relaunch + process death.
        viewModelScope.launch {
            savedStateHandle.getStateFlow("preset", "").collect { p ->
                if (p.isNotBlank() && _state.value.query.isBlank()) onQuery(p)
            }
        }
    }

    init {
        viewModelScope.launch {
            queryFlow.debounce(150).collectLatest { q ->
                if (q.isBlank()) {
                    _state.value = _state.value.copy(local = emptyList(), yt = emptyList(), ytError = null)
                    return@collectLatest
                }
                // Query <2 char + escape wildcard: hindari full-table LIKE tiap keystroke.
                if (q.trim().length < 2) {
                    _state.value = _state.value.copy(local = emptyList())
                    return@collectLatest
                }
                val local = repo.searchLocal(q)
                if (queryFlow.value == q) {
                    _state.value = _state.value.copy(local = local)
                }
            }
        }
        viewModelScope.launch {
            queryFlow.debounce(400).collectLatest { q ->
                ytJob?.cancel()
                if (q.isBlank() || q.trim().length < 2) {
                    _state.value = _state.value.copy(ytLoading = false)
                    return@collectLatest
                }
                _state.value = _state.value.copy(ytLoading = true, ytError = null)
                val asked = q
                ytJob = launch {
                    try {
                        val result = withTimeout(15_000) { repo.searchYouTube(asked) }
                        // Hanya tulis kalau query belum berubah (cegah hasil basi).
                        if (queryFlow.value != asked) return@launch
                        when (result) {
                            is YtResult.Ok -> _state.value = _state.value.copy(yt = result.value, ytLoading = false)
                            is YtResult.Fail -> _state.value = _state.value.copy(yt = emptyList(), ytLoading = false, ytError = result.message)
                        }
                    } catch (e: Throwable) {
                        // CancellationException HARUS diteruskan. Tanpa baris
                        // ini, saat user mengetik lagi atau menutup layar,
                        // request yang sedang berjalan tetap menulis state
                        // lama -> hasil basun yang tidak pernah hilang.
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        if (queryFlow.value != asked) return@launch
                        _state.value = _state.value.copy(yt = emptyList(), ytLoading = false, ytError = "Gagal ambil data dari YouTube, coba lagi")
                    }
                }
            }
        }
    }

    fun onQuery(q: String) {
        queryFlow.value = q
        _state.value = _state.value.copy(query = q)
    }

    /** Preset dari tab New (genre) — hanya kalau user belum mengetik. */
    fun setPreset(q: String) {
        if (_state.value.query.isBlank() && q.isNotBlank()) onQuery(q)
    }

    fun ytAsTrack(t: YtTrack): Track = Track(
        key = "yt:${t.videoId}",
        title = t.title,
        artist = t.artist.ifBlank { "YouTube" },
        artwork = t.thumbnailUrl.ifBlank { null },
        source = PlaybackSource.YouTube(t.videoId),
    )
}
