package com.zaaam.vmusix.ui.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaaam.vmusix.ui.common.GENRES
import com.zaaam.vmusix.ui.common.GenreTile
import com.zaaam.vmusix.ui.common.LargeTitle

/**
 * PRD-007: konten boleh curated, tapi tap genre WAJIB query nyata —
 * navigasi ke Search dengan preset query genre tersebut.
 */
@Composable
fun BrowseScreen(onGenre: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LargeTitle("New", modifier = Modifier.padding(vertical = 6.dp))
        Text("Browse Categories", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "Tap genre untuk mencari yang nyata (lokal + YouTube).",
            color = com.zaaam.vmusix.ui.theme.TextSecondary,
            fontSize = 13.sp,
        )
        LazyVerticalGrid(columns = GridCells.Fixed(2)) {
            items(GENRES) { (name, colors) ->
                GenreTile(name, colors) { onGenre(name) }
            }
        }
    }
}
