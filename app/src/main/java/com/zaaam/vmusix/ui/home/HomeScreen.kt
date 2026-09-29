package com.zaaam.vmusix.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import com.zaaam.vmusix.ui.theme.appear
import com.zaaam.vmusix.ui.theme.glass
import com.zaaam.vmusix.ui.theme.pressable
import com.zaaam.vmusix.ui.theme.GlassShapeLg
import com.zaaam.vmusix.ui.theme.GlassRim
import com.zaaam.vmusix.ui.theme.ArchShape
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zaaam.vmusix.domain.model.Track
import com.zaaam.vmusix.ui.common.Artwork
import com.zaaam.vmusix.ui.common.GENRES
import com.zaaam.vmusix.ui.common.GenreTile
import com.zaaam.vmusix.ui.common.LargeTitle
import com.zaaam.vmusix.ui.common.ScanEmptyState
import com.zaaam.vmusix.ui.common.TrackRow
import com.zaaam.vmusix.ui.common.TrackSheet
import com.zaaam.vmusix.ui.library.LibraryViewModel
import com.zaaam.vmusix.ui.player.PlaybackViewModel
import com.zaaam.vmusix.ui.playlist.PlaylistViewModel
import com.zaaam.vmusix.ui.theme.Accent
import com.zaaam.vmusix.ui.theme.TextSecondary
import java.util.Calendar

/**
 * Home: 100% data nyata. Tidak ada hero rekomendasi dummy — bagian itu
 * di-skip sesuai aturan (rekomendasi algoritmik di luar scope v1).
 * Section "Browse" reuse GENRES/GenreTile yang sama dengan tab New/Search,
 * biar Home tetap ada konten nyata & fungsional walau histori masih dikit.
 */
