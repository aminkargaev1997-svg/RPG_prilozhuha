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
                put("level", character.level)
                put("experience", character.experience)
                put("experienceRequired", character.experienceRequired)
                put("autoExperience", character.autoExperience)
                put("prestigePoints", character.prestigePoints)
                put("buildVersion", character.buildVersion)
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
                    profession = item.getString("profession"),
                    level = item.optInt("level", 1).coerceIn(1, MAX_CHARACTER_LEVEL),
                    experience = item.optInt("experience", 0).coerceAtLeast(0),
                    experienceRequired = item.optInt("experienceRequired", 250).coerceAtLeast(1),
                    // Legacy characters had a freely configured fixed threshold.
                    autoExperience = item.optBoolean("autoExperience", false),
                    prestigePoints = item.optInt("prestigePoints", 0).coerceAtLeast(0),
                    buildVersion = item.optInt("buildVersion", 1).coerceIn(1, 2)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Virtual, undeletable built-in journal. The quests themselves are stored separately.
    fun journalItemId(characterId: Long): Long = Long.MIN_VALUE + characterId.coerceAtLeast(0L)
    fun isJournalItem(item: InventoryItem): Boolean = item.id < 0L && item.name == "Журнал приключений"
    private fun journalItem(characterId: Long) = InventoryItem(
        id = journalItemId(characterId), name = "Журнал приключений",
        category = "Квестовые", description = "Открывает журнал заданий. Не удаляется."
    )

    // Multi-use teleport scroll: two charges per game day are tracked by the host.
    fun isTeleportScroll(item: InventoryItem): Boolean =
        item.id < 0L && item.name == "Свиток телепортации"
    fun isBoundItem(item: InventoryItem): Boolean = isJournalItem(item) || isTeleportScroll(item)
    private fun teleportScroll(characterId: Long) = InventoryItem(
        id = Long.MIN_VALUE / 2 + characterId.coerceAtLeast(0L), name = "Свиток телепортации",
        category = "Особые", description = "Для группового перехода с расстояния. Два применения за игровой день."
    )

    // One locally persisted wallet per character; copper is the exact accounting unit.
    // Shop wallets are maintained by the LAN host and are NOT silently overwritten.
    private fun walletKey(characterId: Long) = "wallet_copper_" + characterId
    private fun walletLedgerKey(characterId: Long) = "wallet_ledger_copper_" + characterId
    // Signed cumulative manual/loot adjustments, not affected by shop purchases or master grants.
    fun walletLedgerCopper(context: Context, characterId: Long): Long =
        preferences(context).getLong(walletLedgerKey(characterId), 0L)

    /** Align the locally displayed cash with the master's authoritative shop balance.
     * The local sub-gold remainder is preserved. Never count this as a new deposit.
     */
    fun mirrorHostWallet(context: Context, characterId: Long, hostGold: Int): Long = synchronized(this) {
        val remainder = walletCopper(context, characterId) % 100L
        val result = hostGold.coerceAtLeast(0).toLong() * 100L + remainder
        preferences(context).edit().putLong(walletKey(characterId), result).commit()
        result
    }
    fun walletCopper(context: Context, characterId: Long): Long =
        preferences(context).getLong(walletKey(characterId), 0L).coerceAtLeast(0L)

    fun changeWallet(context: Context, characterId: Long, delta: Long): Long = synchronized(this) {
        val current = walletCopper(context, characterId)
        val next = (current + delta).coerceIn(0L, 1_000_000_000_000L)
        val applied = next - current
        preferences(context).edit().putLong(walletKey(characterId), next)
            .putLong(walletLedgerKey(characterId), walletLedgerCopper(context, characterId) + applied).commit()
        next
    }

    // Exact denominations only. A story coin, a relic or a "golden ring" stays an item.
    fun currencyCopperValue(name: String): Int = RpgCurrency.denomination(name)

    /** Convert legacy coin stacks once, and atomically remove them from the inventory. */
    fun migrateCoinItems(context: Context, characterId: Long) = synchronized(this) {
        val prefs = preferences(context)
        val key = INVENTORY_PREFIX + characterId
        val raw = prefs.getString(key, null) ?: return@synchronized
        val array = try { JSONArray(raw) } catch (_: Exception) { return@synchronized }
        val remaining = JSONArray()
        var total = 0L
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val denomination = currencyCopperValue(obj.optString("name"))
            val qty = obj.optString("quantity", "1").ifBlank { "1" }.toLongOrNull()
            if (denomination > 0 && qty != null && qty in 1..1_000_000) {
                total += denomination.toLong() * qty
            } else remaining.put(obj)
        }
        if (total > 0) {
            val before = walletCopper(context, characterId)
            val after = (before + total).coerceAtMost(RpgCurrency.MAX_COPPER)
            prefs.edit().putString(key, remaining.toString())
                .putLong(walletKey(characterId), after)
                .putLong(walletLedgerKey(characterId), walletLedgerCopper(context, characterId) + after - before).commit()
        }
    }

    // ИНВЕНТАРЬ
    fun saveInventory(context: Context, characterId: Long, items: List<InventoryItem>) {
        val array = JSONArray()
        items.filterNot(::isBoundItem).forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("quantity", item.quantity)
                put("description", item.description)
                put("category", item.category)
                put("effects", ItemEffectEngine.toJson(item.effects))
            })
        }
        preferences(context).edit()
            .putString(INVENTORY_PREFIX + characterId, array.toString())
            .apply()
    }

    fun loadInventory(context: Context, characterId: Long): List<InventoryItem> {
        migrateCoinItems(context, characterId)
        val saved = preferences(context)
            .getString(INVENTORY_PREFIX + characterId, null) ?: return listOf(journalItem(characterId), teleportScroll(characterId))
        return try {
            val array = JSONArray(saved)
            listOf(journalItem(characterId), teleportScroll(characterId)) + List(array.length()) { index ->
                val item = array.getJSONObject(index)
                InventoryItem(
                    id = item.getLong("id"),
                    name = item.getString("name"),
                    quantity = item.optString("quantity", ""),
                    description = item.optString("description", ""),
                    category = item.optString("category", "Прочее"),
                    effects = if (item.has("effects")) ItemEffectEngine.fromJson(item.optJSONArray("effects")) else ItemEffectEngine.template(item.optString("name"))
                )
            }
        } catch (_: Exception) {
            listOf(journalItem(characterId))
        }
    }

    /**
     * A shop receipt and the new inventory are committed in ONE preferences
     * transaction. Re-delivery after reconnect cannot give the same items twice.
     */
    fun commitShopPurchaseOnce(context: Context, characterId: Long,
                               receiptId: String, goods: List<InventoryItem>): Boolean = synchronized(this) {
        if (receiptId.isBlank() || goods.isEmpty() || goods.size > 100) return@synchronized false
        if (loadCharacters(context).none { it.id == characterId }) return@synchronized false
        val prefs = preferences(context)
        val key = "shop_receipt_" + receiptId
        if (prefs.getBoolean(key, false)) return@synchronized true
        val existing = loadInventory(context, characterId).filterNot(::isBoundItem)
        val data = JSONArray()
        (existing + goods).forEach { item ->
            data.put(JSONObject().put("id", item.id).put("name", item.name)
                .put("quantity", item.quantity).put("description", item.description)
                .put("category", item.category).put("effects", ItemEffectEngine.toJson(item.effects)))
        }
        prefs.edit().putString(INVENTORY_PREFIX + characterId, data.toString())
            .putBoolean(key, true).commit()
    }

    // ЭКИПИРОВКА
    fun saveEquipment(context: Context, characterId: Long, equipment: Map<String, Long>) {
        val json = JSONObject()
        EquipmentHands.migrate(equipment, loadInventory(context, characterId)).forEach { (slot, itemId) -> json.put(slot, itemId) }
        preferences(context).edit()
            .putString(EQUIPMENT_PREFIX + characterId, json.toString())
            .apply()
    }

    fun loadEquipment(context: Context, characterId: Long): Map<String, Long> {
        val saved = preferences(context)
            .getString(EQUIPMENT_PREFIX + characterId, null) ?: return emptyMap()
        return try {
            val json = JSONObject(saved)
            EquipmentHands.migrate(buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, json.getLong(key))
                }
            }, loadInventory(context, characterId))
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
                put("origin", ability.origin)
                put("spellLevel", ability.spellLevel)
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
                    isUsed = item.optBoolean("isUsed", false),
                    origin = item.optString("origin", ""),
                    spellLevel = item.optInt("spellLevel", 0).coerceIn(-1, 9)
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
        SkillTreeStorage.clear(context, characterId)
        HeroStoryStorage.clear(context, characterId)
        RpgQuestStorage.deleteCharacter(context, characterId)
    }
}
