package com.pets.desktoppets.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Palette taken from the launcher icon: auburn hair, sapphire eyes, navy blazer, sky vest, lime skirt.
val Hair = Color(0xFFD9532F)
val Eyes = Color(0xFF3F5BD6)
val Vest = Color(0xFF7CC4E8)
val Skirt = Color(0xFF8CCB4E)
val Navy = Color(0xFF2A2840)
val Cream = Color(0xFFFFF8F3)
val Peach = Color(0xFFFFE6DA)

private val scheme = lightColorScheme(
    primary = Hair, onPrimary = Color.White,
    primaryContainer = Peach, onPrimaryContainer = Color(0xFF5A1F0F),
    secondary = Eyes, onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE4FF), onSecondaryContainer = Color(0xFF0F1D5A),
    tertiary = Skirt, background = Cream, onBackground = Navy,
    surface = Cream, onSurface = Navy, surfaceVariant = Color(0xFFFFEFE7), onSurfaceVariant = Color(0xFF5B5568),
    error = Color(0xFFB3261E),
)

private val darkScheme = darkColorScheme(
    primary = Color(0xFFFF8A68), onPrimary = Color(0xFF4A1500),
    primaryContainer = Color(0xFF5A2A1C), onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFB7C4FF), onSecondary = Color(0xFF0F1D5A),
    secondaryContainer = Color(0xFF2C3A7A), onSecondaryContainer = Color(0xFFDDE4FF),
    tertiary = Skirt, background = Color(0xFF17141F), onBackground = Color(0xFFEDE6F2),
    surface = Color(0xFF17141F), onSurface = Color(0xFFEDE6F2),
    surfaceVariant = Color(0xFF2A2433), onSurfaceVariant = Color(0xFFCBC3D4),
    error = Color(0xFFFFB4AB),
)

/** Backdrop behind transparent pet previews. */
@Composable
fun previewBrush(): Brush = if (isSystemInDarkTheme()) Brush.verticalGradient(listOf(Color(0xFF3A2A3F), Color(0xFF26304F)))
else Brush.verticalGradient(listOf(Peach, Color(0xFFE7EEFF)))

/** Accent text colour that stays readable in both themes. */
@Composable
fun accent(): Color = MaterialTheme.colorScheme.primary

private val shapes = Shapes(
    small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(30.dp),
)

@Composable
fun PetsTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkScheme else scheme, shapes = shapes, content = content)
