package com.zaaam.vmusix.ui.common

import androidx.compose.foundation.background
import com.zaaam.vmusix.ui.theme.pressable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Satu-satunya sumber genre buat tile "Browse Categories" — dipakai sama
 * persis di tab New (`BrowseScreen`) dan kosong-state Search (`SearchScreen`)
 * biar dua layar itu gak pernah beda lagi (UI.md §9.3: wajib 8 tile sama).
 */
val GENRES: List<Pair<String, Pair<Color, Color>>> = listOf(
    "Pop" to (Color(0xFFFC5C7D) to Color(0xFF6A82FB)),
    "Hip-Hop" to (Color(0xFFF7B733) to Color(0xFFFC4A1A)),
    "R&B" to (Color(0xFF8E2DE2) to Color(0xFF4A00E0)),
    "Electronic" to (Color(0xFF11998E) to Color(0xFF38EF7D)),
    "Rock" to (Color(0xFF414345) to Color(0xFF232526)),
    "Jazz" to (Color(0xFFC79081) to Color(0xFFDFA579)),
    "Classical" to (Color(0xFF2C5364) to Color(0xFF0F2027)),
    "Metal" to (Color(0xFF434343) to Color(0xFF000000)),
)

/** Tile genre 110dp radius 12 + watermark ♪ ala Apple Music (UI.md §3). */
@Composable
fun GenreTile(name: String, colors: Pair<Color, Color>, onClick: () -> Unit) {
    Box(
        Modifier.padding(6.dp).fillMaxWidth().height(110.dp)
            .pressable(onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(
            "♪",
            fontSize = 44.sp,
            color = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
