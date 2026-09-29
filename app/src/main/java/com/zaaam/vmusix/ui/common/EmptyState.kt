package com.zaaam.vmusix.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaaam.vmusix.ui.theme.GlassShapeLg
import com.zaaam.vmusix.ui.theme.TextSecondary
import com.zaaam.vmusix.ui.theme.glass

/** Empty state "belum ada lagu lokal" — tombolnya memicu pemindaian beneran (bukan hiasan). */
@Composable
fun ScanEmptyState(
    scanning: Boolean,
    needsPermission: Boolean,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().glass(GlassShapeLg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(34.dp))
        Text("Belum ada lagu di perangkat", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(
            if (needsPermission) "Perlu izin audio untuk memindai musik di perangkat." else "Taruh lagu di folder Music/VmusiX, lalu pindai.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(onClick = onScan, enabled = !scanning, modifier = Modifier.padding(top = 12.dp)) {
            Text(if (scanning) "Memindai…" else if (needsPermission) "Beri izin & pindai" else "Pindai sekarang")
        }
    }
}
