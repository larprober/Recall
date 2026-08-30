package com.larprober.recall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arm after a reboot. BOOT_COMPLETED is an allowed reason to start a
 * foreground service from the background, so the recorder comes back on its own.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching { CallRecorderService.ensureRunning(context) }
        }
    }
}
