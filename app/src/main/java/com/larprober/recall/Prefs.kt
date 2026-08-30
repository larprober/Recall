package com.larprober.recall

import android.content.Context

/** Tiny SharedPreferences wrapper for app settings. */
object Prefs {
    private const val FILE = "recall_prefs"
    private const val KEY_AUTO_SPEAKER = "auto_speaker"
    private const val KEY_SOURCE_INDEX = "source_index"

    // Recording mode indices (see MODE_NAMES in MainActivity):
    // 0 Auto, 1 Voice call, 2 Voice communication, 3 Voice recognition, 4 Mic
    const val MODE_COUNT = 5

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun autoSpeaker(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AUTO_SPEAKER, true)

    fun setAutoSpeaker(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AUTO_SPEAKER, value).apply()
    }

    fun sourceIndex(ctx: Context): Int =
        prefs(ctx).getInt(KEY_SOURCE_INDEX, 0).coerceIn(0, MODE_COUNT - 1)

    fun setSourceIndex(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt(KEY_SOURCE_INDEX, value).apply()
    }
}
