package com.zaaam.vmusix.ui.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import com.zaaam.vmusix.ui.theme.glass
import com.zaaam.vmusix.ui.theme.pressable
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zaaam.vmusix.data.local.PlaylistTrackEntity
import com.zaaam.vmusix.domain.model.Track
import com.zaaam.vmusix.ui.common.Artwork
import com.zaaam.vmusix.ui.common.LargeTitle
import com.zaaam.vmusix.ui.common.TrackRow
import com.zaaam.vmusix.ui.common.TrackSheet
import com.zaaam.vmusix.ui.player.PlaybackViewModel
import com.zaaam.vmusix.ui.playlist.PlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface LibView {
    data object Main : LibView
    data object Songs : LibView
    data object Artists : LibView
    data class Artist(val name: String) : LibView
    data object Albums : LibView
    data class Album(val name: String) : LibView
    data object Playlists : LibView
    data class Playlist(val id: Long, val name: String) : LibView
}

@Composable
fun LibraryScreen(
    player: PlaybackViewModel,
    vm: LibraryViewModel,
    plVm: PlaylistViewModel,
    openFavorit: Boolean = false,
) {
    val state by vm.state.collectAsState()
    val pls by plVm.playlists.collectAsState()
    val favKeys by plVm.favoritKeys.collectAsState()
    // View tahan rotasi (rememberSaveable String, bukan objek).
    var viewKey by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var view: LibView by remember(viewKey) {
        mutableStateOf(
            when {
                viewKey == null -> LibView.Main
                viewKey == "songs" -> LibView.Songs
                viewKey == "artists" -> LibView.Artists
                viewKey == "albums" -> LibView.Albums
                viewKey == "playlists" -> LibView.Playlists
                viewKey!!.startsWith("artist:") -> LibView.Artist(viewKey!!.removePrefix("artist:"))
                viewKey!!.startsWith("album:") -> LibView.Album(viewKey!!.removePrefix("album:"))
                viewKey!!.startsWith("playlist:") -> {
                    val rest = viewKey!!.removePrefix("playlist:")
                    LibView.Playlist(rest.substringBefore("|").toLongOrNull() ?: -1, rest.substringAfter("|", ""))
                }
                else -> LibView.Main
            },
        )
    }
    fun go(v: LibView) {
        viewKey = when (v) {
            LibView.Main -> null
            LibView.Songs -> "songs"
            LibView.Artists -> "artists"
            LibView.Albums -> "albums"
            LibView.Playlists -> "playlists"
            is LibView.Artist -> "artist:${v.name}"
            is LibView.Album -> "album:${v.name}"
            is LibView.Playlist -> "playlist:${v.id}|${v.name}"
        }
        view = v
    }
    // Deep-link dari Home: "Favorit" itu playlist otomatis (lihat PlaylistViewModel.ensureFavorit),
    // jadi begitu daftar playlist ke-load, lompat langsung ke situ.
    LaunchedEffect(openFavorit, pls) {
        if (openFavorit && viewKey == null) {
            pls.find { it.name == "Favorit" }?.let { go(LibView.Playlist(it.id, it.name)) }
        }
    }
    var menu by remember { mutableStateOf<Track?>(null) }
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val allPerms = remember {
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(audioPerm, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(audioPerm)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.scanIfEmpty() }
    LaunchedEffect(Unit) { vm.scanIfEmpty() }

    if (state.needsPermission) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Perlu izin audio untuk membaca folder Music/VmusiX.")
            Text(
                "Taruh file lagu di folder Music/VmusiX, lalu pindai.",
                color = com.zaaam.vmusix.ui.theme.TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { launcher.launch(allPerms) }) { Text("Pindai folder VmusiX") }
        }
        return
    }

    val songList = state.songs
    fun playAll(from: Track) = player.playTrack(from, songList)

    Column(Modifier.fillMaxSize()) {
        if (view != LibView.Main) {
            TextButton(onClick = { go(LibView.Main) }) { Text("‹ Library") }
        }
        when (val v = view) {
            LibView.Main -> {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    item { LargeTitle("Library", modifier = Modifier.padding(vertical = 6.dp)) }
                    item {
                        Column(Modifier.glass(com.zaaam.vmusix.ui.theme.GlassShapeLg).padding(horizontal = 12.dp)) {
                            CatRow(Icons.Filled.Favorite, "Favorit") {
                                pls.find { it.name == "Favorit" }?.let { go(LibView.Playlist(it.id, it.name)) } ?: go(LibView.Playlists)
                            }
                            CatRow(Icons.Filled.QueueMusic, "Playlists") { go(LibView.Playlists) }
                            CatRow(Icons.Filled.Person, "Artists") { go(LibView.Artists) }
                            CatRow(Icons.Filled.Album, "Albums") { go(LibView.Albums) }
                            CatRow(Icons.Filled.MusicNote, "Songs") { go(LibView.Songs) }
                        }
                    }
                    item {
                        Text("Recently Added", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                        if (state.recentlyAdded.isEmpty()) {
                            com.zaaam.vmusix.ui.common.ScanEmptyState(
                                scanning = state.scanning,
                                needsPermission = false,
                                onScan = { vm.scan() },
                            )
                        }
                        LazyRow {
                            items(state.recentlyAdded, key = { it.key }) { t ->
                                Column(Modifier.padding(end = 12.dp).width(140.dp).clickable { playAll(t) }) {
                                    Artwork(model = t.artwork, modifier = Modifier.width(140.dp).height(140.dp), radius = 10.dp, fallbackIconSize = 48.dp)
                                    Text(t.title, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(t.artist, fontSize = 12.sp, maxLines = 1, color = com.zaaam.vmusix.ui.theme.TextSecondary, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text("Songs • ${state.songCount}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Music/VmusiX" + if (state.lastScanCount >= 0) " • scan: ${state.lastScanCount} lagu" else "",
                                    fontSize = 12.sp,
                                    color = com.zaaam.vmusix.ui.theme.TextSecondary,
                                )
                            }
                            TextButton(onClick = { vm.scan() }) { Text(if (state.scanning) "Memindai…" else "Refresh") }
                        }
                        val scanErr = state.scanError
                        if (scanErr != null) {
                            Text(
                                scanErr,
                                fontSize = 12.sp,
                                color = Color(0xFFFF9D9D),
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                    items(songList, key = { it.key }) { t ->
                        TrackRow(track = t, onPlay = { playAll(t) }, onMenu = { menu = t })
                    }
                }
            }
            LibView.Songs -> SongList(songList, onPlay = { playAll(it) }, onMenu = { menu = it })
            LibView.Artists -> NameList("Artists", state.artists) { go(LibView.Artist(it)) }
            is LibView.Artist -> {
                val list = songList.filter { it.artist == v.name }
                SongList(list, title = v.name, onPlay = { player.playTrack(it, list) }, onMenu = { menu = it })
            }
            LibView.Albums -> NameList("Albums", state.albums) { go(LibView.Album(it)) }
            is LibView.Album -> {
                val list = songList.filter { it.album == v.name }
                SongList(list, title = v.name, onPlay = { player.playTrack(it, list) }, onMenu = { menu = it })
            }
            LibView.Playlists -> PlaylistList(
                plVm = plVm,
                onOpen = { id, name -> go(LibView.Playlist(id, name)) },
                onCreate = { plVm.create(it) },
            )
            is LibView.Playlist -> PlaylistDetail(
                id = v.id,
                name = v.name,
                plVm = plVm,
                player = player,
                onDelete = { plVm.deletePlaylist(v.id); go(LibView.Playlists) },
            )
        }
    }
    TrackSheet(
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

@Composable
private fun CatRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().pressable(onClick = onClick, pressedScale = 0.985f).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            // Glyph polos ala Apple Music (tanpa kotak).
            Icon(icon, contentDescription = null, tint = com.zaaam.vmusix.ui.theme.Accent, modifier = Modifier.width(34.dp).size(22.dp))
            Text(label, Modifier.weight(1f), fontSize = 17.sp)
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = com.zaaam.vmusix.ui.theme.TextHint)
        }
        // Inset: divider mulai setelah ikon (34dp), bukan full-width.
        HorizontalDivider(color = com.zaaam.vmusix.ui.theme.Divider, modifier = Modifier.padding(start = 34.dp))
    }
}

@Composable
private fun SongList(list: List<Track>, title: String? = null, onPlay: (Track) -> Unit, onMenu: (Track) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (title != null) item { Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
        if (list.isEmpty()) item { Text("Kosong.", color = com.zaaam.vmusix.ui.theme.TextSecondary) }
        items(list, key = { it.key }) { t -> TrackRow(track = t, onPlay = { onPlay(t) }, onMenu = { onMenu(t) }) }
    }
}

@Composable
private fun NameList(title: String, names: List<String>, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
        if (names.isEmpty()) item { Text("Kosong.", color = com.zaaam.vmusix.ui.theme.TextSecondary) }
        items(names) { n ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(n) }.padding(vertical = 12.dp)) {
                Text(n, Modifier.weight(1f), fontSize = 17.sp)
                Text("›", color = com.zaaam.vmusix.ui.theme.TextHint)
            }
        }
    }
}

