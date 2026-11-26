package com.changsheng.carlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParkingRecord(
    val id: Long,
    val spot: String,
    val coord: String,
    val startStr: String,
    val endStr: String,
    val durationSec: Long
)

class ParkingManager(context: Context) {
    private val prefs = context.getSharedPreferences("changsheng_parking", Context.MODE_PRIVATE)
    private val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

    fun isParkingActive(): Boolean = prefs.getLong("active_start_ts", -1L) > 0L

    fun getActiveStartTs(): Long = prefs.getLong("active_start_ts", -1L)
    fun getActiveSpot(): String = prefs.getString("active_spot", "默认车位") ?: "默认车位"
    fun getActiveCoord(): String = prefs.getString("active_coord", "离线 GPS 待定位") ?: "离线 GPS 待定位"

    fun startParking(spot: String, coord: String) {
        prefs.edit()
            .putLong("active_start_ts", System.currentTimeMillis())
            .putString("active_spot", spot.ifBlank { "默认车位" })
            .putString("active_coord", coord)
            .commit() // Synchronous commit so sudden ACC power-off never loses the timestamp!
    }

    fun finishParking(): ParkingRecord? {
        val startTs = getActiveStartTs()
        if (startTs <= 0L) return null
        val endTs = System.currentTimeMillis()
        val durationSec = ((endTs - startTs) / 1000L).coerceAtLeast(1L)
        val rec = ParkingRecord(
            id = endTs,
            spot = getActiveSpot(),
            coord = getActiveCoord(),
            startStr = fmt.format(Date(startTs)),
            endStr = fmt.format(Date(endTs)),
            durationSec = durationSec
        )
        val history = getHistory().toMutableList()
        history.add(0, rec)
        saveHistory(history.take(30))
        prefs.edit().remove("active_start_ts").commit()
        return rec
    }

    fun getHistory(): List<ParkingRecord> {
        val raw = prefs.getString("history_json", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val out = mutableListOf<ParkingRecord>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                ParkingRecord(
                    id = o.optLong("id"),
                    spot = o.optString("spot"),
                    coord = o.optString("coord"),
                    startStr = o.optString("startStr"),
                    endStr = o.optString("endStr"),
                    durationSec = o.optLong("durationSec")
                )
            )
        }
        return out
    }

    fun clearHistory() {
        prefs.edit().putString("history_json", "[]").apply()
    }

    private fun saveHistory(list: List<ParkingRecord>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("spot", it.spot)
                put("coord", it.coord)
                put("startStr", it.startStr)
                put("endStr", it.endStr)
                put("durationSec", it.durationSec)
            })
        }
        prefs.edit().putString("history_json", arr.toString()).apply()
    }

    companion object {
        fun formatDuration(sec: Long): String {
            val h = sec / 3600
            val m = (sec % 3600) / 60
            val s = sec % 60
            return when {
                h > 0 -> "${h}小时${m}分"
                m > 0 -> "${m}分${s}秒"
                else -> "${s}秒"
            }
        }

        fun formatHMS(sec: Long): String {
            val h = sec / 3600
            val m = (sec % 3600) / 60
            val s = sec % 60
            return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        }
    }
}
