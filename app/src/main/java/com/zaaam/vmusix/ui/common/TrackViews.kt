package com.zaaam.vmusix.ui.common

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import com.zaaam.vmusix.ui.theme.pressable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zaaam.vmusix.R
import com.zaaam.vmusix.data.local.PlaylistEntity
import com.zaaam.vmusix.domain.model.Track
import com.zaaam.vmusix.ui.theme.Accent
import com.zaaam.vmusix.ui.theme.Divider as DividerColor
import com.zaaam.vmusix.ui.theme.TextSecondary

/**
 * Artwork nyata (MediaStore album art / thumbnail YT) + fallback kaca + ikon
 * kalau null/gagal load. Dipakai di SEMUA list agar tidak ada kotak kosong.
 */
@Composable
fun Artwork(
    model: Any?,
    modifier: Modifier = Modifier,
    radius: Dp = 6.dp,
    fallbackIconSize: Dp = 22.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(radius),
) {
    if (model != null) {
        // Thumbnail YouTube (sddefault/hqdefault) itu 4:3 dan bar hitam atas-bawahnya sudah "kepanggang"
        // di gambar. Kalau terdeteksi 4:3, zoom ke area tengah supaya bar-nya kepotong (sisanya cuma cover).
        var zoom by remember(model) { androidx.compose.runtime.mutableFloatStateOf(1f) }
        Box(modifier.clip(shape)) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom },
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onSuccess = { st ->
                    val sz = st.painter.intrinsicSize
                    if (sz.width > 0f && sz.height > 0f) zoom = if (sz.width / sz.height in 1.25f..1.42f) 1.34f else 1f
                },
                error = painterResource(R.drawable.ic_music_note),
                fallback = painterResource(R.drawable.ic_music_note),
            )
        }
    } else {
        Box(
            modifier
                .clip(shape)
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(fallbackIconSize),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    subtitle: String = track.artist,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .pressable(onClick = onPlay, onLongClick = onMenu, pressedScale = 0.985f)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(model = track.artwork, modifier = Modifier.size(54.dp), radius = 14.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextSecondary)
            }
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Menu lagu",
                tint = TextSecondary,
                modifier = Modifier.clickable(onClick = onMenu).padding(8.dp),
            )
        }
    }
}

/** Action sheet: Play Next / Play Last / Favorit / Add to Playlist / Bagikan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(
    track: Track?,
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onPlayNext: (Track) -> Unit,
    onPlayLast: (Track) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onAddToPlaylist: (Long, Track) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (Track) -> Unit = {},
) {
    if (track == null) return
    val ctx = LocalContext.current
    var picking by remember(track.key) { mutableStateOf(false) }
    var newName by remember(track.key) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(model = track.artwork, modifier = Modifier.size(52.dp), radius = 8.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${track.artist} • ${if (track.source is com.zaaam.vmusix.domain.model.PlaybackSource.YouTube) "YouTube" else "Perangkat"}",
                    color = TextSecondary,
                )
            }
        }
        if (!picking) {
            SheetAction("Play Next", Icons.Filled.PlayArrow) { onPlayNext(track); onDismiss() }
            SheetAction("Play Last", Icons.Filled.PlaylistAdd) { onPlayLast(track); onDismiss() }
            SheetAction(
                if (isFavorite) "Hapus dari Favorit" else "Tambah ke Favorit",
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (isFavorite) Accent else TextSecondary,
            ) { onToggleFavorite(track); onDismiss() }
            SheetAction("Add to Playlist…", Icons.Filled.CreateNewFolder) { picking = true }
            SheetAction("Bagikan", Icons.Filled.Share) {
                // Real share intent — teks aja (judul+artis+link YT kalau ada), bukan file mentah.
                val src = track.source
                val text = if (src is com.zaaam.vmusix.domain.model.PlaybackSource.YouTube) {
                    "${track.title} - ${track.artist}\nhttps://youtu.be/${src.videoId}"
                } else {
                    "${track.title} - ${track.artist}"
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                ctx.startActivity(Intent.createChooser(intent, "Bagikan lagu"))
                onDismiss()
            }
        } else {
            playlists.forEach { p ->
                SheetAction(p.name, Icons.Filled.PlaylistAdd) { onAddToPlaylist(p.id, track); onDismiss() }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Playlist baru…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { if (newName.isNotBlank()) { onCreatePlaylist(newName.trim()); onDismiss() } }) {
                    Text("Buat")
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = TextSecondary,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(end = 16.dp))
            Text(label)
        }
    }
}

/** Large title 34/700 ala Apple Music, satu per tab. */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
    Text(
        text,
        fontSize = 34.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        fontFamily = com.zaaam.vmusix.ui.theme.AppFont,
        modifier = modifier,
    )
}
