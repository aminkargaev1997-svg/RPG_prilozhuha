package com.example.rpgcreators

import android.content.Context
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.hypot

/** Fog is a normalized mask independent of image resolution / phone orientation. */
internal data class LanFog(val enabled: Boolean = false, val revealed: String = "0".repeat(48 * 32)) {
    fun visible(x: Float, y: Float): Boolean {
        if (!enabled) return true
        val col = (x.coerceIn(0f, 0.999999f) * 48).toInt()
        val row = (y.coerceIn(0f, 0.999999f) * 32).toInt()
        return revealed.getOrNull(row * 48 + col) == '1'
    }

    fun brush(x: Float, y: Float, erase: Boolean, radius: Float = 0.047f): LanFog {
        val chars = revealed.padEnd(48 * 32, '0').take(48 * 32).toCharArray()
        for (r in 0 until 32) for (c in 0 until 48) {
            val cx = (c + .5f) / 48f
            val cy = (r + .5f) / 32f
            if (hypot(cx - x, cy - y) <= radius) chars[r * 48 + c] = if (erase) '1' else '0'
        }
        return copy(revealed = String(chars))
    }

    /** Block tunneling through hidden regions even when the endpoint is visible. */
    fun allowedPath(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        if (!enabled) return true
        val distance = hypot(x1 - x0, y1 - y0)
        val steps = ceil(distance * 128).toInt().coerceAtLeast(1)
        for (step in 1..steps) {
            val t = step.toFloat() / steps
            if (!visible(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)) return false
        }
        return true
    }
}

internal data class LanConversation(
    val markerId: String,
    val markerName: String,
    val participants: List<String>,
    val speakerId: String = "",
    val global: Boolean = false,
    val masterLine: String = "",
    val history: List<String> = emptyList()
)

internal object LanWorldStorage {
    private fun prefs(context: Context) = context.getSharedPreferences("rpg_lan_world", Context.MODE_PRIVATE)
    private fun key(room: String, map: String): String =
        "fog_" + LanRoomStorage.cachedMapId(room, map)

    fun loadFog(context: Context, room: String, map: String): LanFog {
        if (room.isBlank() || map.isBlank()) return LanFog()
        return try {
            val json = JSONObject(prefs(context).getString(key(room, map), "{}") ?: "{}")
            LanFog(json.optBoolean("enabled"), json.optString("revealed").takeIf { it.length == 48 * 32 }
                ?: "0".repeat(48 * 32))
        } catch (_: Exception) { LanFog() }
    }

    /** Offline network map uses the exact cachedMapId as its local GameMap id. */
    fun loadOfflineFog(context: Context, localMapId: Long): LanFog = try {
        val json = JSONObject(prefs(context).getString("fog_$localMapId", "{}") ?: "{}")
        LanFog(json.optBoolean("enabled"), json.optString("revealed").takeIf { it.length == 48 * 32 }
            ?: "0".repeat(48 * 32))
    } catch (_: Exception) { LanFog() }

    fun saveFog(context: Context, room: String, map: String, fog: LanFog) {
        if (room.isBlank() || map.isBlank()) return
        prefs(context).edit().putString(key(room, map), JSONObject()
            .put("enabled", fog.enabled).put("revealed", fog.revealed).toString()).apply()
    }
}