@Composable
private fun PlaylistList(plVm: PlaylistViewModel, onOpen: (Long, String) -> Unit, onCreate: (String) -> Unit) {
    val pls by plVm.playlists.collectAsState()
    var name by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text("Playlists", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Playlist baru…") },
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { if (name.isNotBlank()) { onCreate(name.trim()); name = "" } }) { Text("Buat") }
            }
        }
        items(pls, key = { it.id }) { p ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(p.id, p.name) }.padding(vertical = 12.dp)) {
                Text(p.name, Modifier.weight(1f), fontSize = 17.sp)
                Text("›", color = com.zaaam.vmusix.ui.theme.TextHint)
            }
        }
    }
}

@Composable
private fun PlaylistDetail(
    id: Long,
    name: String,
    plVm: PlaylistViewModel,
    player: PlaybackViewModel,
    onDelete: () -> Unit,
) {
    var tracks by remember(id) { mutableStateOf<List<PlaylistTrackEntity>>(emptyList()) }
    var menu by remember { mutableStateOf<Track?>(null) }
    val pls by plVm.playlists.collectAsState()
    val favKeys by plVm.favoritKeys.collectAsState()
    LaunchedEffect(id, pls) {
        tracks = withContext(Dispatchers.IO) { plVm.tracksOf(id) }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                TextButton(onClick = onDelete) { Text("Hapus", color = Color.Red) }
            }
        }
        if (tracks.isEmpty()) item { Text("Belum ada lagu.", color = com.zaaam.vmusix.ui.theme.TextSecondary) }
        items(tracks, key = { "${it.playlistId}:${it.position}" }) { e ->
            val t = e.toTrack()
            TrackRow(
                track = t,
                subtitle = "${e.title} • ${e.artist}",
                onPlay = {
                    val list = tracks.map { it.toTrack() }
                    player.playTrack(t, list)
                },
                onMenu = { menu = t },
            )
        }
    }
    TrackSheet(
        track = menu, playlists = pls, onDismiss = { menu = null },
        onPlayNext = { player.playNext(it) }, onPlayLast = { player.addToQueue(it) },
        onCreatePlaylist = { plVm.create(it) }, onAddToPlaylist = { pid, t -> plVm.addTrack(pid, t) },
        isFavorite = menu?.let { favKeys.contains(it.key) } ?: false,
        onToggleFavorite = { plVm.toggleFavorite(it) },
    )
}

private fun PlaylistTrackEntity.toTrack(): Track {
    val src = if (source == "youtube" && videoId != null) {
        com.zaaam.vmusix.domain.model.PlaybackSource.YouTube(videoId)
    } else {
        com.zaaam.vmusix.domain.model.PlaybackSource.Local(android.net.Uri.parse(localUri ?: ""))
    }
    return Track(trackKey, title, artist, "", 0L, artwork, src)
}
