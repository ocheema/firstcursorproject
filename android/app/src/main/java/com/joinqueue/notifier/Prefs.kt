package com.joinqueue.notifier

import android.content.Context

object Prefs {
    private const val NAME = "joinqueue_prefs"

    const val DEFAULT_URL =
        "https://feather.openai.com/campaigns/e27f8b7b-7b84-408b-801d-7437f012e1c1?tab=tasks&is_admin_view=false"

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun campaignUrl(context: Context): String =
        prefs(context).getString("campaign_url", DEFAULT_URL) ?: DEFAULT_URL

    fun isRunning(context: Context): Boolean =
        prefs(context).getBoolean("running", false)

    fun save(context: Context, campaignUrl: String) {
        prefs(context).edit()
            .putString("campaign_url", campaignUrl.trim())
            .apply()
    }

    fun setRunning(context: Context, running: Boolean) {
        prefs(context).edit().putBoolean("running", running).apply()
    }
}
