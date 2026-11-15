package com.joinqueue.notifier

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

object NodeFinder {
    private const val TAG = "JoinQueueFinder"

    private val joinQueries = listOf(
        "Join Queue",
        "JoinQueue",
        "Join queue",
        "join queue",
        "JOIN QUEUE"
    )

    private val joinPattern = Regex("join\\s*queue", RegexOption.IGNORE_CASE)

    private val successPattern = Regex(
        "leave\\s*queue|in\\s*the\\s*queue|already\\s*in\\s*(the\\s*)?queue|" +
            "you.?re\\s*in\\s*(the\\s*)?queue|joined\\s*the\\s*queue|successfully\\s*joined|" +
            "queue\\s*joined|waiting\\s*in\\s*(the\\s*)?queue",
        RegexOption.IGNORE_CASE
    )

    fun rootsFrom(service: AccessibilityService): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        val windows: List<AccessibilityWindowInfo>? = try {
            service.windows
        } catch (_: Throwable) {
            null
        }
        if (!windows.isNullOrEmpty()) {
            for (window in windows) {
                val root = window.root ?: continue
                roots.add(root)
            }
        }
        service.rootInActiveWindow?.let { active ->
            if (roots.none { it == active }) roots.add(0, active)
        }
        return roots
    }

    fun findJoinQueueNode(service: AccessibilityService): AccessibilityNodeInfo? {
        for (root in rootsFrom(service)) {
            // Chrome often exposes web buttons best via findAccessibilityNodeInfosByText
            for (query in joinQueries) {
                val matches = try {
                    root.findAccessibilityNodeInfosByText(query)
                } catch (_: Throwable) {
                    null
                }
                if (!matches.isNullOrEmpty()) {
                    for (node in matches) {
                        val label = nodeLabel(node)
                        if (joinPattern.containsMatchIn(label) && node.isVisibleToUser) {
                            Log.i(TAG, "Found Join Queue via text search: '$label' clickable=${node.isClickable}")
                            return makeClickable(node)
                        }
                    }
                }
            }

            val walked = findFirst(root) { node ->
                joinPattern.containsMatchIn(nodeLabel(node)) && node.isVisibleToUser
            }
            if (walked != null) {
                Log.i(TAG, "Found Join Queue via tree walk: '${nodeLabel(walked)}'")
                return makeClickable(walked)
            }
        }
        return null
    }

    fun hasJoinQueue(service: AccessibilityService): Boolean =
        findJoinQueueNode(service) != null

    fun hasSuccessText(service: AccessibilityService): Boolean {
        for (root in rootsFrom(service)) {
            val byText = listOf(
                "Leave Queue", "In the queue", "in queue", "Already in", "Joined"
            ).any { q ->
                root.findAccessibilityNodeInfosByText(q)?.any { node ->
                    val label = nodeLabel(node)
                    successPattern.containsMatchIn(label) && !joinPattern.containsMatchIn(label)
                } == true
            }
            if (byText) return true
            if (findFirst(root) { node ->
                    val label = nodeLabel(node)
                    successPattern.containsMatchIn(label) && !joinPattern.containsMatchIn(label)
                } != null
            ) return true
        }
        return false
    }

    fun looksJoined(service: AccessibilityService): Boolean =
        !hasJoinQueue(service) && hasSuccessText(service)

    /** Prefer ACTION_CLICK; fall back to a tap gesture at the node center (needed for many Chrome nodes). */
    fun click(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable || current.isEnabled) {
                val args = Bundle()
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK, args) ||
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) {
                    Log.i(TAG, "ACTION_CLICK ok on '${nodeLabel(current)}'")
                    return true
                }
            }
            current = current.parent
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) {
            Log.w(TAG, "Empty bounds for gesture tap")
            return false
        }
        val x = rect.exactCenterX()
        val y = rect.exactCenterY()
        Log.i(TAG, "Gesture tap at ($x, $y) for '${nodeLabel(node)}' bounds=$rect")
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return service.dispatchGesture(gesture, null, null)
    }

    fun debugSummary(service: AccessibilityService): String {
        val labels = mutableListOf<String>()
        for (root in rootsFrom(service)) {
            collectInteresting(root, labels)
            if (labels.size >= 12) break
        }
        return if (labels.isEmpty()) "no labeled nodes" else labels.joinToString(" | ")
    }

    private fun collectInteresting(node: AccessibilityNodeInfo, out: MutableList<String>) {
        if (out.size >= 12) return
        val label = nodeLabel(node)
        if (label.isNotBlank() && (node.isClickable || joinPattern.containsMatchIn(label) ||
                label.contains("queue", ignoreCase = true) ||
                label.contains("join", ignoreCase = true))
        ) {
            out.add(label.take(40))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectInteresting(child, out)
            if (out.size >= 12) return
        }
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String {
        return listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.hintText?.toString()
        ).joinToString(" ").trim()
    }

    private fun makeClickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo = node
        var parent = current.parent
        while (!current.isClickable && parent != null) {
            current = parent
            parent = current.parent
        }
        return current
    }

    private fun findFirst(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirst(child, predicate)
            if (found != null) return found
        }
        return null
    }
}
