package com.pets.desktoppets.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pets.desktoppets.R

@Composable
fun HomeScreen(
    ui: UiState, vm: PetsViewModel,
    onShow: () -> Unit, onHide: () -> Unit, onChoose: () -> Unit, onOpenOverlaySettings: () -> Unit,
) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val canShow = (ui.status == PetStatus.Hidden || ui.status == PetStatus.StoppedUnexpectedly) && ui.selected != null
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.entrance(0, "home0"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val pulse by rememberInfiniteTransition(label = "logo").animateFloat(1f, 1.07f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
            Image(painterResource(R.drawable.brand_chibi), null,
                Modifier.size(58.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.neonBorder(29.dp, 3.dp, speedMs = 2600).padding(3.dp).clip(CircleShape).background(Color.White))
            Column {
                Text("Pets", style = shimmerTitle(38))
                Text("Your little desktop companions", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = p.muted)
            }
        }

        // Hero: the real looping video of the selected pet, in a spinning neon frame
        Box(
            Modifier.entrance(1, "home1").fillMaxWidth().drawBehind {
                drawRect(Brush.radialGradient(listOf(p.primary.copy(alpha = 0.32f), Color.Transparent), Offset(size.width / 2, size.height / 2), size.width * 0.75f))
            }.floaty(4f, 3800),
        ) {
            val c = ui.selected
            GlassCard(Modifier.fillMaxWidth().neonBorder(26.dp, 3.dp)) {
                if (c == null) Text("No pets found in this build.", Modifier.padding(32.dp))
                else Column(Modifier.padding(10.dp)) {
                    Box {
                        PetVideo(c, playing = true, poster = rememberPoster(vm.repo, c), modifier = Modifier.fillMaxWidth(), corner = 20.dp)
                        StatusPill(ui.status, Modifier.align(Alignment.TopStart).padding(10.dp))
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(c.name, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = p.onSurface)
                        if (c.characters.isNotEmpty()) Text(c.characters.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = p.muted)
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${c.durationMs / 1000}s seamless loop", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.weight(1f))
                            FilterChip(
                                selected = ui.settings.soundOn, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.setSound(!ui.settings.soundOn) },
                                label = { Text(if (ui.settings.soundOn) "🔊 Song on" else "🔇 Song off") },
                            )
                        }
                    }
                }
            }
        }

        if (!ui.settings.onboardingDone) {
            GlassCard(Modifier.fillMaxWidth().entrance(2, "home2")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Welcome to Pets ✨", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("1. Pick a pet in the Pets tab.\n2. Allow \"Display over other apps\" once.\n3. Tap Show Pet and go to your home screen.\n4. Drag it wherever you like. It only plays - tapping it does nothing.",
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { vm.finishOnboarding() }) { Text("Got it", fontWeight = FontWeight.Bold) }
                }
            }
        }

        if (!ui.canDrawOverlays) {
            GlassCard(Modifier.fillMaxWidth().entrance(2, "home2")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Overlay permission needed", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Android requires \"Display over other apps\" so your pet can float above the home screen and other apps. Pets uses it for nothing else.",
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenOverlaySettings, shape = RoundedCornerShape(16.dp)) { Text("Open permission settings") }
                }
            }
        }

        if (ui.status == PetStatus.StoppedUnexpectedly) {
            Text("Your pet should be visible but was stopped by the system. Tap Show Pet to bring it back, and see Settings › Troubleshooting if this keeps happening.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        ui.error?.let {
            Text(when (it) {
                "overlay_permission" -> "The overlay permission was revoked. Grant it again to show the pet."
                "foreground_denied" -> "Android refused to start the pet service. Open Pets and try again."
                "no_characters" -> "No pets found."
                else -> "Could not show the pet. Please try again."
            }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Row(Modifier.entrance(3, "home3"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShineButton("Show Pet", { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onShow() }, enabled = canShow, modifier = Modifier.weight(1f))
            OutlineGlowButton("Hide Pet", { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onHide() }, enabled = ui.status != PetStatus.Hidden, modifier = Modifier.weight(1f))
        }
        Box(Modifier.entrance(4, "home4").fillMaxWidth()) { OutlineGlowButton("Choose pet", onChoose, enabled = true, modifier = Modifier.fillMaxWidth()) }
        Spacer(Modifier.height(96.dp))
    }
}

/** Gradient button with a light sweep that glides across it and a breathing glow. */
@Composable
fun ShineButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val inf = rememberInfiniteTransition(label = "shine")
    val sweep by inf.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "sw")
    val glow by inf.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gl")
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.heightIn(min = 58.dp).alpha(if (enabled) 1f else 0.4f)
            .drawBehind { if (enabled) drawRoundRect(p.primary.copy(alpha = glow), cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()), size = size.copy(), topLeft = Offset(0f, 6.dp.toPx())) }
            .clip(shape).background(Brush.horizontalGradient(listOf(p.primary, p.primary2, p.accent)))
            .drawBehind {
                if (enabled) {
                    val x = size.width * sweep
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent), x - 90.dp.toPx(), x + 90.dp.toPx()))
                }
            }
            .bouncy(enabled, onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (p.primary.luminanceDark()) Color.White else Color(0xFF1A1420), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
}

private fun Color.luminanceDark() = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.62f

@Composable
fun OutlineGlowButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier.heightIn(min = 56.dp).alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(22.dp))
            .background(p.container.copy(alpha = 0.7f)).neonBorder(22.dp, 2.dp, active = enabled, speedMs = 5200).bouncy(enabled, onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = p.onSurface, fontWeight = FontWeight.Bold, fontSize = 19.sp) }
}

@Composable
private fun StatusPill(status: PetStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        PetStatus.Active -> "On screen" to Color(0xFF38D27C)
        PetStatus.Starting -> "Starting…" to Color(0xFF6C8CFF)
        PetStatus.Hidden -> "Hidden" to Color(0xFFB0A8C0)
        PetStatus.StoppedUnexpectedly -> "Stopped" to Color(0xFFFF5A4D)
    }
    val c by animateColorAsState(color, label = "status")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.4f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "p")
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.58f)).padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).alpha(if (status == PetStatus.Active) pulse else 1f).clip(CircleShape).background(c))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
