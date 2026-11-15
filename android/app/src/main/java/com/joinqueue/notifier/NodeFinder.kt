package com.joinqueue.notifier

import android.view.accessibility.AccessibilityNodeInfo

object NodeFinder {
    private val joinPattern = Regex("join\\s*queue", RegexOption.IGNORE_CASE)
    private val successPattern =
        Regex("joined|in\\s*queue|already\\s*in\\s*queue|queue\\s*joined", RegexOption.IGNORE_CASE)

    fun findJoinQueueNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        return findFirst(root) { node ->
            val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: ""))
                .trim()
            joinPattern.containsMatchIn(text) && (node.isClickable || node.isEnabled)
        }?.let { makeClickable(it) }
    }

    fun hasSuccessText(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return findFirst(root) { node ->
            val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: ""))
                .trim()
            successPattern.containsMatchIn(text) && !joinPattern.containsMatchIn(text)
        } != null
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
