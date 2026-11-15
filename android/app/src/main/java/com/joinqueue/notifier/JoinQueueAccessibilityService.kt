package com.joinqueue.notifier

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlin.random.Random

/**
 * Clicks Join Queue repeatedly. Most clicks fail — after each click we wait and
 * check for a real success signal before stopping. Otherwise we retry in 7–13s.
 */
class JoinQueueAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "JoinQueueA11y"
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
        handler.post(loopRunnable)
        Log.i(TAG, "Click loop started")
    }

    fun stopLoop() {
        Prefs.setRunning(this, false)
        busy = false
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "Click loop stopped")
    }

    private fun stillRunning(): Boolean = Prefs.isRunning(this) && !joined

    private fun runCycle() {
        if (!stillRunning() || busy) return
        busy = true

        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "No active window — keep Chrome on the campaign page")
            busy = false
            scheduleNextRetry()
            return
        }

        // Already joined from a previous attempt that we haven't notified yet
        if (NodeFinder.looksJoined(root)) {
            Log.i(TAG, "Success UI already visible — confirming")
            confirmSuccessThenFinish()
            return
        }

        val joinNode = NodeFinder.findJoinQueueNode(root)
        if (joinNode == null) {
            Log.i(TAG, "Join Queue not visible yet — retry later")
            busy = false
            scheduleNextRetry()
            return
        }

        attempts += 1
        val clicked = NodeFinder.click(joinNode)
        Log.i(TAG, "Attempt $attempts: clicked=$clicked — waiting to verify success")

        // After every click: wait for UI to settle, then verify.
        // Failed joins are normal; only stop when success is confirmed.
        handler.postDelayed({
            if (!stillRunning()) {
                busy = false
                return@postDelayed
            }
            verifyAfterClick()
        }, POST_CLICK_CHECK_MS)
    }

    private fun verifyAfterClick() {
        val root = rootInActiveWindow
        if (NodeFinder.looksJoined(root)) {
            Log.i(TAG, "Attempt $attempts: success signal seen — confirming")
            confirmSuccessThenFinish()
            return
        }

        val stillHasJoin = NodeFinder.hasJoinQueue(root)
        if (stillHasJoin) {
            Log.i(TAG, "Attempt $attempts: Join Queue still present — join failed, will retry")
        } else {
            Log.i(
                TAG,
                "Attempt $attempts: Join Queue temporarily gone but no success text — treating as failed"
            )
        }

        busy = false
        scheduleNextRetry()
    }

    private fun confirmSuccessThenFinish() {
        // Second read after a short delay so a flicker cannot stop the loop.
        handler.postDelayed({
            if (!stillRunning()) {
                busy = false
                return@postDelayed
            }
            if (NodeFinder.looksJoined(rootInActiveWindow)) {
                onJoined()
            } else {
                Log.i(TAG, "Success not confirmed on second check — keep retrying")
                busy = false
                scheduleNextRetry()
            }
        }, CONFIRM_SUCCESS_MS)
    }

    private fun scheduleNextRetry() {
        if (!stillRunning()) return
        val delay = 7000L + Random.nextLong(0, 6001)
        Log.i(TAG, "Next Join Queue attempt in ${delay}ms (attempts so far=$attempts)")
        handler.postDelayed(loopRunnable, delay)
    }

    private fun onJoined() {
        if (joined) return
        joined = true
        busy = false
        stopLoop()
        Log.i(TAG, "Join confirmed after $attempts attempts — notifying")
        Notifier.showJoined(this)
        stopService(Intent(this, ClickerForegroundService::class.java))
    }
}
