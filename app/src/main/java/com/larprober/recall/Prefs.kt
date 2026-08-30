package com.larprober.recall

import android.content.Context

/** Tiny SharedPreferences wrapper for app settings. */
object Prefs {
    private const val FILE = "recall_prefs"
    private const val KEY_AUTO_SPEAKER = "auto_speaker"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun autoSpeaker(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AUTO_SPEAKER, false)

    fun setAutoSpeaker(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AUTO_SPEAKER, value).apply()
    }
}
