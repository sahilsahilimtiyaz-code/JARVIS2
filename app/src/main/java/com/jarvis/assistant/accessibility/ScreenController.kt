package com.jarvis.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.tools.ScreenResult

/**
 * A small, deliberate screen-control toolkit.
 *
 * It exposes *single* actions rather than a free-running agent loop: the model
 * asks for one tap, sees the result, and decides again. That keeps every step
 * visible in the action log and makes Stop instant, because nothing is queued
 * behind a long automation race.
 */
class ScreenController(private val service: AccessibilityService) {

    data class ScreenSnapshot(
        val packageName: String,
        val appLabel: String,
        val text: String,
        val nodeCount: Int,
    )

    private var walked = 0

    fun foregroundPackage(): String =
        service.rootInActiveWindow?.packageName?.toString().orEmpty()

    fun appLabel(packageName: String): String = runCatching {
        val pm = service.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    fun snapshot(): ScreenSnapshot {
        val root = service.rootInActiveWindow
            ?: return ScreenSnapshot("", "", "", 0)
        val builder = StringBuilder()
        var count = 0
        walked = 0
        walk(root) { node ->
            count++
            val line = describe(node)
            if (line.isNotBlank()) builder.append(line).append('\n')
            true
        }
        val packageName = root.packageName?.toString().orEmpty()
        return ScreenSnapshot(
            packageName = packageName,
            appLabel = if (packageName.isBlank()) "" else appLabel(packageName),
            text = builder.toString().trim(),
            nodeCount = count,
        )
    }

    /**
     * Taps the best match for [text]. Exact matches beat contains matches, and a
     * clickable ancestor is used when the label itself is not clickable, which
     * is the norm in Material and One UI.
     */
    fun tap(text: String): ScreenResult {
        val root = service.rootInActiveWindow
            ?: return ScreenResult(false, "I cannot see the screen yet. Check the accessibility service.")
        val wanted = normalize(text)
        if (wanted.isBlank()) return ScreenResult(false, "Tell me what to tap.")

        val candidates = mutableListOf<Pair<Int, AccessibilityNodeInfo>>()
        walked = 0
        walk(root) { node ->
            val label = normalize(
                node.text?.toString().orEmpty() + " " + node.contentDescription?.toString().orEmpty(),
            )
            if (label.isBlank()) return@walk true
            val score = when {
                label == wanted -> 100
                label.startsWith(wanted) -> 80
                label.contains(wanted) -> 60
                wanted.contains(label) && label.length >= 3 -> 40
                else -> 0
            }
            if (score > 0) candidates += score to node
            candidates.size < MAX_CANDIDATES ||
                candidates.none { it.second === node } && candidates.size < MAX_CANDIDATES * 2
        }

        if (candidates.isEmpty()) {
            return ScreenResult(false, "I could not find \"$text\" on this screen.")
        }

        val sorted = candidates.sortedByDescending { it.first }
        for ((_, node) in sorted) {
            val clickable = clickableTarget(node) ?: continue
            val performed = runCatching {
                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }.getOrDefault(false)
            if (performed) {
                val label = labelOf(node).ifBlank { text }
                return ScreenResult(true, "Tapped \"$label\".")
            }
        }
        return ScreenResult(false, "I found \"$text\", but it is not something I can tap.")
    }

    /** Types into the focused field, or the first editable field on screen. */
    fun type(text: String): ScreenResult {
        if (text.isEmpty()) return ScreenResult(false, "There is nothing to type.")
        val root = service.rootInActiveWindow
            ?: return ScreenResult(false, "I cannot see the screen yet.")
        walked = 0
        val field = findFirst(root) { it.isEditable && it.isFocused }
            ?: run {
                walked = 0
                findFirst(root) { it.isEditable }
            }
            ?: return ScreenResult(false, "I could not find a text field on this screen.")

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = runCatching {
            field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }.getOrDefault(false)
        if (!ok) {
            // Some Material fields only accept text once focused.
            runCatching { field.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
            val retry = runCatching {
                field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }.getOrDefault(false)
            if (!retry) return ScreenResult(false, "That field refused the text.")
        }
        return ScreenResult(true, "Typed ${text.length} characters into the field.")
    }

    fun scroll(forward: Boolean): ScreenResult {
        val root = service.rootInActiveWindow
            ?: return ScreenResult(false, "I cannot see the screen yet.")
        walked = 0
        val scrollable = findFirst(root) { it.isScrollable }
            ?: return ScreenResult(false, "This screen does not scroll.")
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val ok = runCatching { scrollable.performAction(action) }.getOrDefault(false)
        return when {
            ok && forward -> ScreenResult(true, "Scrolled down.")
            ok -> ScreenResult(true, "Scrolled up.")
            forward -> ScreenResult(false, "That screen is already at the bottom.")
            else -> ScreenResult(false, "That screen is already at the top.")
        }
    }

    fun press(key: String): ScreenResult {
        val action = when (key.lowercase()) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents", "recent apps" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications", "notification shade" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            else -> return ScreenResult(
                false,
                "I do not know that button. Try back, home, recents or notifications.",
            )
        }
        val ok = runCatching { service.performGlobalAction(action) }.getOrDefault(false)
        return if (ok) {
            ScreenResult(true, "Pressed $key.")
        } else {
            ScreenResult(false, "Android refused that button.")
        }
    }

    private fun clickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable && node.isEnabled) return node
        var parent = runCatching { node.parent }.getOrNull()
        var depth = 0
        while (parent != null && depth < 6) {
            if (parent.isClickable && parent.isEnabled) return parent
            parent = runCatching { parent?.parent }.getOrNull()
            depth++
        }
        return null
    }

    private fun findFirst(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        walk(root) { node ->
            if (found == null && predicate(node)) {
                found = node
                false
            } else {
                true
            }
        }
        return found
    }

    private fun labelOf(node: AccessibilityNodeInfo): String =
        node.text?.toString() ?: node.contentDescription?.toString().orEmpty()

    private fun describe(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        if (text.isBlank() && description.isBlank()) return ""
        // Never surface password fields, even though Android usually masks them.
        if (node.isPassword) return "- [password field]"
        val label = if (text.isNotBlank()) text else description
        val suffix = buildString {
            if (node.isEditable) append(" [editable]")
            if (node.isClickable) append(" [button]")
            if (node.isScrollable) append(" [scrollable]")
        }
        return "- $label$suffix".take(300)
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /**
     * Depth-first walk with node and depth budgets. The callback returns false
     * to stop. Web views and very deep One UI hierarchies would otherwise cost
     * more than the answer is worth.
     */
    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int = 0,
        visitor: (AccessibilityNodeInfo) -> Boolean,
    ) {
        if (depth > MAX_DEPTH || walked >= MAX_NODES) return
        walked++
        if (!visitor(node)) return
        for (index in 0 until node.childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            walk(child, depth + 1, visitor)
            if (walked >= MAX_NODES) return
        }
    }

    private companion object {
        const val MAX_NODES = 160
        const val MAX_DEPTH = 14
        const val MAX_CANDIDATES = 6
    }
}
