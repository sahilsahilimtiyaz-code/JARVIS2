package com.jarvis.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * The Tier 3 control surface: reads the visible screen and performs taps, text
 * entry, scrolling and system navigation on the owner's behalf.
 *
 * Android only allows this after the owner enables the service in accessibility
 * settings, including the "Allow restricted settings" step for a sideloaded app.
 * Nothing happens unless a tool asks for it; this service collects no events and
 * sends nothing anywhere by itself.
 */
class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: JarvisAccessibilityService? = null

        fun controller(): ScreenController? = instance?.let { ScreenController(it) }

        fun isConnected(): Boolean = instance != null

        /** True when this service is enabled in Android's accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, JarvisAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            return enabled.split(':').any { entry ->
                ComponentName.unflattenFromString(entry) == expected
            }
        }

        fun openSettings(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
