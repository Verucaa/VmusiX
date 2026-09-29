package com.zaaam.vmusix.ui.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.zaaam.vmusix.data.LyricsRepository
import com.zaaam.vmusix.data.local.AppDatabase
import com.zaaam.vmusix.data.local.HistoryEntity
import com.zaaam.vmusix.data.local.QueueEntity
import com.zaaam.vmusix.data.repository.MusicRepository
import com.zaaam.vmusix.data.youtube.YtResult
import com.zaaam.vmusix.domain.model.Lyrics
import com.zaaam.vmusix.domain.model.PlaybackSource
import com.zaaam.vmusix.domain.model.Track
import com.zaaam.vmusix.playback.VmusiXSessionService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class PlayerUiState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isExpanded: Boolean = false,
    val error: String? = null,
    val queue: List<Track> = emptyList(),
    val showQueue: Boolean = false,
    val controllerReady: Boolean = false,
    val shuffleEnabled: Boolean = false,
    /** 0=off 1=one 2=all (mirror Player.REPEAT_MODE_*) */
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

/** Adaptasi dari Zmusic (LyricsState) — dipisah dari PlayerUiState karena siklus hidupnya beda (per-track, lazy-load). */
sealed class LyricsState {
    data object Idle : LyricsState()
    data object Loading : LyricsState()
    data class Success(val lyrics: Lyrics) : LyricsState()
    data object NotFound : LyricsState()
}

