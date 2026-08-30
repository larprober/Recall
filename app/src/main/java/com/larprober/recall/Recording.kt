package com.larprober.recall

import org.json.JSONObject

/** One recorded call. */
data class Recording(
    val id: Long,
    val filePath: String,
    val number: String,
    val direction: String,   // "in" or "out"
    val startTime: Long,     // epoch millis
    val durationMs: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("filePath", filePath)
        put("number", number)
        put("direction", direction)
        put("startTime", startTime)
        put("durationMs", durationMs)
    }

    companion object {
        fun fromJson(o: JSONObject) = Recording(
            id = o.getLong("id"),
            filePath = o.getString("filePath"),
            number = o.optString("number", "Unknown"),
            direction = o.optString("direction", "in"),
            startTime = o.getLong("startTime"),
            durationMs = o.optLong("durationMs", 0L)
        )
    }
}
