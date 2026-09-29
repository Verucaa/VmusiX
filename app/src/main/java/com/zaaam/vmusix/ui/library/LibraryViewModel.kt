package com.zaaam.vmusix.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaaam.vmusix.data.local.AppDatabase
import com.zaaam.vmusix.data.local.MediaStoreScanner
import com.zaaam.vmusix.data.repository.MusicRepository
import com.zaaam.vmusix.data.repository.toTrack
import com.zaaam.vmusix.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import android.content.Context

data class LibraryUiState(
    val songs: List<Track> = emptyList(),
    val songCount: Int = 0,
    val artists: List<String> = emptyList(),
    val albums: List<String> = emptyList(),
    val recentlyAdded: List<Track> = emptyList(),
    val playlistNames: List<Pair<Long, String>> = emptyList(),
    val needsPermission: Boolean = false,
    val scanning: Boolean = false,
    val lastScanCount: Int = -1,
    val scanError: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val scanner: MediaStoreScanner,
    private val repo: MusicRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    init {
        refresh()
    }

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                if (!hasPermission()) {
                    _state.value = _state.value.copy(needsPermission = true)
                    return@launch
                }
                loadFromDb()
            } catch (e: Exception) {
                Log.w("VmusiXLib", "refresh gagal", e)
            }
        }
    }

    fun scan() {
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = true, needsPermission = false, scanError = null)
            try {
                val n = withContext(Dispatchers.IO) { scanner.scan() }
                _state.value = _state.value.copy(lastScanCount = n)
            } catch (e: Exception) {
                Log.w("VmusiXLib", "scan gagal", e)
                _state.value = _state.value.copy(scanError = "Scan gagal: ${e.message ?: "error tak dikenal"}")
            }
            try {
                loadFromDb()
            } catch (e: Exception) {
                Log.w("VmusiXLib", "load gagal", e)
            }
            _state.value = _state.value.copy(scanning = false)
        }
    }

    /** Auto-scan saat pertama buka kalau DB masih kosong (punya izin). */
    fun scanIfEmpty() {
        viewModelScope.launch {
            try {
                if (!hasPermission()) {
                    _state.value = _state.value.copy(needsPermission = true)
                    return@launch
                }
                if (withContext(Dispatchers.IO) { db.trackDao().count() } == 0 && !_state.value.scanning) {
                    scan()
                } else {
                    loadFromDb()
                }
            } catch (e: Exception) {
                Log.w("VmusiXLib", "scanIfEmpty gagal", e)
            }
        }
    }

    private suspend fun loadFromDb() {
        val songs = repo.localSongs()
        val dao = db.trackDao()
        val recentEntities = dao.recentlyAdded(10)
        _state.value = LibraryUiState(
            songs = songs,
            songCount = songs.size,
            artists = dao.artists(),
            albums = dao.albums(),
            recentlyAdded = recentEntities.map { it.toTrack() },
            playlistNames = db.playlistDao().playlists().map { it.id to it.name },
            needsPermission = false,
            scanning = false,
            lastScanCount = _state.value.lastScanCount,
            scanError = _state.value.scanError,
        )
    }
}
