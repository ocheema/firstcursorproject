package com.joinqueue.notifier

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlin.random.Random

/**
 * Clicks Join Queue repeatedly in Chrome. Most clicks fail — after each click we
 * verify real success UI before stopping. Phone notification only on confirmed join.
 */
class JoinQueueAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "JoinQueueA11y"
        private const val FIRST_DELAY_MS = 4000L
        private const val POST_CLICK_CHECK_MS = 2500L
        private const val CONFIRM_SUCCESS_MS = 1500L

        @Volatile
        var instance: JoinQueueAccessibilityService? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var attempts = 0
    private var joined = false
    private var busy = false

    private val loopRunnable = Runnable { runCycle() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        instance = this
        Log.i(TAG, "Accessibility service connected")
        if (Prefs.isRunning(this)) {
            startLoop()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        stopLoop()
        instance = null
        super.onDestroy()
    }

    fun startLoop() {
        handler.removeCallbacksAndMessages(null)
        joined = false
        busy = false
        attempts = 0
        Prefs.setRunning(this, true)
        Prefs.setStatus(this, "Starting — waiting for Chrome to load…")
        Notifier.updateProgress(this, "Waiting for page…", silent = true)
        // Give Chrome time to open/load before the first attempt
        handler.postDelayed(loopRunnable, FIRST_DELAY_MS)
        Log.i(TAG, "Click loop scheduled (first attempt in ${FIRST_DELAY_MS}ms)")
    }

    fun stopLoop() {
        Prefs.setRunning(this, false)
        busy = false
        handler.removeCallbacksAndMessages(null)
        Prefs.setStatus(this, "Stopped")
        Log.i(TAG, "Click loop stopped")
    }

    private fun stillRunning(): Boolean = Prefs.isRunning(this) && !joined

    private fun runCycle() {
        if (!stillRunning() || busy) return
        busy = true

        if (NodeFinder.looksJoined(this)) {
            Log.i(TAG, "Success UI already visible — confirming")
            Prefs.setStatus(this, "Success UI seen — confirming…")
            confirmSuccessThenFinish()
            return
        }

        val joinNode = NodeFinder.findJoinQueueNode(this)
        if (joinNode == null) {
            val summary = NodeFinder.debugSummary(this)
            Log.i(TAG, "Join Queue not found. Visible: $summary")
            Prefs.setStatus(this, "Join Queue not found yet. $summary")
            Notifier.updateProgress(this, "Looking… attempt pending", silent = true)
            busy = false
            scheduleNextRetry()
            return
        }

        attempts += 1
        Prefs.setStatus(this, "Attempt $attempts: clicking Join Queue")
        Notifier.updateProgress(this, "Attempt $attempts — clicking…", silent = true)
        val clicked = NodeFinder.click(this, joinNode)
        Log.i(TAG, "Attempt $attempts: clicked=$clicked — verifying after delay")

        handler.postDelayed({
            if (!stillRunning()) {
                busy = false
                return@postDelayed
            }
            verifyAfterClick()
        }, POST_CLICK_CHECK_MS)
    }

    private fun verifyAfterClick() {
        if (NodeFinder.looksJoined(this)) {
            Log.i(TAG, "Attempt $attempts: success signal seen — confirming")
            Prefs.setStatus(this, "Attempt $attempts: possible success — confirming…")
            confirmSuccessThenFinish()
            return
        }

        val stillHasJoin = NodeFinder.hasJoinQueue(this)
        val msg = if (stillHasJoin) {
            "Attempt $attempts failed — Join Queue still there, retrying"
        } else {
            "Attempt $attempts failed — no success UI yet, retrying"
        }
        Log.i(TAG, msg)
        Prefs.setStatus(this, msg)
        Notifier.updateProgress(this, "Attempt $attempts failed — retrying", silent = true)
        busy = false
        scheduleNextRetry()
    }

    private fun confirmSuccessThenFinish() {
        handler.postDelayed({
            if (!stillRunning()) {
                busy = false
                return@postDelayed
            }
            if (NodeFinder.looksJoined(this)) {
                onJoined()
            } else {
                Log.i(TAG, "Success not confirmed — keep retrying")
                Prefs.setStatus(this, "Success not confirmed — keep retrying")
                busy = false
                scheduleNextRetry()
            }
        }, CONFIRM_SUCCESS_MS)
    }

    private fun scheduleNextRetry() {
        if (!stillRunning()) return
        val delay = 7000L + Random.nextLong(0, 6001)
        Prefs.setStatus(this, "Next try in ${delay / 1000}s (attempts=$attempts)")
        Log.i(TAG, "Next attempt in ${delay}ms (attempts=$attempts)")
        handler.postDelayed(loopRunnable, delay)
    }

    private fun onJoined() {
        if (joined) return
        joined = true
        busy = false
        stopLoop()
        Prefs.setStatus(this, "Joined after $attempts attempts")
        Log.i(TAG, "Join confirmed after $attempts attempts")
        // Only alert notification is the success one
        Notifier.showJoined(this)
        stopService(Intent(this, ClickerForegroundService::class.java))
    }
}
