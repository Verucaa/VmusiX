package com.zaaam.vmusix.ui.player

import android.media.AudioManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.zaaam.vmusix.ui.theme.glass
import com.zaaam.vmusix.ui.theme.appear
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.zaaam.vmusix.ui.common.Artwork
import com.zaaam.vmusix.ui.theme.Accent
import kotlinx.coroutines.launch

/**
 * PRD-005: Now Playing full-screen meniru Apple Music dari screenshot resmi:
 * artwork full-bleed atas + drag handle, judul + bintang + ⋯, progress,
 * kontrol besar, volume, baris bawah (lirik / output / queue).
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    player: PlaybackViewModel,
    snack: SnackbarHostState,
    plVm: com.zaaam.vmusix.ui.playlist.PlaylistViewModel,
    onAddSongs: () -> Unit,
) {
    val cur = state.current
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var menu by remember { mutableStateOf<com.zaaam.vmusix.domain.model.Track?>(null) }
    val pls by plVm.playlists.collectAsState()
    val favKeys by plVm.favoritKeys.collectAsState()
    val isFav = cur != null && favKeys.contains(cur.key)
    val lyricsState by player.lyricsState.collectAsState()
    var showLyrics by remember { mutableStateOf(false) }

    // Tampilan error ditangani di MainActivity (satu snackbar global) — kalau
    // juga di sini, satu error jadi dua snackbar dan yang kedua menimpa yang
    // pertama saat layarnya kebuka.

    // Swipe-down-to-dismiss: drag handle di atas sekarang beneran fungsional,
    // bukan cuma dekorasi. Lewat ambang batas -> collapse ke mini-player.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxSize()
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset > 220f) {
                            player.setExpanded(false)
                        } else {
                            dragOffset = 0f
                        }
                    },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                    },
                )
            },
    ) {
        // Background: artwork blur + overlay gelap (fallback gradient netral).
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF2C2C2E), Color(0xFF1C1C1E)))),
        )
        if (cur?.artwork != null) {
            AsyncImage(
                model = cur.artwork,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(40.dp).graphicsLayer { scaleX = 1.25f; scaleY = 1.25f },
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        Column(Modifier.fillMaxSize()) {
            // Drag handle ala Apple + tombol tutup.
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { player.setExpanded(false) }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Tutup", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.width(36.dp).height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { if (cur != null) menu = cur }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu lagu", tint = Color.White)
                }
            }
            if (cur != null) {
                // Artwork full-bleed (tanpa kartu) seperti Apple Music.
                if (cur.artwork != null) {
                    Artwork(
                        model = cur.artwork,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        radius = 0.dp,
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .background(Color(0x2E000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(cur.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.basicMarquee())
                        Text(cur.artist, fontSize = 19.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.basicMarquee())
                    }
                    val heartScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isFav) 1.18f else 1f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioHighBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                        ),
                        label = "heartPop",
                    )
                    IconButton(onClick = { player.let { plVm.toggleFavorite(cur) } }) {
                        Icon(
                            if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFav) "Hapus dari Favorit" else "Tambah ke Favorit",
                            tint = if (isFav) Accent else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.graphicsLayer { scaleX = heartScale; scaleY = heartScale },
                        )
                    }
                }

                // Seekbar real: drag preview + seekTo saat dilepas.
                var drag: Float? by remember(cur.key) { mutableStateOf(null) }
                val duration = state.durationMs.coerceAtLeast(1)
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Slider(
                        value = drag ?: state.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = { drag = it },
                        valueRange = 0f..duration.toFloat(),
                        onValueChangeFinished = {
                            drag?.let { player.seekTo(it.toLong()) }
                            drag = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(fmtMs(drag?.toLong() ?: state.positionMs), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        Spacer(Modifier.weight(1f))
                        Text("−" + fmtMs((duration - (drag?.toLong() ?: state.positionMs)).coerceAtLeast(0)), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = { player.prev() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    IconButton(onClick = { player.togglePlayPause() }, modifier = Modifier.weight(1f)) {
                        androidx.compose.animation.Crossfade(
                            targetState = state.isPlaying,
                            animationSpec = androidx.compose.animation.core.tween(200),
                            label = "npPlayPause",
                        ) { playing ->
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playing) "Jeda" else "Putar",
                                tint = Color.White,
                                modifier = Modifier.size(72.dp),
                            )
                        }
                    }
                    IconButton(onClick = { player.next() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                }
                // Volume real via AudioManager.
                val am = remember { ctx.getSystemService(AudioManager::class.java) }
                val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
                var vol by remember {
                    mutableFloatStateOf(
                        am.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max).toFloat(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VolumeDown, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(16.dp))
                    var volDrag: Float? by remember { mutableStateOf(null) }
                    Slider(
                        value = volDrag ?: vol,
                        onValueChange = { volDrag = it.coerceIn(0f, max.toFloat()) },
                        valueRange = 0f..max.toFloat(),
                        onValueChangeFinished = {
                            volDrag?.let {
                                vol = it
                                am.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
                            }
                            volDrag = null
                        },
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = Color.White.copy(alpha = 0.7f),
                            activeTrackColor = Color.White.copy(alpha = 0.45f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                        ),
                        modifier = Modifier.weight(1f).height(24.dp),
                    )
                    Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(16.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { player.loadLyrics(); showLyrics = true }) {
                        Icon(Icons.Filled.Subtitles, contentDescription = "Lirik", tint = Color.White.copy(alpha = 0.8f))
                    }
                    Spacer(Modifier.width(24.dp))
                    IconButton(
                        onClick = {
                            scope.launch {
                                snack.showSnackbar("Output audio: hanya speaker/earphone di app ini")
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Speaker, contentDescription = "Output audio", tint = Color.White.copy(alpha = 0.35f))
                    }
                    Spacer(Modifier.width(24.dp))
                    IconButton(onClick = { player.toggleQueue() }) {
                        Icon(Icons.Filled.QueueMusic, contentDescription = "Queue", tint = Color.White.copy(alpha = 0.8f))
                    }
                    Spacer(Modifier.weight(1f))
                }
                if (state.showQueue) {
                    GlassSheet(artwork = cur.artwork, onDismiss = { player.toggleQueue(false) }) {
                        QueuePanel(state = state, player = player, curKey = cur.key, onAddSongs = onAddSongs)
                    }
                }
                if (showLyrics) {
                    GlassSheet(artwork = cur.artwork, onDismiss = { showLyrics = false }) {
                        LyricsPanel(
                            lyricsState = lyricsState,
                            positionMs = state.positionMs,
                            title = cur.title,
                            artist = cur.artist,
                            onSeek = { player.seekTo(it) },
                        )
                    }
                }
            } else {
                Text("Tidak ada lagu. Pilih dari Library atau Search.", Modifier.padding(20.dp))
            }
        }
        com.zaaam.vmusix.ui.common.TrackSheet(
            track = menu,
            playlists = pls,
            onDismiss = { menu = null },
            onPlayNext = { player.playNext(it) },
            onPlayLast = { player.addToQueue(it) },
            onCreatePlaylist = { plVm.create(it) },
            onAddToPlaylist = { id, t -> plVm.addTrack(id, t) },
            isFavorite = menu?.let { favKeys.contains(it.key) } ?: false,
            onToggleFavorite = { plVm.toggleFavorite(it) },
        )
    }
}

/**
 * Panel Up Next meniru Apple Music: header lagu berjalan + pil
 * Shuffle/Repeat + "Playing Next" + Clear + baris ber-cover + Add Songs.
 */
