package com.joinqueue.notifier

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.joinqueue.notifier.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Notifier.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        loadFields()
        refreshStatus()

        binding.saveButton.setOnClickListener {
            saveFields()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }

        binding.accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.openCampaignButton.setOnClickListener {
            saveFields()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Prefs.campaignUrl(this))))
        }

        binding.startButton.setOnClickListener {
            saveFields()
            if (!isAccessibilityEnabled()) {
                Toast.makeText(
                    this,
                    "Enable JoinQueue Notifier in Accessibility settings first",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }

            Prefs.setRunning(this, true)
            ContextCompat.startForegroundService(
                this,
                Intent(this, ClickerForegroundService::class.java)
            )
            JoinQueueAccessibilityService.instance?.startLoop()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Prefs.campaignUrl(this))))
            refreshStatus()
            Toast.makeText(this, "Clicker started — keep Chrome on the campaign page", Toast.LENGTH_LONG).show()
        }

        binding.stopButton.setOnClickListener {
            Prefs.setRunning(this, false)
            JoinQueueAccessibilityService.instance?.stopLoop()
            stopService(Intent(this, ClickerForegroundService::class.java))
            refreshStatus()
            Toast.makeText(this, "Clicker stopped", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun loadFields() {
        binding.campaignUrl.setText(Prefs.campaignUrl(this))
    }

    private fun saveFields() {
        Prefs.save(
            this,
            campaignUrl = binding.campaignUrl.text?.toString().orEmpty().ifBlank { Prefs.DEFAULT_URL }
        )
    }

    private fun refreshStatus() {
        val a11y = if (isAccessibilityEnabled()) "Accessibility ON" else "Accessibility OFF"
        val running = if (Prefs.isRunning(this)) "clicker RUNNING" else "clicker idle"
        binding.statusText.text = "Status: $a11y · $running\n${Prefs.status(this)}"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${JoinQueueAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return JoinQueueAccessibilityService.isEnabled()
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
