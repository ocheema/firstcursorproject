package com.joinqueue.notifier

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.joinqueue.notifier.databinding.ActivityMainBinding
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var attempts = 0
    private var running = false
    private var joined = false
    private var pageLoaded = false

    private val loopRunnable = Runnable { runAttempt() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Notifier.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        setupWebView()
        binding.webView.loadUrl(Prefs.campaignUrl(this))
        setStatus("Log into Feather below, then tap Start. Keep this screen open.")

        binding.startButton.setOnClickListener { startClicker() }
        binding.stopButton.setOnClickListener { stopClicker("Stopped") }
        binding.reloadButton.setOnClickListener {
            binding.webView.loadUrl(Prefs.campaignUrl(this))
            setStatus("Reloading campaign page…")
        }
    }

    private fun setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        }
        binding.webView.webChromeClient = WebChromeClient()
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pageLoaded = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                pageLoaded = true
                if (!running) {
                    setStatus("Page loaded. Log in if needed, then tap Start.")
                }
            }
        }
    }

    private fun startClicker() {
        if (running) return
        joined = false
        attempts = 0
        running = true
        Prefs.setRunning(this, true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ContextCompat.startForegroundService(
            this,
            Intent(this, ClickerForegroundService::class.java)
        )
        Notifier.updateProgress(this, "Clicker running…", silent = true)
        setStatus("Starting — first click soon…")
        // Let the page settle, then begin
        handler.postDelayed(loopRunnable, 3000)
        Toast.makeText(this, "Clicker started — leave this app open on the campaign page", Toast.LENGTH_LONG).show()
    }

    private fun stopClicker(message: String) {
        running = false
        Prefs.setRunning(this, false)
        handler.removeCallbacksAndMessages(null)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopService(Intent(this, ClickerForegroundService::class.java))
        setStatus(message)
    }

    private fun runAttempt() {
        if (!running || joined) return
        if (!pageLoaded) {
            setStatus("Waiting for page to finish loading…")
            handler.postDelayed(loopRunnable, 2000)
            return
        }

        attempts += 1
        setStatus("Attempt $attempts: clicking Join Queue…")
        Notifier.updateProgress(this, "Attempt $attempts — clicking…", silent = true)

        eval(JsScripts.CLICK_JOIN_QUEUE) { clickResult ->
            val cleaned = cleanJsResult(clickResult)
            if (cleaned.startsWith("NOT_FOUND")) {
                setStatus("Attempt $attempts: Join Queue not found on page — retrying")
                scheduleNext()
                return@eval
            }

            setStatus("Attempt $attempts: click sent ($cleaned) — checking result…")
            // Wait for Feather to show either red error or success UI
            handler.postDelayed({
                if (!running || joined) return@postDelayed
                eval(JsScripts.CHECK_STATE) { stateRaw ->
                    val state = cleanJsResult(stateRaw)
                    when {
                        state == "SUCCESS" -> {
                            confirmSuccess()
                        }
                        state == "FAILED_ERROR" -> {
                            setStatus("Attempt $attempts: click landed but join failed (error on page) — retrying")
                            Notifier.updateProgress(this, "Attempt $attempts failed — retrying", silent = true)
                            scheduleNext()
                        }
                        else -> {
                            setStatus("Attempt $attempts: no success yet ($state) — retrying")
                            Notifier.updateProgress(this, "Attempt $attempts — retrying", silent = true)
                            scheduleNext()
                        }
                    }
                }
            }, 2500)
        }
    }

    private fun confirmSuccess() {
        // Second check so a flicker cannot notify early
        handler.postDelayed({
            if (!running || joined) return@postDelayed
            eval(JsScripts.CHECK_STATE) { stateRaw ->
                if (cleanJsResult(stateRaw) == "SUCCESS") {
                    onJoined()
                } else {
                    setStatus("Success not confirmed — keep retrying")
                    scheduleNext()
                }
            }
        }, 1500)
    }

    private fun onJoined() {
        if (joined) return
        joined = true
        stopClicker("Joined after $attempts attempts")
        // Alert only on success
        Notifier.showJoined(this)
        Toast.makeText(this, "Successfully joined the queue", Toast.LENGTH_LONG).show()
    }

    private fun scheduleNext() {
        if (!running || joined) return
        val delay = 7000L + Random.nextLong(0, 6001)
        setStatus("${Prefs.status(this)}\nNext try in ${delay / 1000}s")
        handler.postDelayed(loopRunnable, delay)
    }

    private fun eval(script: String, callback: (String?) -> Unit) {
        binding.webView.evaluateJavascript(script) { result ->
            runOnUiThread { callback(result) }
        }
    }

    private fun cleanJsResult(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return "UNKNOWN"
        return raw.trim().removeSurrounding("\"")
            .replace("\\n", " ")
            .replace("\\\"", "\"")
    }

    private fun setStatus(text: String) {
        Prefs.setStatus(this, text)
        binding.statusText.text = text
    }

    override fun onDestroy() {
        if (running) stopClicker("Stopped")
        binding.webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }
    }
}
