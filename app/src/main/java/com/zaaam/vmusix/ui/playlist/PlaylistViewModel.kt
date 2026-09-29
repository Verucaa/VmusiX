package com.zaaam.vmusix.ui.playlist

import com.zaaam.vmusix.data.local.AppDatabase
import com.zaaam.vmusix.data.local.PlaylistEntity
import com.zaaam.vmusix.data.local.PlaylistTrackEntity
import com.zaaam.vmusix.domain.model.PlaybackSource
import com.zaaam.vmusix.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** PRD-009: playlist campuran lokal + YouTube, persist Room. */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val db: AppDatabase,
) : ViewModel() {
    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val lists = db.playlistDao().playlists()
                _playlists.value = lists
                val fav = lists.find { it.name == "Favorit" }
                _favKeys.value = if (fav != null) {
                    db.playlistDao().tracks(fav.id).map { it.trackKey }.toSet()
                } else {
                    emptySet()
                }
            } catch (e: Exception) {
                android.util.Log.w("VmusiXPl", "refresh gagal", e)
            }
        }
    }

    fun create(name: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                db.playlistDao().create(PlaylistEntity(name = name, createdAt = System.currentTimeMillis()))
                refresh()
            } catch (e: Exception) {
                android.util.Log.w("VmusiXPl", "create gagal", e)
            }
        }
    }

    fun addTrack(playlistId: Long, track: Track) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pos = db.playlistDao().maxPosition(playlistId) + 1
                val (source, localUri, videoId) = when (val s = track.source) {
                    is PlaybackSource.Local -> Triple("local", s.uri.toString(), null as String?)
                    is PlaybackSource.YouTube -> Triple("youtube", null, s.videoId)
                }
                db.playlistDao().putTrack(
                    PlaylistTrackEntity(playlistId, pos, track.key, track.title, track.artist, track.artwork, source, localUri, videoId),
                )
                refresh()
            } catch (e: Exception) {
                android.util.Log.w("VmusiXPl", "addTrack gagal", e)
            }
        }
    }

    suspend fun tracksOf(id: Long) = try {
        db.playlistDao().tracks(id)
    } catch (e: Exception) {
        android.util.Log.w("VmusiXPl", "tracks gagal", e)
        emptyList()
    }

    private val _favKeys = MutableStateFlow<Set<String>>(emptySet())
    val favoritKeys: StateFlow<Set<String>> = _favKeys

    /** Playlist "Favorit" otomatis (backend tombol bintang ala Apple Music). */
    private suspend fun ensureFavorit(): Long {
        val existing = db.playlistDao().playlists().find { it.name == "Favorit" }
        if (existing != null) return existing.id
        return db.playlistDao().create(PlaylistEntity(name = "Favorit", createdAt = System.currentTimeMillis()))
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val id = ensureFavorit()
                val tracks = db.playlistDao().tracks(id)
                val hit = tracks.find { it.trackKey == track.key }
                if (hit != null) {
                    db.playlistDao().removeTrack(id, hit.position)
                } else {
                    val pos = db.playlistDao().maxPosition(id) + 1
                    val (source, localUri, videoId) = when (val s = track.source) {
                        is PlaybackSource.Local -> Triple("local", s.uri.toString(), null as String?)
                        is PlaybackSource.YouTube -> Triple("youtube", null, s.videoId)
                    }
                    db.playlistDao().putTrack(
                        PlaylistTrackEntity(id, pos, track.key, track.title, track.artist, track.artwork, source, localUri, videoId),
                    )
                }
                _favKeys.value = db.playlistDao().tracks(id).map { it.trackKey }.toSet()
                refresh()
            } catch (e: Exception) {
                android.util.Log.w("VmusiXPl", "favorit gagal", e)
            }
        }
    }

    fun removeTrack(playlistId: Long, pos: Int) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                db.playlistDao().removeTrack(playlistId, pos)
                refresh()
            } catch (e: Exception) {
                android.util.Log.w("VmusiXPl", "removeTrack gagal", e)
            }
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                db.playlistDao().deleteCascade(id)
                refresh()
            } catch (e: Exception) {
                android.util.Log.w("VmusiXPl", "delete gagal", e)
            }
        }
    }
}
