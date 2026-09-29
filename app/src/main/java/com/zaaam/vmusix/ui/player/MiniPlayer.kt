package com.zaaam.vmusix.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaaam.vmusix.ui.common.Artwork
import com.zaaam.vmusix.ui.theme.TextSecondary

/** PRD-010: tidak dirender sama sekali saat idle. Hairline progress ala Apple Music. */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onTap: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cur = state.current ?: return
    Column(modifier.fillMaxWidth().clickable(onClick = onTap)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(model = cur.artwork, modifier = Modifier.size(46.dp), radius = 12.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(cur.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cur.artist, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextSecondary)
            }
            IconButton(onClick = onToggle) {
                androidx.compose.animation.Crossfade(targetState = state.isPlaying, animationSpec = androidx.compose.animation.core.tween(180), label = "miniPP") { playing ->
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Jeda" else "Putar",
                    )
                }
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Lagu berikutnya")
            }
        }
        if (state.durationMs > 0) {
            LinearProgressIndicator(
                progress = { (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Color.White.copy(alpha = 0.6f),
                trackColor = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}