@Composable
private fun QueuePanel(
    state: PlayerUiState,
    player: PlaybackViewModel,
    curKey: String,
    onAddSongs: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val cur = state.queue.find { it.key == curKey }
        if (cur != null) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(model = cur.artwork, modifier = Modifier.size(44.dp), radius = 6.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(cur.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(cur.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            QueuePill(
                label = if (state.shuffleEnabled) "Shuffle On" else "Shuffle",
                icon = Icons.Filled.Shuffle,
                on = state.shuffleEnabled,
                modifier = Modifier.weight(1f),
                onClick = { player.setShuffle(!state.shuffleEnabled) },
            )
            Spacer(Modifier.width(8.dp))
            val rpLabel = when (state.repeatMode) {
                Player.REPEAT_MODE_ONE -> "Repeat 1"
                Player.REPEAT_MODE_ALL -> "Repeat"
                else -> "Repeat"
            }
            QueuePill(
                label = rpLabel,
                icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                on = state.repeatMode != Player.REPEAT_MODE_OFF,
                modifier = Modifier.weight(1f),
                onClick = {
                    player.setRepeat(
                        when (state.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        },
                    )
                },
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Playing Next", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { player.clearQueue() }) { Text("Clear") }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            itemsIndexed(state.queue, key = { idx, t -> "$idx:${t.key.hashCode()}" }) { idx, t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(model = t.artwork, modifier = Modifier.size(44.dp), radius = 6.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            t.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (t.key == curKey) Accent else Color.White,
                        )
                        Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                    IconButton(onClick = { player.moveQueue(idx, idx - 1) }, enabled = idx > 0) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Naik")
                    }
                    IconButton(onClick = { player.moveQueue(idx, idx + 1) }, enabled = idx < state.queue.size - 1) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Turun")
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onAddSongs).padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Text("Add Songs", fontSize = 16.sp)
                }
            }
        }
        TextButton(onClick = { player.toggleQueue(false) }, modifier = Modifier.fillMaxWidth()) {
            Text("Tutup")
        }
    }
}

