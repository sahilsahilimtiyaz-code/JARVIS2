package com.jarvis.assistant.tools

import android.app.NotificationManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import com.jarvis.assistant.brain.ToolDefinition
import kotlinx.serialization.json.JsonObject

private val YOUTUBE_PACKAGE = "com.google.android.youtube"

class YouTubeSearchTool : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = "search_youtube",
        description = "Open YouTube and search for a query, e.g. \"lo-fi beats\". Prefers the " +
            "YouTube app and falls back to the browser.",
        parameters = objectSchema(
            required = listOf("query"),
            properties = mapOf(
                "query" to stringProperty("What to search for on YouTube."),
            ),
        ),
    )

    override val label = "YouTube search"
    override val category = ToolCategory.MEDIA

    override fun availability(context: Context): ToolAvailability =
        if (isPackageInstalled(context, YOUTUBE_PACKAGE)) {
            ToolAvailability("YouTube app", true)
        } else {
            ToolAvailability("Browser", true)
        }

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val query = arguments.argString("query")
            ?: return ToolResult(false, "Tell me what to search for on YouTube.")

        val appContext = context.appContext
        val packageManager = appContext.packageManager

        if (isPackageInstalled(appContext, YOUTUBE_PACKAGE)) {
            val appSearch = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(YOUTUBE_PACKAGE)
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = packageManager.resolveActivity(appSearch, 0)
            if (resolved != null) {
                val opened = runCatching { appContext.startActivity(appSearch) }.isSuccess
                if (opened) return ToolResult(true, "Searching YouTube for \"$query\".")
            }
        }

        val url = "https://www.youtube.com/results?search_query=" + Uri.encode(query)
        return openUrl(appContext, url, "Searching YouTube for \"$query\" in the browser.")
    }
}

class WebSearchTool : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = "web_search",
        description = "Search the web for a query, e.g. \"weather in Mumbai\" or \"latest Pixel news\".",
        parameters = objectSchema(
            required = listOf("query"),
            properties = mapOf(
                "query" to stringProperty("The search query."),
            ),
        ),
    )

    override val label = "Web search"
    override val category = ToolCategory.MEDIA

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val query = arguments.argString("query")
            ?: return ToolResult(false, "Tell me what to search the web for.")
        val appContext = context.appContext

        val webSearch = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (appContext.packageManager.resolveActivity(webSearch, 0) != null) {
            val opened = runCatching { appContext.startActivity(webSearch) }.isSuccess
            if (opened) return ToolResult(true, "Searching the web for \"$query\".")
        }

        return openUrl(
            appContext,
            "https://www.google.com/search?q=" + Uri.encode(query),
            "Searching the web for \"$query\".",
        )
    }
}

@Suppress("DEPRECATION")
class MediaControlTool : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = "control_media",
        description = "Play, pause, or skip the media that is currently playing on the phone.",
        parameters = objectSchema(
            required = listOf("action"),
            properties = mapOf(
                "action" to stringProperty(
                    "What to do with the current media.",
                    enum = listOf("play_pause", "play", "pause", "next", "previous", "stop"),
                ),
            ),
        ),
    )

    override val label = "Media control"
    override val category = ToolCategory.MEDIA

    override fun availability(context: Context): ToolAvailability {
        val audio = context.getSystemService(AudioManager::class.java)
        return if (audio?.isMusicActive == true) {
            ToolAvailability("Playing", true)
        } else {
            ToolAvailability("Ready", true)
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val action = arguments.argString("action")?.lowercase() ?: "play_pause"
        val keyCode = when (action) {
            "play", "pause", "play_pause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next", "skip" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "back" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return ToolResult(false, "I do not know the media action \"$action\".")
        }

        val audio = context.appContext.getSystemService(AudioManager::class.java)
            ?: return ToolResult(false, "Audio is unavailable on this device.")

        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))

        val label = when (action) {
            "next", "skip" -> "Skipped to the next track."
            "previous", "back" -> "Went back a track."
            "stop" -> "Stopped playback."
            else -> "Toggled playback."
        }
        return ToolResult(true, label)
    }
}

class VolumeTool : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = "set_volume",
        description = "Set the phone volume, either to an exact percentage or one step up/down.",
        parameters = objectSchema(
            required = emptyList(),
            properties = mapOf(
                "level" to intProperty("Target volume as a percentage from 0 to 100."),
                "direction" to stringProperty(
                    "Use \"up\" or \"down\" instead of a level to step the volume.",
                    enum = listOf("up", "down"),
                ),
                "stream" to stringProperty(
                    "Which volume to change. Defaults to media.",
                    enum = listOf("media", "ring", "alarm", "notification"),
                ),
            ),
        ),
    )

    override val label = "Volume"
    override val category = ToolCategory.DEVICE

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val audio = context.appContext.getSystemService(AudioManager::class.java)
            ?: return ToolResult(false, "Audio is unavailable on this device.")

        val stream = when (arguments.argString("stream")?.lowercase()) {
            "ring", "ringer" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }
        val max = audio.getStreamMaxVolume(stream)
        val current = audio.getStreamVolume(stream)
        val target = when {
            arguments.argInt("level") != null ->
                (max * arguments.argInt("level")!!.coerceIn(0, 100) + 50) / 100
            arguments.argString("direction")?.lowercase() == "down" -> current - 1
            arguments.argString("direction")?.lowercase() == "up" -> current + 1
            else -> return ToolResult(false, "Tell me the volume you want, or say louder or quieter.")
        }.coerceIn(0, max)

        runCatching { audio.setStreamVolume(stream, target, 0) }
            .getOrElse { error ->
                return ToolResult(false, "Android would not change the volume: ${error.message}.")
            }

        val percent = if (max > 0) (target * 100) / max else 0
        return ToolResult(true, "Volume set to $percent%.")
    }
}

