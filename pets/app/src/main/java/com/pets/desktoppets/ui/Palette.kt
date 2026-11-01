package com.pets.desktoppets.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** One complete look for the app: backdrop, accents, cards, text. The user picks one in Settings > Menu colors. */
data class PetsPalette(
    val id: String,
    val name: String,
    val dark: Boolean,
    val bg1: Color, val bg2: Color, val bg3: Color,
    val primary: Color, val primary2: Color,
    val secondary: Color, val accent: Color,
    val surface: Color, val container: Color,
    val onSurface: Color, val muted: Color,
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

val Palettes: List<PetsPalette> = listOf(
    PetsPalette("sakura", "Sakura", false, c(0xFFD6E4), c(0xFFF4F0), c(0xE8E2FF),
        c(0xE8457A), c(0xFF8FB1), c(0x7A5CFF), c(0xFFB703), c(0xFFFFFF), c(0xFFE9F0), c(0x3A2233), c(0x7A5C6B)),
    PetsPalette("ocean", "Ocean", false, c(0xCDEFFF), c(0xEEFAFF), c(0xD9E4FF),
        c(0x0A74E0), c(0x19C3FF), c(0x00B894), c(0xFFB84D), c(0xFFFFFF), c(0xE3F3FF), c(0x0F2A44), c(0x547089)),
    PetsPalette("sunset", "Sunset", false, c(0xFFD2B0), c(0xFFF0DE), c(0xFFC4D6),
        c(0xFF5A36), c(0xFFB02E), c(0xC2185B), c(0x7B2CBF), c(0xFFFFFF), c(0xFFE7D6), c(0x40200F), c(0x84553A)),
    PetsPalette("mint", "Mint", false, c(0xCDF7E3), c(0xF0FFF8), c(0xD7F1FF),
        c(0x00A06C), c(0x44E3AD), c(0x0091D5), c(0xF4A261), c(0xFFFFFF), c(0xDDF9EC), c(0x0E3328), c(0x4F7F6E)),
    PetsPalette("lavender", "Lavender", false, c(0xE2D6FF), c(0xF6F1FF), c(0xFFDFF3),
        c(0x7440F5), c(0xB388FF), c(0xFF3D8B), c(0x00C8E0), c(0xFFFFFF), c(0xEFE6FF), c(0x2A1B4D), c(0x6D5C91)),
    PetsPalette("midnight", "Midnight", true, c(0x0A0F2A), c(0x161A48), c(0x2A0F46),
        c(0x8C9BFF), c(0xC2A8FF), c(0x4DE1FF), c(0xFF7AD9), c(0x1B2046), c(0x262C5E), c(0xEEF0FF), c(0xA8AEDB)),
    PetsPalette("neon", "Neon", true, c(0x06010F), c(0x1C0036), c(0x00212F),
        c(0xFF2EC4), c(0x8A63FF), c(0x00F5D4), c(0xFFEE32), c(0x160A26), c(0x24123D), c(0xFFF0FF), c(0xC6A8DD)),
    PetsPalette("ember", "Ember", true, c(0x1C0505), c(0x3D0B0B), c(0x2B1300),
        c(0xFF5A36), c(0xFFB300), c(0xFF3D71), c(0xFFD54F), c(0x2B1010), c(0x3D1A14), c(0xFFEFE6), c(0xD9A998)),
    PetsPalette("forest", "Forest", true, c(0x06231A), c(0x0F3C2C), c(0x142A22),
        c(0x3DDC97), c(0xB6F36B), c(0x00B3A4), c(0xFFC857), c(0x12302A), c(0x1B4538), c(0xE8FFF4), c(0x9ACDB8)),
    PetsPalette("royal", "Royal Gold", true, c(0x1C1530), c(0x32224F), c(0x1F1A12),
        c(0xFFC947), c(0xFF9F1C), c(0xC77DFF), c(0x4CC9F0), c(0x2A2142), c(0x3A2D5C), c(0xFFF6E0), c(0xCBBBE0)),
)

fun paletteById(id: String?): PetsPalette = Palettes.firstOrNull { it.id == id } ?: Palettes.first()

val LocalPalette = staticCompositionLocalOf { Palettes.first() }

/** Cross-fades every colour of [t] so changing the palette in Settings morphs the whole app. */
@Composable
fun animatedPalette(t: PetsPalette): PetsPalette {
    @Composable fun a(x: Color) = animateColorAsState(x, tween(650), label = "pal").value
    return t.copy(
        bg1 = a(t.bg1), bg2 = a(t.bg2), bg3 = a(t.bg3), primary = a(t.primary), primary2 = a(t.primary2),
        secondary = a(t.secondary), accent = a(t.accent), surface = a(t.surface), container = a(t.container),
        onSurface = a(t.onSurface), muted = a(t.muted),
    )
}