/** Batas auto-skip beruntun sebelum playback dihentikan (lihat onPlayerError). */
private const val MAX_AUTO_SKIP = 5

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val repo: MusicRepository,
    private val lyricsRepo: LyricsRepository,
) : androidx.lifecycle.ViewModel() {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val lyricsState: StateFlow<LyricsState> = _lyricsState

    fun loadLyrics() {
        val track = _state.value.current ?: return
        if (_lyricsState.value is LyricsState.Success || _lyricsState.value is LyricsState.Loading) return
        viewModelScope.launch {
            _lyricsState.value = LyricsState.Loading
            val lyrics = lyricsRepo.getLyrics(track.title, track.artist)
            _lyricsState.value = if (lyrics != null) LyricsState.Success(lyrics) else LyricsState.NotFound
        }
    }

    private var controller: MediaController? = null
    private var ticker: Job? = null
    /** Aksi yang datang sebelum controller tersambung — dijalankan saat connect, tidak hilang diam-diam. */
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var connectAttempts = 0

    /** Jumlah error putar beruntun; cap-nya mencegah loncat-loncat tanpa henti. */
    private var consecutiveSkips = 0

    /** Key lagu yang sudah dicoba di-re-resolve, supaya retry tidak jadi loop. */
    private val reResolved = mutableSetOf<String>()

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null && c.isConnected) {
            try {
                block(c)
            } catch (e: Exception) {
                Log.w("VmusiXPlayer", "controller call gagal", e)
                _state.value = _state.value.copy(error = "Aksi gagal, coba lagi")
            }
        } else {
            if (c != null && !c.isConnected) {
                // Koneksi ke playback service putus di tengah sesi (mis. service
                // di-kill OS) — reset & coba sambung ulang otomatis, bukan diem aja.
                controller = null
                connectAttempts = 0
                connect()
            }
            if (pending.size >= 20) pending.removeAt(0)
            pending.add(block)
        }
    }

    init {
        connect()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Restore queue saja (metadata), current dibiarkan null supaya
                // mini-player tidak nampil lagu basi yang belum di-load ke controller.
                val saved = db.queueDao().load()
                    .filter { it.source == "youtube" || !it.localUri.isNullOrBlank() }
                if (saved.isNotEmpty()) {
                    _state.value = _state.value.copy(queue = saved.map { it.toTrack() })
                }
            } catch (e: Exception) {
                Log.w("VmusiXPlayer", "restore queue gagal", e)
            }
        }
    }

    private fun connect() {
        try {
            val token = SessionToken(context, ComponentName(context, VmusiXSessionService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                val c = try {
                    future.get()
                } catch (e: Exception) {
                    Log.w("VmusiXPlayer", "controller connect gagal", e)
                    connectAttempts++
                    if (connectAttempts <= 3) {
                        viewModelScope.launch {
                            delay(3000)
                            connect()
                        }
                    } else {
                        _state.value = _state.value.copy(
                            error = "Tidak bisa tersambung ke layanan putar, restart app",
                        )
                    }
                    return@addListener
                }
                connectAttempts = 0
                controller = c
                c.addListener(playerListener)
                _state.value = _state.value.copy(
                    isPlaying = c.isPlaying,
                    controllerReady = true,
                    shuffleEnabled = c.shuffleModeEnabled,
                    repeatMode = c.repeatMode,
                )
                if (c.isPlaying) startTicker()
                // Sinkronkan track lokal restore ke controller (tanpa autoplay).
                // Track YouTube diskip: URL expired, di-resolve ulang saat di-tap.
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val locals = _state.value.queue.filter { it.source is PlaybackSource.Local }
                        if (locals.isNotEmpty() && c.mediaItemCount == 0) {
                            val items = locals.mapNotNull { t ->
                                val uri = (t.source as PlaybackSource.Local).uri.toString()
                                if (uri.isBlank()) return@mapNotNull null
                                VmusiXSessionService.buildMediaItem(t.key, t.title, t.artist, t.artwork, uri)
                            }
                            if (items.isNotEmpty()) {
                                withController { cc ->
                                    cc.setMediaItems(items)
                                    cc.prepare()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("VmusiXPlayer", "sync restore gagal", e)
                    }
                }
                pending.forEach { block ->
                    try {
                        block(c)
                    } catch (e: Exception) {
                        Log.w("VmusiXPlayer", "pending gagal", e)
                    }
                }
                pending.clear()
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.w("VmusiXPlayer", "build controller gagal", e)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
            // Putus rantai error begitu ada lagu yang benar-benar bunyi.
            if (isPlaying) {
                consecutiveSkips = 0
                reResolved.clear()
            }
            if (isPlaying) startTicker() else ticker?.cancel()
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            _lyricsState.value = LyricsState.Idle
            // Sengaja TIDAK di-reset di sini: beberapa skip beruntun itu
            // memang rantai error, bukan lagu sehat yang berganti. Yang
            // me-reset adalah isPlaying == true (lihat onIsPlayingChanged).
            val found = _state.value.queue.find { it.key == item?.mediaId }
            // Fallback: turunkan current dari metadata controller bila queue divergen.
            val track = found ?: item?.let { fallbackTrack(it) }
            if (track != null) {
                if (!_state.value.queue.any { it.key == track.key }) {
                    _state.value = _state.value.copy(queue = _state.value.queue + track)
                }
                _state.value = _state.value.copy(current = track)
                recordHistory(track)
            }
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _state.value = _state.value.copy(shuffleEnabled = enabled)
        }

        override fun onRepeatModeChanged(mode: Int) {
            _state.value = _state.value.copy(repeatMode = mode)
        }

        override fun onPlayerError(error: PlaybackException) {
            // Coba pertama: URL stream YouTube itu time-limited, jadi item bisa
            // gagal BUKAN karena lagu rusak, tapi karena URL-nya kedaluwarsa
            // (antrian panjang, restore dari sesi lalu Chemicals). Re-resolve
            // sekali lalu putar ulang sebelum menyerah & skip.
            val cur = _state.value.current
            val mediaId = controller?.currentMediaItem?.mediaId
            if (cur != null && mediaId == cur.key &&
                cur.source is PlaybackSource.YouTube && reResolved.add(cur.key)
            ) {
                _state.value = _state.value.copy(error = "Muat ulang ${cur.title}…")
                viewModelScope.launch {
                    val url = resolveUrl(cur)
                    if (url == null) return@launch
                    withController { cc ->
                        val idx = cc.currentMediaItemIndex
                        if (idx >= 0 && idx < cc.mediaItemCount) {
                            cc.replaceMediaItem(idx, item(cur, url))
                            cc.prepare()
                            cc.play()
                        }
                    }
                }
                return
            }
            // Auto-skip tanpa batas = satu antrian rusak bikin app loncat-loncat
            // dan tetap tidak menghasilkan suara. Batas consecutive skip, lalu
            // berhenti dan complainant jujur.
            if (consecutiveSkips >= MAX_AUTO_SKIP) {
                withController { cc -> cc.pause() }
                _state.value = _state.value.copy(
                    isPlaying = false,
                    error = "Berhenti: $consecutiveSkips lagu berturut-turut gagal dimuat. " +
                        "Coba tap lagunya lagi, atau pindai ulang Library.",
                )
                consecutiveSkips = 0
                return
            }
            consecutiveSkips++
            _state.value = _state.value.copy(
                isPlaying = false,
                error = "Gagal memutar ${cur?.title ?: "lagu ini"} (${error.errorCodeName}), lanjut ke lagu berikutnya…",
            )
            // Jangan biarin antrian macet total gara-gara satu URL YouTube yang
            // udah basi/gagal — coba lanjut ke lagu berikutnya secara otomatis.
            withController { cc ->
                if (cc.hasNextMediaItem()) {
                    cc.seekToNext()
                    cc.prepare()
                    cc.play()
                } else {
                    // Lagu terakhir yang gagal: diamkan, jangan putar ulang
                    // item yang sama dalam loop.
                    cc.pause()
                }
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                try {
                    val c = controller
                    if (c != null && c.duration > 0) {
                        _state.value = _state.value.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0),
                            durationMs = c.duration,
                        )
                    }
                } catch (_: Exception) {
                }
                delay(400)
            }
        }
    }

    fun setExpanded(v: Boolean) {
        _state.value = _state.value.copy(isExpanded = v)
    }

    fun toggleQueue(v: Boolean? = null) {
        _state.value = _state.value.copy(showQueue = v ?: !_state.value.showQueue)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private var playJob: Job? = null

    private fun item(t: Track, url: String) =
        VmusiXSessionService.buildMediaItem(t.key, t.title, t.artist, t.artwork, url)

    /**
     * Mulai muter lagu yang diketuk LANGSUNG, sisa antrian di-resolve di belakang layar lalu
     * ditempel berurutan (lanjutan di belakang, lagu sebelumnya di depan). Dulu semua URL
     * di-resolve dulu baru mulai: lambat, dan URL YouTube di ujung antrian keburu basi.
     */
    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            val list = if (queue.any { it.key == track.key }) queue else listOf(track) + queue
            val firstUrl = resolveUrl(track)
            if (firstUrl == null) {
                if (_state.value.error == null) _state.value = _state.value.copy(error = "Lagu gagal dimuat, coba lagi")
                return@launch
            }
            withController { c ->
                c.setMediaItems(listOf(item(track, firstUrl)), 0, 0)
                c.prepare()
                c.play()
            }
            _state.value = _state.value.copy(queue = listOf(track), current = track, error = null)
            recordHistory(track)
            val at = list.indexOfFirst { it.key == track.key }
            val before = mutableListOf<Track>()
            val after = mutableListOf<Track>()
            var failed = 0
            for (t in list.drop(at + 1)) {
                val url = resolveUrl(t)
                if (url == null) { failed++; continue }
                withController { c -> c.addMediaItem(item(t, url)) }
                after.add(t)
                _state.value = _state.value.copy(queue = before + track + after)
            }
            for (t in list.take(at)) {
                val url = resolveUrl(t)
                if (url == null) { failed++; continue }
                val pos = before.size
                withController { c -> c.addMediaItem(pos, item(t, url)) }
                before.add(t)
                _state.value = _state.value.copy(queue = before + track + after)
            }
            persistQueue(before + track + after)
            if (failed > 0) _state.value = _state.value.copy(error = "$failed lagu dilewati (gagal dimuat)")
        }
    }

    /** Buang duplikat lagu yang sama dari playlist player (kecuali yang lagi diputar) biar state & player gak beda. */
    private fun dropDuplicate(c: MediaController, key: String) {
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (i != c.currentMediaItemIndex && c.getMediaItemAt(i).mediaId == key) c.removeMediaItem(i)
        }
    }

    fun playNext(track: Track) {
        if (track.key == _state.value.current?.key) return
        viewModelScope.launch {
            val url = resolveUrl(track) ?: return@launch
            withController { c ->
                dropDuplicate(c, track.key)
                val idx = if (c.mediaItemCount > 0) (c.currentMediaItemIndex + 1).coerceIn(0, c.mediaItemCount) else 0
                c.addMediaItem(idx, item(track, url))
            }
            val cur = _state.value.current
            val q = _state.value.queue.toMutableList()
            q.removeAll { it.key == track.key }
            val at = if (cur != null) (q.indexOfFirst { it.key == cur.key } + 1).coerceIn(0, q.size) else 0
            q.add(at, track)
            _state.value = _state.value.copy(queue = q)
            persistQueue(q)
        }
    }

    fun addToQueue(track: Track) {
        if (track.key == _state.value.current?.key) return
        viewModelScope.launch {
            val url = resolveUrl(track) ?: return@launch
            withController { c ->
                dropDuplicate(c, track.key)
                c.addMediaItem(item(track, url))
            }
            val q = _state.value.queue.toMutableList()
            q.removeAll { it.key == track.key }
            q.add(track)
            _state.value = _state.value.copy(queue = q)
            persistQueue(q)
        }
    }

    /** Hapus satu lagu dari antrian (lagu yang lagi diputar gak bisa dihapus). */
    fun removeFromQueue(index: Int) {
        val q = _state.value.queue
        if (index !in q.indices || q[index].key == _state.value.current?.key) return
        withController { c -> if (index < c.mediaItemCount && c.getMediaItemAt(index).mediaId == q[index].key) c.removeMediaItem(index) }
        val next = q.toMutableList().also { it.removeAt(index) }
        _state.value = _state.value.copy(queue = next)
        viewModelScope.launch(Dispatchers.IO) { persistQueue(next) }
    }

    fun moveQueue(from: Int, to: Int) {
        val q = _state.value.queue
        if (from !in q.indices || to !in q.indices) return
        withController { c ->
            if (from < c.mediaItemCount && to < c.mediaItemCount) {
                c.moveMediaItem(from, to)
            }
        }
        val next = q.toMutableList()
        val t = next.removeAt(from)
        next.add(to, t)
        _state.value = _state.value.copy(queue = next)
        viewModelScope.launch(Dispatchers.IO) { persistQueue(next) }
    }

    fun togglePlayPause() = withController { c ->
        if (c.mediaItemCount == 0) return@withController
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = withController { c -> if (c.mediaItemCount > 0) c.seekToNext() }
    fun prev() = withController { c -> if (c.mediaItemCount > 0) c.seekToPrevious() }
    fun seekTo(ms: Long) = withController { c ->
        if (c.duration > 0) c.seekTo(ms.coerceIn(0, c.duration))
    }

    fun setShuffle(v: Boolean) = withController { c -> c.shuffleModeEnabled = v }
    fun setRepeat(mode: Int) = withController { c -> c.repeatMode = mode }

    /** Kosongkan queue (UI + controller), ala tombol Clear Apple Music. */
    fun clearQueue() {
        withController { c -> c.clearMediaItems() }
        _state.value = _state.value.copy(queue = emptyList(), current = null, isPlaying = false)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.queueDao().clear()
            } catch (e: Exception) {
                Log.w("VmusiXPlayer", "clear queue gagal", e)
            }
        }
    }

    /** Lazy resolve: lokal = contentUri langsung; YT = re-resolve tiap mau play (URL expired). */
    private suspend fun resolveUrl(t: Track): String? {
        return when (val s = t.source) {
            is PlaybackSource.Local -> {
                val uri = s.uri.toString()
                if (uri.isBlank()) {
                    _state.value = _state.value.copy(error = "File lagu tidak ditemukan, scan ulang Library")
                    null
                } else {
                    uri
                }
            }
            is PlaybackSource.YouTube -> try {
                when (val r = withTimeout(20_000) { repo.resolveStream(s.videoId) }) {
                    is YtResult.Ok -> r.value
                    is YtResult.Fail -> {
                        _state.value = _state.value.copy(error = r.message)
                        null
                    }
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(error = "Gagal ambil data dari YouTube, coba lagi")
                null
            }
        }
    }

    private suspend fun persistQueue(q: List<Track>) {
        // Room menolak main thread. Sebelumnya fungsi ini dipanggil langsung
        // dari playTrack/playNext/addToQueue yang jalan di Dispatchers.Main —
        // IllegalStateException, atau (kalau allowMainThreadQuery aktif) jank
        // tiap kali user menambah lagu.
        withContext(Dispatchers.IO) {
            try {
                val entities = q.mapIndexed { i, t ->
                    val (source, localUri, videoId) = when (val s = t.source) {
                        is PlaybackSource.Local -> Triple("local", s.uri.toString(), null as String?)
                        is PlaybackSource.YouTube -> Triple("youtube", null, s.videoId)
                    }
                    QueueEntity(i, t.key, t.title, t.artist, t.artwork, source, localUri, videoId)
                }
                db.queueDao().replaceAll(entities)
            } catch (e: Exception) {
                Log.w("VmusiXPlayer", "persist queue gagal", e)
            }
        }
    }

    private fun recordHistory(t: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (source, localUri, videoId) = when (val s = t.source) {
                    is PlaybackSource.Local -> Triple("local", s.uri.toString(), null as String?)
                    is PlaybackSource.YouTube -> Triple("youtube", null, s.videoId)
                }
                db.historyDao().upsert(
                    HistoryEntity(t.key, t.title, t.artist, t.artwork, source, localUri, videoId, System.currentTimeMillis()),
                )
            } catch (e: Exception) {
                Log.w("VmusiXPlayer", "history gagal", e)
            }
        }
    }

    /** Bentuk Track darurat dari metadata controller (queue divergen). */
    private fun fallbackTrack(item: MediaItem): Track? {
        val meta = item.mediaMetadata ?: return null
        val key = item.mediaId.ifBlank { return null }
        val uri = item.localConfiguration?.uri?.toString() ?: ""
        val src = when {
            key.startsWith("yt:") -> PlaybackSource.YouTube(key.removePrefix("yt:"))
            uri.isNotBlank() -> PlaybackSource.Local(Uri.parse(uri))
            else -> return null
        }
        return Track(
            key = key,
            title = meta.title?.toString() ?: "Unknown",
            artist = meta.artist?.toString() ?: "Unknown",
            artwork = meta.artworkUri?.toString(),
            source = src,
        )
    }

    private fun QueueEntity.toTrack(): Track {
        val src = if (source == "youtube" && videoId != null) {
            PlaybackSource.YouTube(videoId)
        } else {
            PlaybackSource.Local(Uri.parse(localUri ?: ""))
        }
        return Track(trackKey, title, artist, "", 0L, artwork, src)
    }

    override fun onCleared() {
        ticker?.cancel()
        try {
            controller?.release()
        } catch (_: Exception) {
        }
    }
}
