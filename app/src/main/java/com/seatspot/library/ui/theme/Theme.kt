package com.seatspot.library.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.seatspot.library.R

val Paper = Color(0xFFF7F7F5)
val Paper2 = Color(0xFFFFFFFF)
val Ink = Color(0xFF111111)
val Muted = Color(0xFF5C5C5C)
val Line = Color(0xFFE6E6E6)
val Green = Color(0xFF3DDC97)
val GreenSoft = Color(0xFFE7F8EF)
val Accent = Color(0xFF111111)
val Danger = Color(0xFFE11D48)
val DangerSoft = Color(0xFFFFF1F2)
val Taken = Color(0xFFD4D4D4)

private fun outfit(weight: Int) = Font(
    R.font.outfit,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Sans = FontFamily(outfit(400), outfit(500), outfit(600), outfit(700))
val Display = Sans

private val Colors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper2,
    onSurface = Ink,
    error = Danger,
    outline = Line,
)

private val Type = Typography(
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp, color = Ink),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.6).sp, color = Ink),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp, color = Ink),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp, color = Muted),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.6.sp, color = Muted),
)

@Composable
fun SeatSpotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
