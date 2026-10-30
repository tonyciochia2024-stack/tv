package com.pets.desktoppets.overlay

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pets.desktoppets.MainActivity
import com.pets.desktoppets.R
import com.pets.desktoppets.data.PetPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores the pet after a reboot (or app update) when the user wants it and the overlay permission is
 * still granted. If Android (or an OEM battery manager) refuses the background start, a notification lets
 * the user resume with one tap instead of failing silently.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = PetPrefs(app).current()
                if (s.wantActive && s.restoreOnBoot && PetOverlayService.canDrawOverlays(app)) {
                    try {
                        PetOverlayService.start(app)
                    } catch (e: Exception) {
                        notifyResume(app)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun notifyResume(ctx: Context) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("pet_restore", ctx.getString(R.string.notif_channel_restore), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_RESUME_PET, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(2, NotificationCompat.Builder(ctx, "pet_restore").setSmallIcon(R.drawable.ic_stat_pet)
            .setContentTitle(ctx.getString(R.string.notif_restore_title)).setContentText(ctx.getString(R.string.notif_restore_text))
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
