package com.jarvis.assistant.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.accessibility.JarvisAccessibilityService
import com.jarvis.assistant.service.JarvisListeningService

/** A live snapshot of every permission the app actually uses. */
data class PermissionSummary(
    val microphone: Boolean = false,
    val notifications: Boolean = false,
    val accessibility: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val serviceRunning: Boolean = false,
    val backgroundListeningWanted: Boolean = false,
)

object PermissionStatus {

    fun read(context: Context): PermissionSummary = PermissionSummary(
        microphone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED,
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        accessibility = JarvisAccessibilityService.isEnabled(context),
        accessibilityConnected = JarvisAccessibilityService.isConnected(),
        batteryUnrestricted = (context.getSystemService(PowerManager::class.java))
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: false,
        serviceRunning = JarvisListeningService.isRunning,
    )

    fun openAccessibilitySettings(context: Context) {
        JarvisAccessibilityService.openSettings(context)
    }

    fun openNotificationSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Samsung hides this under Battery, Background usage limits, never sleeping apps. */
    fun requestBatteryUnrestricted(context: Context) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(direct) }.isSuccess
        if (!opened) openAppDetails(context)
    }

    fun openAppDetails(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
