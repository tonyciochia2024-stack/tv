package com.pets.desktoppets.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import com.pets.desktoppets.R

@Composable
fun HomeScreen(
    ui: UiState, vm: PetsViewModel,
    onShow: () -> Unit, onHide: () -> Unit, onChoose: () -> Unit, onOpenOverlaySettings: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.brand_chibi), null, Modifier.size(48.dp).clip(CircleShape))
            Column {
                Text("Pets", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Hair)
                Text("Your little desktop companion", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Preview of the selected character
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Peach, Color(0xFFE7EEFF)))).padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                val c = ui.selected
                if (c == null) Text("No characters found in this build.", Modifier.padding(40.dp))
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PetAnimation(c, playing = true, fallback = vm.repo.thumb(c), modifier = Modifier.height(210.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(c.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }

        StatusChip(ui.status)

        if (!ui.canDrawOverlays) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Overlay permission needed", fontWeight = FontWeight.Bold)
                    Text("Android requires \"Display over other apps\" so your pet can float above the home screen and other apps. Pets uses it for nothing else.",
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenOverlaySettings, shape = RoundedCornerShape(16.dp)) { Text("Open permission settings") }
                }
            }
        } else {
            Text("✓ Overlay permission granted", color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyMedium)
        }

        if (ui.status == PetStatus.StoppedUnexpectedly) {
            Text("Your pet should be visible but was stopped by the system. Tap Show Pet to bring it back, and see Settings › Troubleshooting if this keeps happening.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        ui.error?.let {
            Text(when (it) {
                "overlay_permission" -> "The overlay permission was revoked. Grant it again to show the pet."
                "foreground_denied" -> "Android refused to start the pet service. Open Pets and try again."
                "no_characters" -> "No character assets found."
                else -> "Could not show the pet. Please try again."
            }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PressButton("Show Pet", onShow, enabled = (ui.status == PetStatus.Hidden || ui.status == PetStatus.StoppedUnexpectedly) && ui.selected != null, primary = true, modifier = Modifier.weight(1f))
            PressButton("Hide Pet", onHide, enabled = ui.status != PetStatus.Hidden, primary = false, modifier = Modifier.weight(1f))
        }
        OutlinedButton(onClick = onChoose, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp)) { Text("Choose character") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PressButton(text: String, onClick: () -> Unit, enabled: Boolean, primary: Boolean, modifier: Modifier) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f), label = "press")
    val m = modifier.heightIn(min = 56.dp).scale(s)
    if (primary) Button(onClick, m, enabled, interactionSource = src, shape = RoundedCornerShape(20.dp)) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    else FilledTonalButton(onClick, m, enabled, interactionSource = src, shape = RoundedCornerShape(20.dp)) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
private fun StatusChip(status: PetStatus) {
    val (label, color) = when (status) {
        PetStatus.Active -> "Pet is active" to Color(0xFF2E7D32)
        PetStatus.Starting -> "Starting…" to Eyes
        PetStatus.Hidden -> "Pet is hidden" to Color(0xFF6B6577)
        PetStatus.StoppedUnexpectedly -> "Pet stopped" to Color(0xFFB3261E)
    }
    val c by animateColorAsState(color, label = "status")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Text(label, color = c, fontWeight = FontWeight.SemiBold)
    }
}
