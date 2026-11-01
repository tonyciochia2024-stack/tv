package com.pets.desktoppets.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pets.desktoppets.R

@Composable
fun HomeScreen(
    ui: UiState, vm: PetsViewModel,
    onShow: () -> Unit, onHide: () -> Unit, onChoose: () -> Unit, onOpenOverlaySettings: () -> Unit,
) {
    val canShow = (ui.status == PetStatus.Hidden || ui.status == PetStatus.StoppedUnexpectedly) && ui.selected != null
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.brand_chibi), null, Modifier.size(52.dp).clip(CircleShape).background(Color.White))
            Column {
                Text("Pets", style = brandTitle(32))
                Text("Your little desktop companion", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Hero: the real looping video of the selected pet
        GlassCard(Modifier.fillMaxWidth()) {
            val c = ui.selected
            if (c == null) Text("No pets found in this build.", Modifier.padding(32.dp))
            else Column(Modifier.padding(10.dp)) {
                Box {
                    PetVideo(c, playing = true, poster = vm.repo.poster(c), modifier = Modifier.fillMaxWidth(), corner = 20.dp)
                    StatusPill(ui.status, Modifier.align(Alignment.TopStart).padding(10.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("${c.durationMs / 1000}s seamless loop" + if (c.hasAudio) " · with song" else "", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilterChip(
                        selected = ui.settings.soundOn, onClick = { vm.setSound(!ui.settings.soundOn) },
                        label = { Text(if (ui.settings.soundOn) "🔊 Song on" else "🔇 Song off") },
                    )
                }
            }
        }

        if (!ui.settings.onboardingDone) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Welcome to Pets ✨", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("1. Pick a pet in the Pets tab.\n2. Allow \"Display over other apps\" once.\n3. Tap Show Pet and go to your home screen.\n4. Drag it wherever you like. It only plays - tapping it does nothing.",
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { vm.finishOnboarding() }) { Text("Got it") }
                }
            }
        }

        if (!ui.canDrawOverlays) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Overlay permission needed", fontWeight = FontWeight.Bold)
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

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            GradientButton("Show Pet", onShow, enabled = canShow, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = onHide, enabled = ui.status != PetStatus.Hidden, modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = RoundedCornerShape(20.dp)) { Text("Hide Pet", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
        OutlinedButton(onClick = onChoose, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp)) { Text("Choose pet") }
        Spacer(Modifier.height(88.dp))
    }
}

@Composable
private fun GradientButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f), label = "press")
    Box(
        modifier.heightIn(min = 56.dp).scale(s).alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(20.dp)).background(BrandGradient)
            .clickable(enabled = enabled, interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
}

@Composable
private fun StatusPill(status: PetStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        PetStatus.Active -> "On screen" to Color(0xFF2E9E5B)
        PetStatus.Starting -> "Starting…" to Eyes
        PetStatus.Hidden -> "Hidden" to Color(0xFF6B6577)
        PetStatus.StoppedUnexpectedly -> "Stopped" to Color(0xFFD33A2C)
    }
    val c by animateColorAsState(color, label = "status")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(9.dp).alpha(if (status == PetStatus.Active) pulse else 1f).clip(CircleShape).background(c))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
