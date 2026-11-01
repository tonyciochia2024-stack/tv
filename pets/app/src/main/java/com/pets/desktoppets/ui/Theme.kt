package com.pets.desktoppets.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palette from the launcher icon: auburn hair, sapphire eyes, navy blazer, sky vest, lime skirt.
val Hair = Color(0xFFE2512F)
val HairLight = Color(0xFFFF8A5B)
val Eyes = Color(0xFF4B63E0)
val Navy = Color(0xFF2A2840)
val Cream = Color(0xFFFFF6F0)
val Peach = Color(0xFFFFE3D6)
val Sky = Color(0xFF7CC4E8)
val Mint = Color(0xFF55C28A)

private val light = lightColorScheme(
    primary = Hair, onPrimary = Color.White, primaryContainer = Peach, onPrimaryContainer = Color(0xFF5A1F0F),
    secondary = Eyes, onSecondary = Color.White, secondaryContainer = Color(0xFFDDE4FF), onSecondaryContainer = Color(0xFF0F1D5A),
    tertiary = Mint, background = Cream, onBackground = Navy, surface = Color.White, onSurface = Navy,
    surfaceVariant = Color(0xFFFFEDE4), onSurfaceVariant = Color(0xFF5B5568), error = Color(0xFFB3261E),
)
private val dark = darkColorScheme(
    primary = Color(0xFFFF8A68), onPrimary = Color(0xFF4A1500), primaryContainer = Color(0xFF5A2A1C), onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFB7C4FF), onSecondary = Color(0xFF0F1D5A), secondaryContainer = Color(0xFF2C3A7A), onSecondaryContainer = Color(0xFFDDE4FF),
    tertiary = Mint, background = Color(0xFF14101C), onBackground = Color(0xFFEDE6F2), surface = Color(0xFF1F1A2A), onSurface = Color(0xFFEDE6F2),
    surfaceVariant = Color(0xFF2A2433), onSurfaceVariant = Color(0xFFCBC3D4), error = Color(0xFFFFB4AB),
)
private val shapes = Shapes(small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(30.dp))

@Composable
fun PetsTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, shapes = shapes, content = content)

/** Whole-app backdrop. */
@Composable
fun appBackground(): Brush = if (isSystemInDarkTheme())
    Brush.verticalGradient(listOf(Color(0xFF241A33), Color(0xFF14101C), Color(0xFF111A2E)))
else Brush.verticalGradient(listOf(Color(0xFFFFE9DD), Color(0xFFFFF6F0), Color(0xFFE9EEFF)))

val BrandGradient = Brush.horizontalGradient(listOf(Hair, HairLight))

@Composable
fun brandTitle(size: Int = 32) = TextStyle(brush = BrandGradient, fontSize = size.sp, fontWeight = FontWeight.ExtraBold)

@Composable
fun accent(): Color = MaterialTheme.colorScheme.primary

/** Soft rounded card with a glassy border. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    Surface(
        modifier = modifier.shadow(if (dark) 0.dp else 10.dp, RoundedCornerShape(26.dp), ambientColor = Hair.copy(alpha = 0.25f), spotColor = Hair.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.85f else 0.92f),
        border = BorderStroke(1.dp, if (dark) Color.White.copy(alpha = 0.08f) else Color.White),
    ) { Column(content = content) }
}
