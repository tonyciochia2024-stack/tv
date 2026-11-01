package com.pets.desktoppets.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pets.desktoppets.R

/** Base font of the whole app: Liberation Serif, the open Times New Roman-compatible serif (same metrics and look). */
val PetsFont = FontFamily(
    Font(R.font.serif_regular, FontWeight.Normal), Font(R.font.serif_regular, FontWeight.Medium),
    Font(R.font.serif_bold, FontWeight.SemiBold), Font(R.font.serif_bold, FontWeight.Bold), Font(R.font.serif_bold, FontWeight.ExtraBold),
    Font(R.font.serif_italic, FontWeight.Normal, FontStyle.Italic), Font(R.font.serif_bolditalic, FontWeight.Bold, FontStyle.Italic),
)

private fun Typography.serif(): Typography {
    fun TextStyle.f() = copy(fontFamily = PetsFont)
    return Typography(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f().copy(fontSize = 18.sp), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f().copy(fontSize = 18.sp), bodyMedium = bodyMedium.f().copy(fontSize = 17.sp, lineHeight = 23.sp),
        bodySmall = bodySmall.f().copy(fontSize = 15.sp), labelLarge = labelLarge.f().copy(fontSize = 17.sp),
        labelMedium = labelMedium.f().copy(fontSize = 15.sp), labelSmall = labelSmall.f().copy(fontSize = 13.sp),
    )
}

private val shapes = Shapes(small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(30.dp))

private fun PetsPalette.scheme(): ColorScheme {
    val onP = if (primary.luminance() > 0.55f) Color(0xFF1A1420) else Color.White
    return if (dark) darkColorScheme(
        primary = primary, onPrimary = onP, primaryContainer = container, onPrimaryContainer = onSurface,
        secondary = secondary, onSecondary = Color.White, secondaryContainer = container, onSecondaryContainer = onSurface,
        tertiary = accent, background = bg2, onBackground = onSurface, surface = surface, onSurface = onSurface,
        surfaceVariant = container, onSurfaceVariant = muted, outline = muted.copy(alpha = 0.6f), error = Color(0xFFFF8A80),
    ) else lightColorScheme(
        primary = primary, onPrimary = onP, primaryContainer = container, onPrimaryContainer = onSurface,
        secondary = secondary, onSecondary = Color.White, secondaryContainer = container, onSecondaryContainer = onSurface,
        tertiary = accent, background = bg2, onBackground = onSurface, surface = surface, onSurface = onSurface,
        surfaceVariant = container, onSurfaceVariant = muted, outline = muted.copy(alpha = 0.6f), error = Color(0xFFB3261E),
    )
}

@Composable
fun PetsTheme(paletteId: String, content: @Composable () -> Unit) {
    val p = animatedPalette(paletteById(paletteId))
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = p.scheme(), typography = Typography().serif(), shapes = shapes, content = content)
    }
}

@Composable
fun brandGradient(): Brush = LocalPalette.current.let { Brush.horizontalGradient(listOf(it.primary, it.primary2)) }

@Composable
fun accent(): Color = LocalPalette.current.primary

/** Soft rounded card with a glassy border and a glow in the palette colour. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Surface(
        modifier = modifier.shadow(if (p.dark) 0.dp else 12.dp, RoundedCornerShape(26.dp), ambientColor = p.primary.copy(alpha = 0.3f), spotColor = p.primary.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(26.dp),
        color = p.surface.copy(alpha = if (p.dark) 0.78f else 0.9f),
        contentColor = p.onSurface,
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(p.primary.copy(alpha = 0.55f), p.secondary.copy(alpha = 0.25f), p.primary2.copy(alpha = 0.55f)))),
    ) { Column(content = content) }
}
