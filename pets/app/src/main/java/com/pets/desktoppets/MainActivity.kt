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
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
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
        setContent { PetsTheme { PetsApp(vm) } }
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

private enum class Tab(val label: String) { Home("Home"), Characters("Characters"), Settings("Settings") }

@Composable
private fun PetsApp(vm: PetsViewModel) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshSystemState() }

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

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t }, label = { Text(t.label) },
                        icon = { Icon(when (t) { Tab.Home -> Icons.Default.Home; Tab.Characters -> Icons.Default.Favorite; Tab.Settings -> Icons.Default.Settings }, null) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Crossfade(tab, label = "tab") { t ->
                when (t) {
                    Tab.Home -> HomeScreen(ui, vm, onShow, { vm.hidePet() }, { tab = Tab.Characters }, openOverlaySettings)
                    Tab.Characters -> CharactersScreen(ui, vm) { tab = Tab.Home }
                    Tab.Settings -> SettingsScreen(ui, vm, openOverlaySettings)
                }
            }
        }
    }
}
