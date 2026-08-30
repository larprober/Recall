package com.larprober.recall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A single, long-lived foreground service. It is started once (from the app or
 * on boot) and then stays alive listening to the phone state. When a call is
 * connected it records; when the call ends it saves the file. Keeping ONE
 * foreground service alive avoids Android 12+ background service-start
 * restrictions that would otherwise make auto-recording flaky.
 */
class CallRecorderService : Service() {

    private lateinit var telephony: TelephonyManager
    private var listener: PhoneStateListener? = null

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L
    private var recording = false

    private var speakerApplied = false
    private var speakerWasOn = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundSafely()
        telephony = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        listener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                if (!phoneNumber.isNullOrBlank()) currentNumber = phoneNumber
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> onCallConnected()
                    TelephonyManager.CALL_STATE_IDLE -> onCallEnded()
                    TelephonyManager.CALL_STATE_RINGING -> { /* incoming; wait for offhook */ }
                }
            }
        }
        @Suppress("DEPRECATION")
        telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        Log.i(TAG, "CallRecorderService armed and listening.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()
        return START_STICKY
    }

    private fun onCallConnected() {
        if (recording) return
        recording = true
        beginRecording()
    }

    private fun onCallEnded() {
        if (!recording) return
        recording = false
        finishRecording()
        currentNumber = "Unknown"
        outgoing = false
    }

    // ---- recording ----------------------------------------------------------

    private fun beginRecording() {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = if (outgoing) "out" else "in"
        val safeNum = currentNumber.replace(Regex("[^0-9+]"), "").ifBlank { "unknown" }
        val file = File(RecordingStore.audioDir(this), "${stamp}_${dir}_$safeNum.m4a")

        // Prefer true call sources; fall back to MIC. On modern stock Android the
        // call sources are usually blocked, so MIC is what actually succeeds
        // (your side clear, far side faint unless the call is on speaker).
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )

        for (src in sources) {
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            try {
                rec.setAudioSource(src)
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioEncodingBitRate(128_000)
                rec.setAudioSamplingRate(44_100)
                rec.setOutputFile(file.absolutePath)
                rec.prepare()
                rec.start()
                recorder = rec
                outputFile = file
                startedAt = System.currentTimeMillis()
                Log.i(TAG, "Recording started (source=$src) -> ${file.name}")
                enableSpeakerIfWanted()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Audio source $src failed: ${e.message}")
                runCatching { rec.release() }
            }
        }
        recording = false
        Log.e(TAG, "No usable audio source; could not start recording.")
    }

    private fun finishRecording() {
        restoreSpeaker()
        val rec = recorder ?: return
        val file = outputFile
        val savedNumber = currentNumber
        val savedDir = if (outgoing) "out" else "in"
        try {
            rec.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stop() failed (call too short?): ${e.message}")
        } finally {
            runCatching { rec.release() }
            recorder = null
        }
        if (file != null && file.exists() && file.length() > 0) {
            RecordingStore.add(
                this,
                Recording(
                    id = startedAt,
                    filePath = file.absolutePath,
                    number = savedNumber,
                    direction = savedDir,
                    startTime = startedAt,
                    durationMs = System.currentTimeMillis() - startedAt
                )
            )
        } else {
            runCatching { file?.delete() }
        }
        outputFile = null
    }

    // ---- speakerphone (optional, improves far-side capture) -----------------

    private fun enableSpeakerIfWanted() {
        if (!Prefs.autoSpeaker(this)) return
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speaker = am.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null && am.setCommunicationDevice(speaker)) {
                    speakerApplied = true
                }
            } else {
                speakerWasOn = am.isSpeakerphoneOn
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
                speakerApplied = true
            }
            Log.i(TAG, "Speakerphone enabled for capture: $speakerApplied")
        } catch (e: Exception) {
            Log.w(TAG, "Could not enable speakerphone: ${e.message}")
        }
    }

    private fun restoreSpeaker() {
        if (!speakerApplied) return
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = speakerWasOn
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore speakerphone: ${e.message}")
        }
        speakerApplied = false
    }

    // ---- foreground notification -------------------------------------------

    private fun startForegroundSafely() {
        val channelId = "recall_listening"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Recall", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Recall")
            .setContentText("Auto call recording is on")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onDestroy() {
        if (recording) finishRecording()
        listener?.let {
            @Suppress("DEPRECATION")
            telephony.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Recall"
        private const val NOTIF_ID = 4141

        // Updated by PhoneStateReceiver so recordings can be labelled.
        @Volatile var currentNumber: String = "Unknown"
        @Volatile var outgoing: Boolean = false

        fun ensureRunning(ctx: Context) {
            // A microphone foreground service must not be started without the
            // RECORD_AUDIO permission, or the OS will kill us. Skip until granted.
            val granted = ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
            ctx.startForegroundService(Intent(ctx, CallRecorderService::class.java))
        }
    }
}
