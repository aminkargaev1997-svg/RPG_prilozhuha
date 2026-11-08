package com.example.rpgcreators

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object CharacterStorage {

    private const val PREFS_NAME = "rpg_creators"
    private const val CHARACTERS_KEY = "characters"

    private const val INVENTORY_PREFIX = "inventory_"
    private const val EQUIPMENT_PREFIX = "equipment_"
    private const val ATTRIBUTES_PREFIX = "attributes_"
    private const val ABILITIES_PREFIX = "abilities_"
    private const val COMBAT_PREFIX = "combat_"
    private const val MAPS_KEY = "shared_maps"
    private const val CHARACTER_MAP_TAGS_PREFIX = "map_tags_"
    private const val CHARACTER_MAP_MARKERS_PREFIX = "map_markers_"

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ПЕРСОНАЖИ
    fun saveCharacters(context: Context, characters: List<Character>) {
        val array = JSONArray()
        characters.forEach { character ->
            array.put(JSONObject().apply {
                put("id", character.id)
                put("name", character.name)
                put("race", character.race)
                put("profession", character.profession)
            })
        }
        preferences(context).edit()
            .putString(CHARACTERS_KEY, array.toString())
            .apply()
    }

    fun loadCharacters(context: Context): List<Character> {
        val saved = preferences(context).getString(CHARACTERS_KEY, "[]") ?: "[]"
        return try {
            val array = JSONArray(saved)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                Character(
                    id = item.getLong("id"),
                    name = item.getString("name"),
                    race = item.getString("race"),
                    profession = item.getString("profession")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ИНВЕНТАРЬ
    fun saveInventory(context: Context, characterId: Long, items: List<InventoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("quantity", item.quantity)
            })
        }
        preferences(context).edit()
            .putString(INVENTORY_PREFIX + characterId, array.toString())
            .apply()
    }

    fun loadInventory(context: Context, characterId: Long): List<InventoryItem> {
        val saved = preferences(context)
            .getString(INVENTORY_PREFIX + characterId, null) ?: return emptyList()
        return try {
            val array = JSONArray(saved)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                InventoryItem(
                    id = item.getLong("id"),
                    name = item.getString("name"),
                    quantity = item.optString("quantity", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ЭКИПИРОВКА
    fun saveEquipment(context: Context, characterId: Long, equipment: Map<String, Long>) {
        val json = JSONObject()
        equipment.forEach { (slot, itemId) -> json.put(slot, itemId) }
        preferences(context).edit()
            .putString(EQUIPMENT_PREFIX + characterId, json.toString())
            .apply()
    }

    fun loadEquipment(context: Context, characterId: Long): Map<String, Long> {
        val saved = preferences(context)
            .getString(EQUIPMENT_PREFIX + characterId, null) ?: return emptyMap()
        return try {
            val json = JSONObject(saved)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, json.getLong(key))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ХАРАКТЕРИСТИКИ
    fun saveAttributes(context: Context, characterId: Long, attributes: Map<String, Int>) {
        val json = JSONObject()
        attributes.forEach { (name, value) -> json.put(name, value) }
        preferences(context).edit()
            .putString(ATTRIBUTES_PREFIX + characterId, json.toString())
            .apply()
    }

    fun loadAttributes(context: Context, characterId: Long): Map<String, Int> {
        val saved = preferences(context)
            .getString(ATTRIBUTES_PREFIX + characterId, null) ?: return emptyMap()
        return try {
            val json = JSONObject(saved)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, json.getInt(key))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // СПОСОБНОСТИ
    fun saveAbilities(context: Context, characterId: Long, abilities: List<Ability>) {
        val array = JSONArray()
        abilities.forEach { ability ->
            array.put(JSONObject().apply {
                put("id", ability.id)
                put("name", ability.name)
                put("description", ability.description)
                put("activatorDescription", ability.activatorDescription)
                put("passiveDescription", ability.passiveDescription)
                put("isUsed", ability.isUsed)
            })
        }
        preferences(context).edit()
            .putString(ABILITIES_PREFIX + characterId, array.toString())
            .apply()
    }

    fun loadAbilities(context: Context, characterId: Long): List<Ability> {
        val saved = preferences(context)
            .getString(ABILITIES_PREFIX + characterId, null) ?: return emptyList()
        return try {
            val array = JSONArray(saved)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val legacyName = item.optString("name", "")
                val legacyActivator = item.optBoolean("isActivator", false)
                val legacyPassive = item.optBoolean("isPassive", false)

                Ability(
                    id = item.getLong("id"),
                    name = legacyName,
                    description = item.optString(
                        "description",
                        if (legacyActivator) "Старая отдельная запись активатора" else ""
                    ),
                    activatorDescription = item.optString("activatorDescription", ""),
                    passiveDescription = item.optString(
                        "passiveDescription",
                        if (legacyPassive) "Пассивный эффект (запись из старой версии)" else ""
                    ),
                    isUsed = item.optBoolean("isUsed", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // СОСТОЯНИЕ БОЯ
    fun saveCombatState(context: Context, characterId: Long, inCombat: Boolean) {
        preferences(context).edit()
            .putBoolean(COMBAT_PREFIX + characterId, inCombat)
            .apply()
    }

    fun loadCombatState(context: Context, characterId: Long): Boolean {
        return preferences(context).getBoolean(COMBAT_PREFIX + characterId, false)
    }

    // ОБЩАЯ БИБЛИОТЕКА КАРТ
    fun saveMaps(context: Context, maps: List<GameMap>) {
        val array = JSONArray()
        maps.forEach { map ->
            array.put(JSONObject().apply {
                put("id", map.id)
                put("name", map.name)
                put("imageUri", map.imageUri)
                put("tags", JSONArray().apply {
                    map.tags.forEach { put(it) }
                })
            })
        }
        preferences(context).edit().putString(MAPS_KEY, array.toString()).apply()
    }

    fun loadMaps(context: Context): List<GameMap> {
        val saved = preferences(context).getString(MAPS_KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(saved)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val tagsJson = item.optJSONArray("tags") ?: JSONArray()
                GameMap(
                    id = item.getLong("id"),
                    name = item.getString("name"),
                    imageUri = item.getString("imageUri"),
                    tags = List(tagsJson.length()) { tagIndex -> tagsJson.getString(tagIndex) }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ПЕРСОНАЛЬНЫЕ ОТМЕТКИ НА КАРТАХ.
    // Данные разделены по characterId + mapId, координаты нормализованы в диапазон 0..1.
    fun saveCharacterMapMarkers(
        context: Context,
        characterId: Long,
        mapId: Long,
        markers: List<MapMarker>
    ) {
        val array = JSONArray()
        markers.forEach { marker ->
            array.put(JSONObject().apply {
                put("id", marker.id)
                put("name", marker.name)
                put("description", marker.description)
                put("type", marker.type)
                put("x", marker.x.toDouble())
                put("y", marker.y.toDouble())
            })
        }
        preferences(context).edit()
            .putString(CHARACTER_MAP_MARKERS_PREFIX + characterId + "_" + mapId, array.toString())
            .apply()
    }

    fun loadCharacterMapMarkers(
        context: Context,
        characterId: Long,
        mapId: Long
    ): List<MapMarker> {
        val saved = preferences(context)
            .getString(CHARACTER_MAP_MARKERS_PREFIX + characterId + "_" + mapId, null)
            ?: return emptyList()
        return try {
            val array = JSONArray(saved)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                MapMarker(
                    id = item.getLong("id"),
                    name = item.getString("name"),
                    description = item.optString("description", ""),
                    type = item.optString("type", "Обычная"),
                    x = item.getDouble("x").toFloat().coerceIn(0f, 1f),
                    y = item.getDouble("y").toFloat().coerceIn(0f, 1f)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // УДАЛЕНИЕ ДАННЫХ
    fun deleteInventory(context: Context, characterId: Long) {
        preferences(context).edit()
            .remove(INVENTORY_PREFIX + characterId)
            .apply()
    }

    fun deleteCharacterData(context: Context, characterId: Long) {
        val prefs = preferences(context)
        val editor = prefs.edit()
            .remove(INVENTORY_PREFIX + characterId)
            .remove(EQUIPMENT_PREFIX + characterId)
            .remove(ATTRIBUTES_PREFIX + characterId)
            .remove(ABILITIES_PREFIX + characterId)
            .remove(COMBAT_PREFIX + characterId)

        val markerPrefix = CHARACTER_MAP_MARKERS_PREFIX + characterId + "_"
        prefs.all.keys
            .filter { it.startsWith(markerPrefix) }
            .forEach { editor.remove(it) }

        editor.apply()
    }
}
