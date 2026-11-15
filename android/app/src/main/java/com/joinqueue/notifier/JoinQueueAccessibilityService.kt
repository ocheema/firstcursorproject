package com.joinqueue.notifier

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.random.Random

class JoinQueueAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "JoinQueueA11y"
        @Volatile
        var instance: JoinQueueAccessibilityService? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var attempts = 0
    private var joined = false
    private var scheduled = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!Prefs.isRunning(this@JoinQueueAccessibilityService) || joined) {
                scheduled = false
                return
            }
            tryClick()
            val delay = 7000L + Random.nextLong(0, 6001)
            Log.i(TAG, "Next attempt in ${delay}ms")
            handler.postDelayed(this, delay)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
        if (Prefs.isRunning(this)) {
            startLoop()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Loop is timer-driven; events are not required for clicking.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        stopLoop()
        instance = null
        super.onDestroy()
    }

    fun startLoop() {
        if (scheduled) return
        joined = false
        attempts = 0
        scheduled = true
        Prefs.setRunning(this, true)
        handler.post(tickRunnable)
        Log.i(TAG, "Click loop started")
    }

    fun stopLoop() {
        Prefs.setRunning(this, false)
        scheduled = false
        handler.removeCallbacks(tickRunnable)
        Log.i(TAG, "Click loop stopped")
    }

    private fun tryClick() {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "No active window root")
            return
        }

        try {
            if (joinedAlready(root)) {
                onJoined()
                return
            }

            val joinNode = NodeFinder.findJoinQueueNode(root)
            if (joinNode == null) {
                Log.i(TAG, "Join Queue control not visible")
                return
            }

            attempts += 1
            val clicked = NodeFinder.click(joinNode)
            Log.i(TAG, "Attempt $attempts click=$clicked")

            handler.postDelayed({
                val after = rootInActiveWindow
                if (after == null) return@postDelayed
                try {
                    val stillHasJoin = NodeFinder.findJoinQueueNode(after) != null
                    if (!stillHasJoin || joinedAlready(after)) {
                        // After a real click, Join Queue disappearing is the success signal
                        onJoined()
                    }
                } finally {
                    after.recycle()
                }
            }, 1500)
        } finally {
            root.recycle()
        }
    }

    private fun joinedAlready(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val hasJoin = NodeFinder.findJoinQueueNode(root) != null
        val hasSuccess = NodeFinder.hasSuccessText(root)
        return !hasJoin && hasSuccess
    }

    private fun onJoined() {
        if (joined) return
        joined = true
        stopLoop()
        Log.i(TAG, "Joined detected after $attempts attempts")

        Notifier.showJoined(this)

        Thread {
            try {
                EmailSender.sendJoinedEmail(
                    gmailUser = Prefs.gmailUser(this),
                    gmailAppPassword = Prefs.gmailAppPassword(this),
                    notifyEmail = Prefs.notifyEmail(this),
                    campaignUrl = Prefs.campaignUrl(this),
                    attempts = attempts
                )
                Log.i(TAG, "Success email sent")
            } catch (t: Throwable) {
                Log.e(TAG, "Email failed: ${t.message}", t)
            } finally {
                stopService(Intent(this, ClickerForegroundService::class.java))
            }
        }.start()
    }
}
