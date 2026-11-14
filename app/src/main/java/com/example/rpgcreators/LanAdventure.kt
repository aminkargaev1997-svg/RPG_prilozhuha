package com.example.rpgcreators

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.hypot

internal const val AUTO_RETURN_MARKER_PREFIX = "rpg_return_"
internal fun isAutoReturnMarker(id: String): Boolean = id.startsWith(AUTO_RETURN_MARKER_PREFIX)

/** Persistent master-owned adventure state. Never use visible names as object identifiers. */
internal data class AdventureItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val category: String = "Прочее",
    val quantity: String = "1",
    val keyFor: String = "",
    val price: Int = 0,
    val stock: Int = 1
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("description", description)
        .put("category", category).put("quantity", quantity).put("keyFor", keyFor)
        .put("price", price).put("stock", stock)
    companion object {
        fun parse(o: JSONObject) = AdventureItem(o.optString("id"), o.optString("name"),
            o.optString("description"), o.optString("category", "Прочее"),
            o.optString("quantity", "1"), o.optString("keyFor"),
            o.optInt("price", 0).coerceAtLeast(0), o.optInt("stock", 1).coerceAtLeast(0))
    }
}

/** Chest, door, house, store or portal settings tied to a map marker. */
internal data class AdventureObject(
    val markerId: String,
    val mapId: String,
    val kind: String,
    val locked: Boolean = false,
    val keyId: String = "",
    val targetMapId: String = "",
    val x: Float = .5f,
    val y: Float = .5f,
    val items: List<AdventureItem> = emptyList(),
    val opened: Boolean = false,
    val houseMode: String = "map" // Existing Дом: map or shop. Old Магазин markers stay shops.
) {
    fun json(includeItems: Boolean = true): JSONObject = JSONObject()
        .put("markerId", markerId).put("mapId", mapId).put("kind", kind)
        .put("locked", locked).put("keyId", keyId).put("targetMapId", targetMapId)
        .put("x", x.toDouble()).put("y", y.toDouble()).put("opened", opened)
        .put("houseMode", houseMode)
        .put("items", JSONArray().apply { if (includeItems) items.forEach { put(it.json()) } })
    companion object {
        fun parse(o: JSONObject): AdventureObject {
            val array = o.optJSONArray("items") ?: JSONArray()
            return AdventureObject(o.optString("markerId"), o.optString("mapId"),
                o.optString("kind"), o.optBoolean("locked"), o.optString("keyId"),
                o.optString("targetMapId"), o.optDouble("x", .5).toFloat(),
                o.optDouble("y", .5).toFloat(),
                (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(AdventureItem::parse) },
                o.optBoolean("opened"), o.optString("houseMode", "map"))
        }
    }
}

/** A host-persisted purchase receipt. Stock and coins are reserved before delivery;
 * an acknowledged client receives the same receipt at most once. */
internal data class AdventurePurchase(
    val id: String,
    val characterId: Long,
    val shopMarkerId: String,
    val items: List<AdventureItem>,
    val cost: Int
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("characterId", characterId)
        .put("shopMarkerId", shopMarkerId).put("cost", cost)
        .put("items", JSONArray().apply { items.forEach { put(it.json()) } })
    companion object {
        fun parse(o: JSONObject): AdventurePurchase {
            val a = o.optJSONArray("items") ?: JSONArray()
            return AdventurePurchase(o.optString("id"), o.optLong("characterId", -1),
                o.optString("shopMarkerId"), (0 until a.length()).mapNotNull { i ->
                    a.optJSONObject(i)?.let(AdventureItem::parse)
                }, o.optInt("cost", 0))
        }
    }
}

internal fun AdventureObject.isShop(): Boolean = kind == "Магазин" || (kind == "Дом" && houseMode == "shop")

internal data class AdventureVote(
    val markerId: String,
    val mapId: String,
    val initiator: String,
    val targetMapId: String,
    val voters: List<String>,
    val yes: Set<String> = emptySet(),
    val no: Set<String> = emptySet(),
    val remoteNeedsScroll: Set<String> = emptySet()
) {
    fun json() = JSONObject().put("markerId", markerId).put("mapId", mapId)
        .put("initiator", initiator).put("targetMapId", targetMapId)
        .put("voters", JSONArray(voters)).put("yes", JSONArray(yes.toList()))
        .put("no", JSONArray(no.toList()))
        .put("remote", JSONArray(remoteNeedsScroll.toList()))
    companion object {
        private fun strings(a: JSONArray?): List<String> = (0 until (a?.length() ?: 0)).map { a!!.optString(it) }
        fun parse(o: JSONObject) = AdventureVote(o.optString("markerId"), o.optString("mapId"),
            o.optString("initiator"), o.optString("targetMapId"), strings(o.optJSONArray("voters")),
            strings(o.optJSONArray("yes")).toSet(), strings(o.optJSONArray("no")).toSet(),
            strings(o.optJSONArray("remote")).toSet())
    }
}

