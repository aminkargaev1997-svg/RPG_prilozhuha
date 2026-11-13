package com.example.rpgcreators

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Local, offline copies of maps exchanged during a LAN session. */
internal object LanRoomStorage {
    private val guard = Any()

    fun masterRoomId(context: Context): String = synchronized(guard) {
        val prefs = context.getSharedPreferences("rpg_lan_cache", Context.MODE_PRIVATE)
        prefs.getString("master_installation_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("master_installation_id", it).apply()
        }
    }

    /** Null means never stored; an empty list means the player deleted every marker. */
    fun loadRoomMarkers(context: Context, roomId: String, mapId: String): List<LanMarker>? {
        val key = "markers_${cachedMapId(roomId, mapId)}"
        val prefs = context.getSharedPreferences("rpg_lan_cache", Context.MODE_PRIVATE)
        val json = prefs.getString(key, null) ?: return null
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                LanMarker(item.getString("id"), item.getString("name"),
                    item.optString("description"), item.optString("type", "Обычная"),
                    item.getDouble("x").toFloat(), item.getDouble("y").toFloat(),
                    item.optString("ownerId", "host"))
            }
        } catch (_: Exception) { null }
    }

    fun saveRoomMarkers(context: Context, roomId: String, mapId: String, markers: List<LanMarker>) {
        if (roomId.isBlank() || mapId.isBlank()) return
        val array = JSONArray()
        markers.take(500).forEach { marker ->
            array.put(JSONObject().put("id", marker.id).put("name", marker.name)
                .put("description", marker.description).put("type", marker.type)
                .put("x", marker.x.toDouble()).put("y", marker.y.toDouble())
                .put("ownerId", marker.ownerId))
        }
        context.getSharedPreferences("rpg_lan_cache", Context.MODE_PRIVATE).edit()
            .putString("markers_${cachedMapId(roomId, mapId)}", array.toString()).apply()
    }

    /** Stable across recreated rooms: the same image and name get the same LAN map ID.
     * Different masters still have separate copies because cachedMapId uses roomId. */
    fun stableRoomMapKey(name: String, bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(name.trim().lowercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(bytes)
        return "map-" + digest.digest().take(16).joinToString("") { "%02x".format(it) }
    }

    fun cachedMapId(roomId: String, roomMapId: String): Long =
        UUID.nameUUIDFromBytes("lan-map:$roomId:$roomMapId".toByteArray(Charsets.UTF_8))
            .mostSignificantBits and Long.MAX_VALUE

    fun saveMap(context: Context, roomId: String, roomMapId: String, name: String, bytes: ByteArray): Long {
        require(roomId.isNotBlank() && roomMapId.isNotBlank())
        require(bytes.isNotEmpty() && bytes.size <= LAN_MAX_IMAGE)
        val id = cachedMapId(roomId, roomMapId)
        synchronized(guard) {
            val dir = File(context.filesDir, "lan_room_maps")
            if (!dir.exists() && !dir.mkdirs()) error("Не удалось создать каталог карт")
            val image = File(dir, "$id.img")
            val temporary = File(dir, "$id.pending")
            try {
                temporary.outputStream().use { it.write(bytes) }
                if (!temporary.renameTo(image)) {
                    temporary.copyTo(image, overwrite = true)
                    temporary.delete()
                }
            } finally {
                if (temporary.exists()) temporary.delete()
            }

            val maps = CharacterStorage.loadMaps(context).toMutableList()
            val stored = GameMap(id = id, name = name.take(80), imageUri = Uri.fromFile(image).toString(),
                tags = listOf("Сетевая"))
            val index = maps.indexOfFirst { it.id == id }
            if (index >= 0) maps[index] = stored else maps.add(stored)
            CharacterStorage.saveMaps(context, maps)
        }
        return id
    }

    fun markerId(roomId: String, roomMapId: String, lanMarkerId: String): Long =
        UUID.nameUUIDFromBytes("lan-marker:$roomId:$roomMapId:$lanMarkerId".toByteArray(Charsets.UTF_8))
            .mostSignificantBits and Long.MAX_VALUE

    // One stable ID per character; it does not change on reconnect.
    fun playerPositionId(): Long = Long.MIN_VALUE + 99L

    suspend fun readImage(context: Context, imageUri: String): ByteArray = withContext(Dispatchers.IO) {
        val uri = Uri.parse(imageUri)
        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            val result = ByteArrayOutputStream()
            while (result.size() <= LAN_MAX_IMAGE) {
                val count = input.read(buffer)
                if (count < 0) break
                result.write(buffer, 0, count)
            }
            if (result.size() <= LAN_MAX_IMAGE) result.toByteArray() else null
        }
        if (raw != null && raw.isNotEmpty()) return@withContext raw

        // Large photographs can be scaled and compressed to the protocol's 4 MB limit.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Не удалось открыть изображение" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2500) sample *= 2
        repeat(5) {
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: error("Не удалось уменьшить изображение")
            try {
                for (quality in listOf(88, 73, 58, 43)) {
                    val output = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                    if (output.size() <= LAN_MAX_IMAGE) return@withContext output.toByteArray()
                }
            } finally { bitmap.recycle() }
            sample *= 2
        }
        error("Изображение не удалось сжать до 4 МБ")
    }
}
