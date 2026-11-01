package com.pets.desktoppets.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import com.pets.desktoppets.BuildConfig
import com.pets.desktoppets.data.DEFAULT_SIZE_DP
import com.pets.desktoppets.data.MAX_SIZE_DP
import com.pets.desktoppets.data.MIN_OPACITY
import com.pets.desktoppets.data.MIN_SIZE_DP
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(ui: UiState, vm: PetsViewModel, onOpenOverlaySettings: () -> Unit) {
    sectionIndex = 0
    val ctx = LocalContext.current
    var size by remember(ui.settings.sizeDp) { mutableFloatStateOf(ui.settings.sizeDp.toFloat()) }
    var volume by remember(ui.settings.volume) { mutableFloatStateOf(ui.settings.volume.toFloat()) }
    var opacity by remember(ui.settings.opacity) { mutableFloatStateOf(ui.settings.opacity.toFloat()) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = shimmerTitle(36), modifier = Modifier.entrance(0, "settingsTitle"))

        Section("Menu colors") {
            Text("Pick a look for the whole app. It changes live.", style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
            PalettePicker(ui.settings.paletteId) { vm.setPalette(it) }
        }

        Section("Pet size") {
            Text("${size.roundToInt()} dp tall (default $DEFAULT_SIZE_DP dp)", fontWeight = FontWeight.SemiBold)
            Slider(
                value = size, onValueChange = { size = it; vm.setSize(it.roundToInt()) },   // the floating pet resizes live while dragging
                valueRange = MIN_SIZE_DP.toFloat()..MAX_SIZE_DP.toFloat(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ size = DEFAULT_SIZE_DP.toFloat(); vm.setSize(DEFAULT_SIZE_DP) }, shape = RoundedCornerShape(14.dp)) { Text("Default size") }
                OutlinedButton({ vm.resetPosition() }, shape = RoundedCornerShape(14.dp)) { Text("Reset position") }
            }
            Text("Drag the pet to move it (while Pets is open the pet lets touches through, so it never covers these controls). Tapping it does nothing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section("Song") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Play the song", fontWeight = FontWeight.SemiBold)
                    Text("Each pet video carries its song. Turn it off for a silent pet; the video keeps playing.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(ui.settings.soundOn, { vm.setSound(it) })
            }
            if (ui.settings.soundOn) {
                Text("Volume ${volume.roundToInt()} %", fontWeight = FontWeight.SemiBold)
                Slider(value = volume, onValueChange = { volume = it; vm.setVolume(it.roundToInt()) }, valueRange = 0f..100f)
            }
        }

        Section("Look") {
            Text("Opacity ${opacity.roundToInt()} %", fontWeight = FontWeight.SemiBold)
            Slider(
                value = opacity, onValueChange = { opacity = it; vm.setOpacity(it.roundToInt()) },
                valueRange = MIN_OPACITY.toFloat()..100f,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Surprise me", fontWeight = FontWeight.SemiBold)
                    Text("Pick a different random pet every time you tap Show Pet.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(ui.settings.shuffle, { vm.setShuffle(it) })
            }
            Text("Tip: the Quick Settings tile \"Desktop pet\" shows or hides the pet from the notification shade.", style = MaterialTheme.typography.bodySmall)
        }

        Section("Restore after reboot") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bring my pet back automatically", fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            !ui.settings.restoreOnBoot -> "Off: start the pet manually after a restart."
                            !ui.canDrawOverlays -> "On, but the overlay permission is missing, so restore cannot work yet."
                            !ui.settings.wantActive -> "On. Nothing to restore right now because the pet is hidden."
                            else -> "On. The pet will be restored after restart, if your phone allows it."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(ui.settings.restoreOnBoot, { vm.setRestoreOnBoot(it) })
            }
            Text("A pet you hide yourself is never restarted automatically.", style = MaterialTheme.typography.bodySmall)
        }

        Section("Overlay permission") {
            Text(if (ui.canDrawOverlays) "✓ Granted" else "✗ Not granted", fontWeight = FontWeight.SemiBold,
                color = if (ui.canDrawOverlays) androidx.compose.ui.graphics.Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
            Text("Pets needs \"Display over other apps\" to draw the pet above your home screen and other apps. It cannot read your screen or touches outside the pet. If you revoke it the pet disappears and the rest of the app keeps working.",
                style = MaterialTheme.typography.bodyMedium)
            Button(onOpenOverlaySettings, shape = RoundedCornerShape(14.dp)) { Text(if (ui.canDrawOverlays) "Review permission" else "Grant permission") }
        }

        Section("Troubleshooting") {
            Text(if (ui.ignoringBatteryOpt) "✓ Battery optimisation is off for Pets." else "Battery optimisation is on for Pets (the system may stop the pet or block restore).",
                style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, shape = RoundedCornerShape(14.dp)) { Text("Battery settings") }
                OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }, shape = RoundedCornerShape(14.dp)) { Text("App info") }
            }
            Text(
                "If the pet does not come back after a restart:\n" +
                    "• Allow Pets to \"Run in background\" / set battery to Unrestricted.\n" +
                    "• Xiaomi/Redmi/POCO: Security › Autostart › enable Pets. Huawei/Honor: App launch › Manage manually.\n" +
                    "• OPPO/Realme/OnePlus/Vivo: allow auto-launch / background activity. Samsung: remove Pets from \"Sleeping apps\".\n" +
                    "• Allow notifications for Pets (Android 13+); the pet runs as a visible foreground service.\n" +
                    "• You can always tap Show Pet on the Home tab to start it manually.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("About") {
            Text("Pets ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", fontWeight = FontWeight.SemiBold)
            Text("${ui.characters.size} pets, all bundled offline. No ads, no analytics, no internet access.", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(88.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth().entrance(sectionIndex++, title)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

private var sectionIndex = 0

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PalettePicker(selectedId: String, onPick: (String) -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Palettes.forEach { pal ->
            val sel = pal.id == selectedId
            val pop by androidx.compose.animation.core.animateFloatAsState(if (sel) 1.12f else 1f, androidx.compose.animation.core.spring(0.35f, 400f), label = "pop")
            Column(
                Modifier.width(62.dp).bouncy { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onPick(pal.id) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    Modifier.size(54.dp).graphicsLayer { scaleX = pop; scaleY = pop }.neonBorder(27.dp, 3.dp, active = sel, speedMs = 2200).padding(4.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(pal.bg1, pal.primary, pal.secondary, pal.accent))),
                    contentAlignment = Alignment.Center,
                ) { if (sel) Text("✓", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }
                Text(pal.name, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, maxLines = 1, color = LocalPalette.current.onSurface)
            }
        }
    }
}