internal data class AdventureTrade(
    val id: String = UUID.randomUUID().toString(),
    val proposer: String,
    val recipient: String,
    val proposerItems: List<Long>,
    val recipientItems: List<Long> = emptyList(),
    val proposerNames: List<String> = emptyList(),
    val recipientNames: List<String> = emptyList(),
    val proposerConfirmed: Boolean = false,
    val recipientConfirmed: Boolean = false
) {
    fun json() = JSONObject().put("id", id).put("proposer", proposer).put("recipient", recipient)
        .put("proposerItems", JSONArray(proposerItems)).put("recipientItems", JSONArray(recipientItems))
        .put("proposerNames", JSONArray(proposerNames)).put("recipientNames", JSONArray(recipientNames))
        .put("proposerConfirmed", proposerConfirmed).put("recipientConfirmed", recipientConfirmed)
    companion object {
        fun parse(o: JSONObject): AdventureTrade {
            fun longs(key: String): List<Long> = (o.optJSONArray(key) ?: JSONArray()).let { a ->
                (0 until a.length()).map { a.optLong(it) }
            }
            return AdventureTrade(o.optString("id"), o.optString("proposer"), o.optString("recipient"),
                longs("proposerItems"), longs("recipientItems"),
                (o.optJSONArray("proposerNames") ?: JSONArray()).let { a -> (0 until a.length()).map { a.optString(it) } },
                (o.optJSONArray("recipientNames") ?: JSONArray()).let { a -> (0 until a.length()).map { a.optString(it) } },
                o.optBoolean("proposerConfirmed"), o.optBoolean("recipientConfirmed"))
        }
    }
}

internal data class AdventureWorld(
    val day: Int = 1,
    val phase: String = "day", // day -> night -> day
    val campMapId: String = "",
    val campMarkerId: String = "",
    val objects: List<AdventureObject> = emptyList(),
    val vote: AdventureVote? = null,
    val trade: AdventureTrade? = null,
    // Character ID is stable across reconnects; player network IDs are not.
    val scrollUses: Map<String, Int> = emptyMap(),
    val heroStories: Set<String> = emptySet(),
    val notice: String = "",
    val openChestId: String = "", // view-only
    val wallets: Map<String, Int> = emptyMap(), // master-owned, keyed by stable character ID
    val pendingShop: List<AdventurePurchase> = emptyList(), // master-owned durable receipts
    val openShopId: String = "" // view-only, accessible only to the shopping player
) {
    fun json(viewer: String = "host", openedChest: String = "", openedShop: String = "", viewerCharacterId: Long = -1): JSONObject = JSONObject()
        .put("day", day).put("phase", phase).put("campMapId", campMapId).put("campMarkerId", campMarkerId)
        .put("objects", JSONArray().apply { objects.forEach { obj ->
            put(obj.json(viewer == "host" || ((obj.kind == "Сундук" && obj.markerId == openedChest) ||
                (obj.isShop() && obj.markerId == openedShop))))
        } })
        .put("vote", vote?.json()).put("trade", trade?.takeIf {
            viewer == "host" || viewer in listOf(it.proposer, it.recipient)
        }?.json())
        .put("scrollUses", JSONObject(scrollUses)).put("heroStories", JSONArray(heroStories.toList()))
        .put("notice", notice).put("openChestId", openedChest).put("openShopId", openedShop)
        .put("wallets", JSONObject(if (viewer == "host") wallets
            else wallets.filterKeys { it == viewerCharacterId.toString() }))
        .put("pendingShop", JSONArray().apply {
            pendingShop.filter { viewer == "host" || it.characterId == viewerCharacterId }.forEach { put(it.json()) }
        })
    companion object {
        fun parse(o: JSONObject): AdventureWorld {
            fun strings(a: JSONArray?): Set<String> = (0 until (a?.length() ?: 0)).map { a!!.optString(it) }.toSet()
            val objs = o.optJSONArray("objects") ?: JSONArray()
            val charges = o.optJSONObject("scrollUses") ?: JSONObject()
            val counts = mutableMapOf<String, Int>()
            charges.keys().forEach { counts[it] = charges.optInt(it) }
            val money = o.optJSONObject("wallets") ?: JSONObject()
            val wallets = mutableMapOf<String, Int>()
            money.keys().forEach { k -> wallets[k] = money.optInt(k, 0).coerceAtLeast(0) }
            val receipts = o.optJSONArray("pendingShop") ?: JSONArray()
            return AdventureWorld(o.optInt("day", 1).coerceAtLeast(1), o.optString("phase", "day"),
                o.optString("campMapId"), o.optString("campMarkerId"),
                (0 until objs.length()).mapNotNull { objs.optJSONObject(it)?.let(AdventureObject::parse) },
                o.optJSONObject("vote")?.let(AdventureVote::parse),
                o.optJSONObject("trade")?.let(AdventureTrade::parse), counts,
                strings(o.optJSONArray("heroStories")), o.optString("notice"), o.optString("openChestId"),
                wallets, (0 until receipts.length()).mapNotNull { i -> receipts.optJSONObject(i)?.let(AdventurePurchase::parse) },
                o.optString("openShopId"))
        }
    }
}

