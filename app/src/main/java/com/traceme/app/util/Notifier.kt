package com.traceme.app.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.traceme.app.CheckInActivity
import com.traceme.app.R

object Notifier {

    const val CH_WATCH = "traceme.watch"
    const val CH_CHECKIN = "traceme.checkin"
    const val CH_SOS = "traceme.sos"

    const val ID_WATCH = 1001
    const val ID_CHECKIN = 1002
    const val ID_SOS = 1003

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(CH_WATCH, "Night watch", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent status while Overwatch watches your location."
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CH_CHECKIN, "Check-in alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Asked when you stop moving for a while."
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CH_SOS, "Emergency SOS", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when a safety check is missed."
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
        )
    }

    fun canPost(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    /** "Are you okay?" notification with a full-screen intent, so it shows over the lock screen. */
    fun showCheckIn(
        context: Context,
        responseSeconds: Int,
        actions: List<NotificationCompat.Action>
    ) {
        if (!canPost(context)) return
        val intent = Intent(context, CheckInActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            21,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CH_CHECKIN)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("Are you okay?")
            .setContentText("No movement for a while. Answer within ${responseSeconds}s.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)

        actions.forEach { builder.addAction(it) }

        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.notify(ID_CHECKIN, builder.build())
        } catch (e: Exception) {
            // Notification permission can be revoked while running; the in-app prompt still works.
        }
    }

    fun cancel(context: Context, id: Int) {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancel(id)
        } catch (e: Exception) {
            // ignore
        }
    }
}
