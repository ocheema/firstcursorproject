package com.joinqueue.notifier

import android.view.accessibility.AccessibilityNodeInfo

object NodeFinder {
    private val joinPattern = Regex("join\\s*queue", RegexOption.IGNORE_CASE)

    // Positive success signals only — never treat "button missing" alone as success.
    private val successPattern = Regex(
        "leave\\s*queue|in\\s*the\\s*queue|already\\s*in\\s*(the\\s*)?queue|" +
            "you.?re\\s*in\\s*(the\\s*)?queue|joined\\s*the\\s*queue|successfully\\s*joined|" +
            "queue\\s*joined|waiting\\s*in\\s*(the\\s*)?queue",
        RegexOption.IGNORE_CASE
    )

    fun findJoinQueueNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        return findFirst(root) { node ->
            val text = nodeLabel(node)
            joinPattern.containsMatchIn(text) && node.isVisibleToUser
        }?.let { makeClickable(it) }
    }

    fun hasJoinQueue(root: AccessibilityNodeInfo?): Boolean =
        findJoinQueueNode(root) != null

    fun hasSuccessText(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return findFirst(root) { node ->
            val text = nodeLabel(node)
            successPattern.containsMatchIn(text) && !joinPattern.containsMatchIn(text)
        } != null
    }

    /**
     * True only when Join Queue is gone AND a positive success label is visible.
     * A missing button alone is NOT success (common right after a failed click).
     */
    fun looksJoined(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return !hasJoinQueue(root) && hasSuccessText(root)
    }

    fun click(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String {
        return listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString()
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