class RingerTool : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = "set_ringer_mode",
        description = "Switch the phone between sound, vibrate and silent.",
        parameters = objectSchema(
            required = listOf("mode"),
            properties = mapOf(
                "mode" to stringProperty(
                    "The ringer mode to switch to.",
                    enum = listOf("sound", "vibrate", "silent"),
                ),
            ),
        ),
    )

    override val label = "Ringer mode"
    override val category = ToolCategory.DEVICE

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val mode = arguments.argString("mode")?.lowercase()
            ?: return ToolResult(false, "Tell me whether to use sound, vibrate or silent.")

        val appContext = context.appContext
        val audio = appContext.getSystemService(AudioManager::class.java)
            ?: return ToolResult(false, "Audio is unavailable on this device.")
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)

        val target = when (mode) {
            "sound", "normal", "ring" -> AudioManager.RINGER_MODE_NORMAL
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            "silent", "mute" -> AudioManager.RINGER_MODE_SILENT
            else -> return ToolResult(false, "I do not know the ringer mode \"$mode\".")
        }

        if (target == AudioManager.RINGER_MODE_SILENT &&
            notificationManager?.isNotificationPolicyAccessGranted != true
        ) {
            return ToolResult(
                false,
                "Android only lets me go fully silent with Do Not Disturb access. Open " +
                    "Settings, Notifications, Do Not Disturb access and allow Jarvis, " +
                    "or ask me to switch to vibrate instead.",
            )
        }

        runCatching { audio.ringerMode = target }
            .getOrElse { error ->
                return ToolResult(false, "Android refused the ringer change: ${error.message}.")
            }

        val label = when (target) {
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "sound"
        }
        return ToolResult(true, "Ringer set to $label.")
    }
}

object TorchState {
    @Volatile
    var isOn: Boolean = false
}

class FlashlightTool : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = "control_flashlight",
        description = "Turn the phone torch (flashlight) on or off. With no argument it toggles.",
        parameters = objectSchema(
            required = emptyList(),
            properties = mapOf(
                "on" to booleanProperty("true to turn the torch on, false to turn it off."),
            ),
        ),
    )

    override val label = "Flashlight"
    override val category = ToolCategory.DEVICE

    override fun availability(context: Context): ToolAvailability =
        if (flashCameraId(context) != null) {
            ToolAvailability(if (TorchState.isOn) "On" else "Ready", true)
        } else {
            ToolAvailability("No flash", false)
        }

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val appContext = context.appContext
        val manager = appContext.getSystemService(CameraManager::class.java)
            ?: return ToolResult(false, "This phone does not expose a torch.")
        val cameraId = flashCameraId(appContext)
            ?: return ToolResult(false, "This phone has no flashlight.")

        val target = arguments.argBoolean("on") ?: !TorchState.isOn

        fun apply(): Boolean = runCatching {
            manager.setTorchMode(cameraId, target)
            true
        }.getOrDefault(false)

        if (!apply()) {
            val granted = context.permissions.ensure(
                android.Manifest.permission.CAMERA,
                "The torch needs the Camera permission on some phones.",
            )
            if (!granted || !apply()) {
                return ToolResult(
                    false,
                    "The torch is unavailable. Close other camera apps and allow the " +
                        "Camera permission in Settings, Permissions.",
                )
            }
        }

        TorchState.isOn = target
        return ToolResult(true, if (target) "Torch on." else "Torch off.")
    }
}

internal fun flashCameraId(context: Context): String? {
    val manager = context.getSystemService(CameraManager::class.java) ?: return null
    val ids = runCatching { manager.cameraIdList }.getOrElse { return null }
    for (id in ids) {
        val hasFlash = runCatching {
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
        }.getOrNull()
        if (hasFlash == true) return id
    }
    return null
}

internal fun isPackageInstalled(context: Context, packageName: String): Boolean =
    runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

internal fun openUrl(context: Context, url: String, successMessage: String): ToolResult {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }
        .fold(
            onSuccess = { ToolResult(true, successMessage) },
            onFailure = { error ->
                ToolResult(false, "No app could open that link: ${error.message}.")
            },
        )
}
