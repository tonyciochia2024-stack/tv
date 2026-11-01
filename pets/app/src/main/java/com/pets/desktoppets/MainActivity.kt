package com.pets.desktoppets

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pets.desktoppets.overlay.PetState
import com.pets.desktoppets.ui.*

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_RESUME_PET = "resume_pet" }

    private val vm: PetsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(EXTRA_RESUME_PET, false) == true) vm.showPet()   // tapped the "bring your pet back" notification
        setContent { PetsApp(vm) }
    }

    override fun onResume() {
        super.onResume()
        PetState.setAppInForeground(true)
    }

    override fun onPause() {
        PetState.setAppInForeground(false)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_RESUME_PET, false)) vm.showPet()
    }
}

private enum class Tab(val label: String) { Home("Home"), Characters("Pets"), Settings("Settings") }

@Composable
private fun PetsApp(vm: PetsViewModel) {
    val ui by vm.state.collectAsStateWithLifecycle()
    PetsTheme(ui.settings.paletteId) { PetsShell(vm, ui) }
}

@Composable
private fun PetsShell(vm: PetsViewModel, ui: UiState) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val confetti = remember { Confetti() }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshSystemState() }
    val view = LocalView.current
    SideEffect {   // status / navigation bar icons readable on light and dark palettes
        val w = (ctx as android.app.Activity).window
        WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !p.dark; isAppearanceLightNavigationBars = !p.dark }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.showPet() }
    val onShow: () -> Unit = {
        if (!ui.canDrawOverlays) {
            tab = Tab.Settings
        } else if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)   // optional: the pet starts either way
        } else vm.showPet()
    }
    val openOverlaySettings: () -> Unit = {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
    }

    CompositionLocalProvider(LocalConfetti provides confetti) {
        Box(Modifier.fillMaxSize()) {
            AnimatedBackdrop()
            AnimatedContent(
                targetState = tab, label = "tab",
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(spring(0.8f, 300f)) { dir * it / 3 } + fadeIn(tween(350)) + scaleIn(tween(350), 0.92f)) togetherWith
                        (slideOutHorizontally(tween(300)) { -dir * it / 4 } + fadeOut(tween(200)) + scaleOut(tween(300), 0.92f))
                },
            ) { t ->
                when (t) {
                    Tab.Home -> HomeScreen(ui, vm, onShow, { vm.hidePet() }, { tab = Tab.Characters }, openOverlaySettings)
                    Tab.Characters -> CharactersScreen(ui, vm) { tab = Tab.Home }
                    Tab.Settings -> SettingsScreen(ui, vm, openOverlaySettings)
                }
            }
            ConfettiLayer(confetti)
            // floating pill navigation with a glowing, bouncing selection
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp).neonBorder(32.dp, 2.dp, speedMs = 6000),
                shape = RoundedCornerShape(32.dp), shadowElevation = 16.dp, color = p.surface.copy(alpha = 0.94f),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tab.entries.forEach { t ->
                        val sel = tab == t
                        val bg by animateColorAsState(if (sel) p.primary.copy(alpha = 0.2f) else Color.Transparent, tween(300), label = "tabbg")
                        val sc by animateFloatAsState(if (sel) 1.2f else 1f, spring(0.3f, 450f), label = "tabsc")
                        Row(
                            Modifier.clip(RoundedCornerShape(26.dp)).background(bg)
                                .clickable(onClickLabel = t.label) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); tab = t }
                                .heightIn(min = 50.dp).padding(horizontal = 16.dp).animateContentSize(spring(0.7f, 400f)),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Icon(when (t) { Tab.Home -> Icons.Default.Home; Tab.Characters -> Icons.Default.Favorite; Tab.Settings -> Icons.Default.Settings }, t.label,
                                Modifier.graphicsLayer { scaleX = sc; scaleY = sc }, tint = if (sel) p.primary else p.muted)
                            if (sel) Text(t.label, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = p.primary)
                        }
                    }
                }
            }
        }
    }
}
