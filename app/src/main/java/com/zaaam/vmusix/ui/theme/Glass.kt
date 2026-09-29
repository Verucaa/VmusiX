package com.zaaam.vmusix.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Design system "VmusiX Glass" — satu bahasa visual buat SEMUA layar:
 * permukaan translusen berlapis, rim-light tipis (highlight kiri-atas), dan
 * ambient glow di belakang biar kaca kelihatan punya kedalaman.
 */
val GlassShape = RoundedCornerShape(22.dp)
val GlassShapeLg = RoundedCornerShape(30.dp)

/** Bentuk arch: atas melengkung penuh, bawah membulat lembut — ciri visual VmusiX buat artis/koleksi. */
val ArchShape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp, bottomStart = 20.dp, bottomEnd = 20.dp)

/** Rim-light: terang di sudut kiri-atas, memudar ke kanan-bawah — kesan pantulan cahaya di tepi kaca. */
val GlassRim = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.16f)))

fun Modifier.glass(shape: Shape = GlassShape, strength: Float = 1f): Modifier = this
    .clip(shape)
    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.13f * strength), Color.White.copy(alpha = 0.05f * strength))))
    .border(1.dp, GlassRim, shape)

/** Latar seluruh app: hitam + 3 glow lembut (merah aksen, ungu, biru) supaya permukaan kaca punya sesuatu buat "dibiaskan". */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.background(BgMain).drawBehind {
            drawRect(Brush.radialGradient(listOf(Accent.copy(alpha = 0.24f), Color.Transparent), center = Offset(size.width * 0.1f, size.height * 0.04f), radius = size.width * 0.95f))
            drawRect(Brush.radialGradient(listOf(Color(0xFF5B3FD6).copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width * 0.98f, size.height * 0.5f), radius = size.width * 1.0f))
            drawRect(Brush.radialGradient(listOf(Color(0xFF0A84FF).copy(alpha = 0.13f), Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.97f), radius = size.width * 0.9f))
        },
        content = content,
    )
}