@Composable
private fun QueuePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) Accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f)),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, color = Color.White)
    }
}

private fun fmtMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/**
 * Sheet kaca dipakai bareng Lirik & Antrian: artwork lagu di-blur jadi latar, ditutup scrim gelap
 * (teks tetap terbaca), tepi atas diberi rim-light. Container asli ModalBottomSheet dibuat transparan.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GlassSheet(artwork: String?, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        dragHandle = null,
        shape = shape,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
    ) {
        Box(
            Modifier.fillMaxWidth().clip(shape).background(Color(0xFF101012))
                .border(1.dp, com.zaaam.vmusix.ui.theme.GlassRim, shape),
        ) {
            Box(Modifier.fillMaxSize()) {
                Artwork(model = artwork, modifier = Modifier.fillMaxSize().blur(44.dp).graphicsLayer(alpha = 0.5f), radius = 0.dp)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.78f))),
                    ),
                )
            }
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 6.dp)
                        .size(width = 38.dp, height = 5.dp).clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.4f)),
                )
                content()
            }
        }
    }
}

/**
 * Panel lirik. Sumber: LyricsRepository (LRCLIB via lrcmux). Versi synced: baris aktif membesar/terang
 * dengan animasi, daftar auto-scroll mengikuti lagu, ketuk baris = loncat ke bagian itu.
 */
@Composable
private fun LyricsPanel(
    lyricsState: LyricsState,
    positionMs: Long,
    title: String,
    artist: String,
    onSeek: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(artist, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 10.dp))
        when (lyricsState) {
            is LyricsState.Idle, is LyricsState.Loading -> {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Accent)
                }
            }
            is LyricsState.NotFound -> {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp).glass(com.zaaam.vmusix.ui.theme.GlassShapeLg).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Lirik untuk lagu ini belum ketemu.", color = Color.White.copy(alpha = 0.7f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            is LyricsState.Success -> {
                val lyrics = lyricsState.lyrics
                if (lyrics.hasSynced) {
                    val active = lyrics.synced.indexOfLast { it.timeMs <= positionMs }
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LaunchedEffect(active) {
                        if (active >= 0) listState.animateScrollToItem(active, scrollOffset = -160)
                    }
                    Box(Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 500.dp).appear(80)) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 40.dp)) {
                            itemsIndexed(lyrics.synced) { i, line ->
                                val dist = kotlin.math.abs(i - active)
                                val alpha by androidx.compose.animation.core.animateFloatAsState(
                                    if (i == active) 1f else if (dist == 1) 0.55f else 0.32f, androidx.compose.animation.core.tween(320), label = "lyAlpha",
                                )
                                val scale by androidx.compose.animation.core.animateFloatAsState(
                                    if (i == active) 1f else 0.84f, androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow), label = "lyScale",
                                )
                                Text(
                                    line.text.ifBlank { "♪" },
                                    fontSize = 27.sp,
                                    lineHeight = 33.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null,
                                        ) { onSeek(line.timeMs) }
                                        .graphicsLayer {
                                            this.alpha = alpha
                                            scaleX = scale; scaleY = scale
                                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                        }
                                        .padding(vertical = 9.dp),
                                )
                            }
                        }
                        // Pudar di tepi atas/bawah biar lirik "keluar-masuk" halus.
                        Box(Modifier.fillMaxWidth().height(36.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
                        Box(Modifier.fillMaxWidth().height(36.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 500.dp).appear(80), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
                        item { Text(lyrics.plain, fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.92f)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