@Composable
fun HomeScreen(
    player: PlaybackViewModel,
    libVm: LibraryViewModel,
    plVm: PlaylistViewModel,
    onGenre: (String) -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFavorit: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsState()
    val pls by plVm.playlists.collectAsState()
    val favKeys by plVm.favoritKeys.collectAsState()
    val lib by libVm.state.collectAsState()
    var menu by remember { mutableStateOf<Track?>(null) }
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val allPerms = remember {
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(audioPerm, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(audioPerm)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        libVm.scanIfEmpty(); vm.refresh()
    }
    LaunchedEffect(Unit) { vm.refresh() }
    // Scan selesai di tab mana pun (songCount berubah) -> Home ikut refresh.
    LaunchedEffect(lib.songCount) { vm.refresh() }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 4..10 -> "Selamat pagi"
            in 11..14 -> "Selamat siang"
            in 15..17 -> "Selamat sore"
            else -> "Selamat malam"
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { LargeTitle("Home") }
        item { Text(greeting, Modifier.padding(start = 16.dp, bottom = 8.dp), color = TextSecondary, fontSize = 14.sp) }
        // 1) Hero: lanjut dari lagu terakhir — kartu kaca dengan artwork sebagai backdrop.
        if (s.recent.isNotEmpty()) {
            item {
                val last = s.recent.first()
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().appear(0)
                        .pressable({ player.playTrack(last, s.recent) }, pressedScale = 0.97f)
                        .glass(GlassShapeLg),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Artwork(
                            model = last.artwork,
                            modifier = Modifier.fillMaxSize().blur(28.dp).graphicsLayer(alpha = 0.35f),
                            radius = 0.dp,
                        )
                    }
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(model = last.artwork, modifier = Modifier.size(92.dp), radius = 26.dp)
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text("LANJUT DENGERIN", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                            Text(last.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                            Text(last.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = TextSecondary)
                        }
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Accent).clickable { player.playTrack(last, s.recent) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Putar", tint = Color.White) }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().appear(90).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickChip("Favorit", Icons.Filled.Favorite, onOpenFavorit)
                QuickChip("Jelajah", Icons.Filled.Explore, onOpenBrowse)
                QuickChip("Cari", Icons.Filled.Search, onOpenSearch)
            }
        }
        // 2) Trending: kartu fitur lebar + kartu kotak yang naik-turun (staggered).
        item { SectionTitle("Trending") }
        when {
            s.trendingLoading && s.trending.isEmpty() -> item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Memuat trending…", Modifier.padding(start = 12.dp), color = TextSecondary)
                }
            }
            s.trendingError != null && s.trending.isEmpty() -> item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Gagal ambil data dari YouTube, coba lagi", color = TextSecondary)
                    Button(onClick = { vm.retryTrending() }, modifier = Modifier.padding(top = 8.dp)) { Text("Coba lagi") }
                }
            }
            s.trending.isNotEmpty() -> {
                item {
                    LazyRow(Modifier.padding(vertical = 8.dp).appear(170)) {
                        itemsIndexed(s.trending, key = { _, t -> t.key }) { i, t ->
                            if (i == 0) FeatureCard(t) { player.playTrack(t, s.trending) }
                            else MediaCard(t, Modifier.padding(top = if (i % 2 == 0) 0.dp else 24.dp)) { player.playTrack(t, s.trending) }
                        }
                    }
                }
                // 3) Artis: bentuk arch (bukan lingkaran/kotak biasa) — identitas visual VmusiX.
                item { SectionTitle("Artis Terpopuler") }
                item {
                    LazyRow(Modifier.padding(vertical = 8.dp)) {
                        items(s.trending.distinctBy { it.artist }.take(8), key = { it.artist }) { t ->
                            Column(
                                Modifier.padding(start = 16.dp).width(96.dp).pressable({ onGenre(t.artist) }),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Artwork(
                                    model = t.artwork,
                                    modifier = Modifier.size(width = 96.dp, height = 124.dp).border(1.dp, GlassRim, ArchShape),
                                    shape = ArchShape,
                                    fallbackIconSize = 32.dp,
                                )
                                Text(t.artist, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
            }
        }
        // 4) Aktivitas: statistik nyata dari data app.
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Riwayat", s.recent.size.toString(), Modifier.weight(1f))
                StatTile("Di perangkat", lib.songCount.toString(), Modifier.weight(1f))
                StatTile("Favorit", favKeys.size.toString(), Modifier.weight(1f))
            }
        }
        item { SectionTitle("Browse") }
        item {
            LazyRow(Modifier.padding(vertical = 8.dp)) {
                items(GENRES) { (name, colors) ->
                    Box(Modifier.padding(start = 10.dp).width(168.dp)) { GenreTile(name, colors) { onGenre(name) } }
                }
            }
        }
        item { SectionTitle("Recently Played") }
        if (s.recent.isEmpty()) {
            item { Text("Belum ada riwayat, mulai putar musik dari Library", Modifier.padding(16.dp), color = TextSecondary) }
        } else {
            item {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(GlassShapeLg).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    s.recent.take(5).forEach { t ->
                        TrackRow(track = t, onPlay = { player.playTrack(t, s.recent) }, onMenu = { menu = t })
                    }
                }
            }
        }
        item { SectionTitle("Recently Added") }
        if (s.newMusic.isEmpty()) {
            item {
                ScanEmptyState(
                    scanning = lib.scanning,
                    needsPermission = lib.needsPermission,
                    onScan = { if (libVm.hasPermission()) libVm.scan() else launcher.launch(allPerms) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            item {
                LazyRow(Modifier.padding(vertical = 8.dp)) {
                    items(s.newMusic, key = { it.key }) { t -> MediaCard(t) { player.playTrack(t, s.newMusic) } }
                }
            }
        }
        // 5) Koleksi: kolase artwork bentuk campur dari semua lagu yang ada.
        val collage = (s.trending + s.recent + s.newMusic).filter { it.artwork != null }.distinctBy { it.artwork }.take(6)
        if (collage.size >= 3) {
            item { SectionTitle("Koleksi") }
            item { ArtMosaic(collage) { player.playTrack(it, collage) } }
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
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 16.dp, top = 14.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun QuickChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.pressable(onClick).glass(RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.glass(RoundedCornerShape(20.dp)).padding(vertical = 12.dp, horizontal = 14.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}

/** Kartu fitur lebar: artwork penuh + scrim + judul di atasnya. */
@Composable
private fun FeatureCard(t: Track, onClick: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Box(Modifier.padding(start = 16.dp).size(width = 280.dp, height = 190.dp).pressable(onClick).clip(shape).border(1.dp, GlassRim, shape)) {
        Artwork(model = t.artwork, modifier = Modifier.fillMaxSize(), shape = shape, fallbackIconSize = 56.dp)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))
        Text("#1 TRENDING", Modifier.align(Alignment.TopStart).padding(14.dp).glass(RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(t.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(t.artist, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MediaCard(t: Track, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(modifier.padding(start = 14.dp).width(148.dp).pressable(onClick)) {
        Artwork(model = t.artwork, modifier = Modifier.size(148.dp).border(1.dp, GlassRim, shape), shape = shape, fallbackIconSize = 48.dp)
        Text(t.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(t.artist, fontSize = 12.sp, maxLines = 1, color = TextSecondary, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ArtMosaic(tracks: List<Track>, onClick: (Track) -> Unit) {
    fun t(i: Int) = tracks[i % tracks.size]
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MosaicTile(t(0), Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(36.dp), onClick)
            MosaicTile(t(1), Modifier.fillMaxWidth().aspectRatio(1.6f), RoundedCornerShape(22.dp), onClick)
            MosaicTile(t(2), Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(topStart = 64.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp), onClick)
        }
        Column(Modifier.weight(1f).padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MosaicTile(t(3), Modifier.fillMaxWidth().aspectRatio(0.78f), ArchShape, onClick)
            MosaicTile(t(4), Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(26.dp), onClick)
            MosaicTile(t(5), Modifier.fillMaxWidth().aspectRatio(1.3f), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 56.dp), onClick)
        }
    }
}

@Composable
private fun MosaicTile(t: Track, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape, onClick: (Track) -> Unit) {
    Box(modifier.pressable({ onClick(t) }).clip(shape).border(1.dp, GlassRim, shape)) {
        Artwork(model = t.artwork, modifier = Modifier.fillMaxSize(), shape = shape)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
        Text(t.title, Modifier.align(Alignment.BottomStart).padding(12.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
