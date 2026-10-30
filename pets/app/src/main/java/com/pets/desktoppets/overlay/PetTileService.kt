package com.pets.desktoppets.overlay

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.pets.desktoppets.MainActivity
import com.pets.desktoppets.R
import com.pets.desktoppets.data.PetPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Quick Settings tile: show / hide the pet without opening the app. Same rules as the in-app buttons. */
class PetTileService : TileService() {
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        scope = CoroutineScope(Dispatchers.Main).also { sc ->
            job = sc.launch {
                combine(PetState.serviceRunning, PetState.overlayAttached) { r, a -> r || a }.collect { render(it) }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel(); scope = null; job = null
        super.onStopListening()
    }

    private fun render(on: Boolean) {
        val t = qsTile ?: return
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = getString(R.string.tile_label)
        t.icon = Icon.createWithResource(this, R.drawable.ic_stat_pet)
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = getString(if (on) R.string.tile_on else R.string.tile_off)
        t.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = PetPrefs(this)
        if (PetState.serviceRunning.value) {
            CoroutineScope(Dispatchers.Main).launch { prefs.setWantActive(false); PetOverlayService.stop(this@PetTileService) }
            return
        }
        if (!PetOverlayService.canDrawOverlays(this)) { openApp(); return }   // permission screen lives in the app
        CoroutineScope(Dispatchers.Main).launch {
            prefs.setWantActive(true)
            runCatching { PetOverlayService.start(this@PetTileService) }.onFailure { openApp() }
        }
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")   // Intent overload only used below API 34
    @Suppress("DEPRECATION")
    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 3, i, PendingIntent.FLAG_IMMUTABLE))
        } else startActivityAndCollapse(i)
    }
}
