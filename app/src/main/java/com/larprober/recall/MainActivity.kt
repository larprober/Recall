package com.larprober.recall

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.larprober.recall.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var player: MediaPlayer? = null
    private var playingId: Long? = null
    private var items: List<Recording> = emptyList()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refresh()
            updateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.grantButton.setOnClickListener { requestPermissions() }

        binding.speakerSwitch.isChecked = Prefs.autoSpeaker(this)
        binding.speakerSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setAutoSpeaker(this, checked)
        }

        binding.list.setOnItemClickListener { _, _, position, _ ->
            togglePlay(items[position])
        }
        binding.list.setOnItemLongClickListener { _, _, position, _ ->
            confirmDelete(items[position]); true
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        updateStatus()
    }

    override fun onPause() {
        super.onPause()
        stopPlayback()
    }

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.toTypedArray()
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions())
    }

    private fun updateStatus() {
        if (hasAllPermissions()) {
            binding.status.text = getString(R.string.status_armed)
            binding.grantButton.visibility = View.GONE
            // Arm the always-on recorder service.
            runCatching { CallRecorderService.ensureRunning(this) }
        } else {
            binding.status.text = getString(R.string.status_needs_permissions)
            binding.grantButton.visibility = View.VISIBLE
        }
    }

    private fun refresh() {
        items = RecordingStore.all(this)
        binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.list.adapter = object : ArrayAdapter<Recording>(
            this, 0, items
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: layoutInflater.inflate(
                    R.layout.item_recording, parent, false
                )
                val rec = items[position]
                v.findViewById<TextView>(R.id.title).text =
                    (if (rec.direction == "out") "↗ " else "↙ ") + rec.number
                v.findViewById<TextView>(R.id.subtitle).text =
                    "${dateFmt.format(Date(rec.startTime))}  ·  ${formatDur(rec.durationMs)}" +
                        if (playingId == rec.id) "  ▶ playing" else ""
                v.findViewById<ImageButton>(R.id.share).setOnClickListener { shareRecording(rec) }
                return v
            }
        }
    }

    private fun shareRecording(rec: Recording) {
        val file = File(rec.filePath)
        if (!file.exists()) {
            Toast.makeText(this, "File no longer exists", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't share this file", Toast.LENGTH_SHORT).show()
            return
        }
        val label = "Call with ${rec.number} - ${dateFmt.format(Date(rec.startTime))}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, label)
            putExtra(Intent.EXTRA_TITLE, label)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share recording"))
    }

    private fun togglePlay(rec: Recording) {
        if (playingId == rec.id) { stopPlayback(); refresh(); return }
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(rec.filePath)
                setOnCompletionListener { stopPlayback(); refresh() }
                prepare()
                start()
            }
            playingId = rec.id
        } catch (e: Exception) {
            Toast.makeText(this, "Can't play this file", Toast.LENGTH_SHORT).show()
        }
        refresh()
    }

    private fun stopPlayback() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        playingId = null
    }

    private fun confirmDelete(rec: Recording) {
        AlertDialog.Builder(this)
            .setTitle("Delete recording?")
            .setMessage("${rec.number}\n${dateFmt.format(Date(rec.startTime))}")
            .setPositiveButton("Delete") { _, _ ->
                if (playingId == rec.id) stopPlayback()
                RecordingStore.delete(this, rec)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatDur(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    companion object {
        private val dateFmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    }
}