/** 0.085 map coordinate units, same range model as NPC conversations. */
internal fun adventureDistanceNear(x: Float, y: Float, marker: LanMarker): Boolean =
    hypot(x - marker.x, y - marker.y) <= LAN_INTERACTION_RADIUS

internal object AdventureStorage {
    private fun prefs(context: Context) = context.getSharedPreferences("rpg_adventure_v1", Context.MODE_PRIVATE)
    private fun key(roomId: String) = "world_$roomId"
    fun load(context: Context, roomId: String): AdventureWorld = try {
        AdventureWorld.parse(JSONObject(prefs(context).getString(key(roomId), "{}") ?: "{}"))
    } catch (_: Exception) { AdventureWorld() }
    fun save(context: Context, roomId: String, world: AdventureWorld) {
        if (roomId.isBlank()) return
        prefs(context).edit().putString(key(roomId), world.json().toString()).apply()
    }
}

/** Applies one host-authorized inventory operation once, even after socket retries. */
internal object AdventureDelivery {
    private fun prefs(context: Context) = context.getSharedPreferences("rpg_adventure_deliveries", Context.MODE_PRIVATE)
    fun apply(context: Context, packet: JSONObject): Boolean {
        val deliveryId = packet.optString("deliveryId")
        val characterId = packet.optLong("characterId", -1L)
        if (deliveryId.isBlank() || characterId < 0L ||
            CharacterStorage.loadCharacters(context).none { it.id == characterId }) return false
        if (prefs(context).getBoolean(deliveryId, false)) return true
        val previous = CharacterStorage.loadInventory(context, characterId).filterNot(CharacterStorage::isBoundItem)
        val operation = packet.optString("operation")
        if (operation == "shop_buy") {
            val items = packet.optJSONArray("items") ?: return false
            val purchased = (0 until items.length()).mapNotNull { i ->
                items.optJSONObject(i)?.let(AdventureItem::parse)
            }
            if (purchased.size != items.length() || purchased.isEmpty() || purchased.size > 100) return false
            return CharacterStorage.commitShopPurchaseOnce(context, characterId, deliveryId,
                purchased.map { record ->
                    InventoryItem(name = record.name.take(100),
                        description = record.description.take(500) +
                            if (record.keyFor.isNotBlank()) " [KEY:${record.keyFor}]" else "",
                        category = record.category.take(60), quantity = record.quantity.take(60))
                })
        }
        val next = when (operation) {
            "add" -> {
                val data = packet.optJSONObject("item") ?: return false
                val record = AdventureItem.parse(data)
                previous + InventoryItem(name = record.name.take(100), description = record.description.take(500) +
                    if (record.keyFor.isNotBlank()) " [KEY:${record.keyFor}]" else "",
                    category = record.category.take(60), quantity = record.quantity.take(60))
            }
            "replace" -> {
                val expected = packet.optJSONArray("expectedIds") ?: return false
                val actualIds = previous.map { it.id }.toSet()
                if ((0 until expected.length()).any { expected.optLong(it) !in actualIds }) return false
                val items = packet.optJSONArray("items") ?: return false
                (0 until items.length()).mapNotNull { index -> items.optJSONObject(index)?.let { obj ->
                    InventoryItem(id = obj.optLong("id", System.nanoTime()), name = obj.optString("name"),
                        quantity = obj.optString("quantity"), description = obj.optString("description"),
                        category = obj.optString("category", "Прочее"))
                } }
            }
            else -> return false
        }
        // Synchronous commit before acknowledgement; no duplicate delivery on repeated packets.
        CharacterStorage.saveInventory(context, characterId, next)
        prefs(context).edit().putBoolean(deliveryId, true).commit()
        return true
    }
}

/** Optional numeric health tracker for the future combat screen; legacy characters default to full. */
internal object AdventureVitalsStorage {
    private fun prefs(context: Context) = context.getSharedPreferences("rpg_adventure_vitals", Context.MODE_PRIVATE)
    fun maxHealth(context: Context, characterId: Long): Int =
        prefs(context).getInt("max_$characterId", 100).coerceAtLeast(1)
    fun currentHealth(context: Context, characterId: Long): Int =
        prefs(context).getInt("current_$characterId", maxHealth(context, characterId))
    fun save(context: Context, characterId: Long, current: Int, maximum: Int) {
        prefs(context).edit().putInt("max_$characterId", maximum.coerceAtLeast(1))
            .putInt("current_$characterId", current.coerceIn(0, maximum.coerceAtLeast(1))).apply()
    }
    fun fullRest(context: Context, characterId: Long) {
        prefs(context).edit().putInt("current_$characterId", maxHealth(context, characterId)).apply()
    }
}
