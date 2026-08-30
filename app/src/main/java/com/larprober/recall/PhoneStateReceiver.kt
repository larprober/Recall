package com.larprober.recall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * Lightweight receiver. The actual recording is driven by the persistent
 * CallRecorderService (which listens to call state itself). This receiver only:
 *   1. keeps the service alive, and
 *   2. captures the phone number + call direction so recordings are labelled.
 *
 * NEW_OUTGOING_CALL and the RINGING state both arrive before the call goes
 * off-hook, so the service has the number/direction ready when it starts.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Make sure the recorder service is running to catch the off-hook event.
        runCatching { CallRecorderService.ensureRunning(context) }

        when (intent.action) {
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                CallRecorderService.outgoing = true
                intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)?.let {
                    if (it.isNotBlank()) CallRecorderService.currentNumber = it
                }
            }

            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                    CallRecorderService.outgoing = false
                }
                intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)?.let {
                    if (it.isNotBlank()) CallRecorderService.currentNumber = it
                }
            }
        }
    }
}
