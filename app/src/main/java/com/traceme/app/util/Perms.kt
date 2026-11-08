package com.traceme.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Perms {

    val location = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun notifications(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    fun activityRecognition(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACTIVITY_RECOGNITION
        } else {
            null
        }

    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasLocation(context: Context): Boolean = location.any { has(context, it) }

    fun hasNotifications(context: Context): Boolean {
        val permission = notifications() ?: return true
        return has(context, permission)
    }

    fun hasSms(context: Context): Boolean = has(context, Manifest.permission.SEND_SMS)

    fun hasCall(context: Context): Boolean = has(context, Manifest.permission.CALL_PHONE)

    fun hasMotion(context: Context): Boolean {
        val permission = activityRecognition() ?: return true
        return has(context, permission)
    }
}
