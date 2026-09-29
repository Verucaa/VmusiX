package com.zaaam.vmusix.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zaaam.vmusix.R

val BgMain = Color(0xFF000000)
val SurfaceElevated = Color(0xFF1C1C1E)
val SurfaceSecondary = Color(0xFF2C2C2E)
val Divider = Color(0x14FFFFFF)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0x99FFFFFF)
val TextHint = Color(0x66FFFFFF)
val Accent = Color(0xFFFA233B)

// Inter (OFL) dibundle sebagai font resource — pengganti SF Pro yang
// berlisensi khusus Apple dan tidak boleh didistribusikan.
val AppFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 34.sp),
    titleLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 11.sp),
)

private val Scheme = darkColorScheme(
    background = BgMain,
    surface = SurfaceElevated,
    surfaceVariant = SurfaceSecondary,
    primary = Accent,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun VmusiXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography) {
        CompositionLocalProvider(
            // Icon tanpa tint memakai LocalContentColor. Default-nya HITAM kalau tidak ada Surface/Scaffold
            // yang mengisinya — jangan sampai bergantung pada warna container (Scaffold transparan => ikon hitam).
            LocalContentColor provides TextPrimary,
            LocalTextStyle provides TextStyle(fontFamily = AppFont, color = TextPrimary),
        ) {
            content()
        }
    }
}
