package com.larprober.recall

import android.content.Context
import org.json.JSONArray
import java.io.File

/** Tiny JSON-file store for recording metadata. Audio files live in filesDir/recordings. */
object RecordingStore {

    private const val INDEX = "recordings.json"

    fun audioDir(ctx: Context): File =
        File(ctx.filesDir, "recordings").apply { if (!exists()) mkdirs() }

    private fun indexFile(ctx: Context) = File(ctx.filesDir, INDEX)

    @Synchronized
    fun all(ctx: Context): List<Recording> {
        val f = indexFile(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length())
                .map { Recording.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.startTime }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(ctx: Context, rec: Recording) {
        val current = all(ctx).toMutableList()
        current.add(rec)
        save(ctx, current)
    }

    @Synchronized
    fun delete(ctx: Context, rec: Recording) {
        runCatching { File(rec.filePath).delete() }
        save(ctx, all(ctx).filterNot { it.id == rec.id })
    }

    private fun save(ctx: Context, list: List<Recording>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        indexFile(ctx).writeText(arr.toString())
    }
}
