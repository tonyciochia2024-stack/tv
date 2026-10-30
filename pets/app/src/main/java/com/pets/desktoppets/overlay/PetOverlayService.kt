package com.pets.desktoppets.overlay

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.pets.desktoppets.MainActivity
import com.pets.desktoppets.R
import com.pets.desktoppets.data.CharacterInfo
import com.pets.desktoppets.data.CharacterRepository
import com.pets.desktoppets.data.PetPrefs
import com.pets.desktoppets.data.PetSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Foreground service that owns the overlay window. Starting = pet visible, stopping = pet gone. */
class PetOverlayService : LifecycleService() {
    companion object {
        const val ACTION_START = "com.pets.desktoppets.START"
        const val ACTION_STOP = "com.pets.desktoppets.STOP"
        private const val CHANNEL = "pet_overlay"
        private const val NOTIF_ID = 1

        fun canDrawOverlays(context: Context) = Settings.canDrawOverlays(context)

        /** Throws if Android refuses a background foreground-service start. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PetOverlayService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PetOverlayService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: PetPrefs
    private lateinit var repo: CharacterRepository
    private var view: PetView? = null
    private var params: WindowManager.LayoutParams? = null
    private var player: FramePlayer? = null
    private var settingsJob: Job? = null
    private val appOps by lazy { getSystemService(AppOpsManager::class.java) }
    private val opListener = AppOpsManager.OnOpChangedListener { _, _ ->
        // The user revoked "Display over other apps" while the pet was shown: Android removes the window
        // silently, so stop the service instead of pretending the pet is still on screen.
        Handler(Looper.getMainLooper()).post {
            if (!canDrawOverlays(this)) { PetState.setError("overlay_permission"); stopSelf() }
        }
    }

    private var current: CharacterInfo? = null
    private var sizeDp = -1
    private var lastFx = -1f
    private var lastFy = -1f
    private var dragStartX = 0
    private var dragStartY = 0
    private var isDragging = false
    private var curX = 0
    private var curY = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> player?.stop()          // no rendering while the screen is off
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> { player?.start(); relayout() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = PetPrefs(this)
        repo = CharacterRepository(this)
        PetState.setServiceRunning(true)
        PetState.setError(null)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!promoteToForeground()) return START_NOT_STICKY

        if (intent?.action == ACTION_STOP) {
            lifecycleScope.launch { prefs.setWantActive(false); stopSelf() }
            return START_NOT_STICKY
        }
        if (!canDrawOverlays(this)) {
            PetState.setError("overlay_permission")
            stopSelf()
            return START_NOT_STICKY
        }
        if (view == null) {
            // intent == null means Android restarted us after killing the process: only continue if the user still wants the pet.
            lifecycleScope.launch {
                if (intent == null && !prefs.current().wantActive) { stopSelf(); return@launch }
                attach()
            }
        }
        return START_STICKY
    }

    private fun promoteToForeground(): Boolean {
        val n = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                ServiceCompat.startForeground(this, NOTIF_ID, n, type)
            } else startForeground(NOTIF_ID, n)
            return true
        } catch (e: Exception) {
            PetState.setError("foreground_denied")
            stopSelf()
            return false
        }
    }

    private fun attach() {
        if (view != null) return
        val v = PetView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; title = "PetOverlay" }
        v.dragListener = dragListener
        val p = FramePlayer(this) { bmp -> v.show(bmp) }
        view = v; params = lp; player = p

        // Follow the preferences: character / size changes are applied live, no restart needed.
        settingsJob = lifecycleScope.launch { prefs.flow.collect { apply(it) } }
        appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, opListener)
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        })
    }

    private var added = false

    private fun apply(s: PetSettings) {
        val v = view ?: return
        val lp = params ?: return
        val info = repo.resolve(s.selectedId)
        if (info == null) { PetState.setError("no_characters"); stopSelf(); return }
        val characterChanged = info.id != current?.id
        if (characterChanged) { current = info; player?.setCharacter(info) }
        if (characterChanged || s.sizeDp != sizeDp) {
            sizeDp = s.sizeDp
            val h = (sizeDp * resources.displayMetrics.density).roundToInt()
            lp.height = h
            lp.width = (h * info.aspect).roundToInt()
        }
        val moved = lastFx < 0 || abs(s.fx - lastFx) > 0.002f || abs(s.fy - lastFy) > 0.002f
        if (moved && !isDragging) { lastFx = s.fx; lastFy = s.fy }   // e.g. "Reset position" in Settings
        placeFromFractions(lastFx.coerceAtLeast(0f), lastFy.coerceAtLeast(0f))
        if (!added) {
            try {
                wm.addView(v, lp)
                added = true
                PetState.setOverlayAttached(true)
                player?.start()
            } catch (e: Exception) {   // e.g. permission revoked between check and add
                PetState.setError("overlay_failed")
                stopSelf()
            }
        } else if (!isDragging) {
            runCatching { wm.updateViewLayout(v, lp) }
        }
    }

    private fun placeFromFractions(fx: Float, fy: Float) {
        val lp = params ?: return
        val (x, y) = fractionToXY(usableBounds(this, wm), lp.width, lp.height, fx, fy)
        lp.x = x; lp.y = y; curX = x; curY = y
    }

    /** Re-clamp after rotation, resolution change or unlock. */
    private fun relayout() {
        val v = view ?: return
        val lp = params ?: return
        if (!added || isDragging) return
        placeFromFractions(lastFx.coerceAtLeast(0f), lastFy.coerceAtLeast(0f))
        runCatching { wm.updateViewLayout(v, lp) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val info = current
        if (info != null && params != null) {   // density may change too
            val h = (sizeDp * resources.displayMetrics.density).roundToInt()
            params!!.height = h; params!!.width = (h * info.aspect).roundToInt()
        }
        view?.post { relayout() }
    }

    private val dragListener = object : PetView.DragListener {
        override fun onDragStart() { isDragging = true; dragStartX = curX; dragStartY = curY }
        override fun onDrag(dx: Int, dy: Int) {
            val lp = params ?: return
            val b = usableBounds(this@PetOverlayService, wm)
            curX = (dragStartX + dx).coerceIn(b.left, (b.right - lp.width).coerceAtLeast(b.left))
            curY = (dragStartY + dy).coerceIn(b.top, (b.bottom - lp.height).coerceAtLeast(b.top))
            lp.x = curX; lp.y = curY
            view?.let { runCatching { wm.updateViewLayout(it, lp) } }
        }
        override fun onDragEnd() {
            isDragging = false
            val lp = params ?: return
            val (fx, fy) = xyToFraction(usableBounds(this@PetOverlayService, wm), lp.width, lp.height, curX, curY)
            lastFx = fx; lastFy = fy
            lifecycleScope.launch { prefs.setPosition(fx, fy) }
        }
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        runCatching { appOps.stopWatchingMode(opListener) }
        runCatching { unregisterReceiver(screenReceiver) }
        player?.release(); player = null
        view?.let { v -> if (added) runCatching { wm.removeView(v) } }
        view = null; added = false
        PetState.setOverlayAttached(false)
        PetState.setServiceRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? { super.onBind(intent); return null }

    // ---- notification -------------------------------------------------------------------------
    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notif_channel_pet), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.notif_channel_pet_desc); setShowBadge(false)
        })
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val hide = PendingIntent.getService(this, 1, Intent(this, PetOverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_hide), hide)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
