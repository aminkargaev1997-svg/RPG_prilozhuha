package com.example.rpgcreators

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

internal const val LAN_PORT = 8988
internal const val LAN_MAX_IMAGE = 4 * 1024 * 1024

internal data class LanPlayer(val id: String, val name: String, val x: Float, val y: Float, val mapId: String = "")
internal data class LanPermissions(
    val createMarkers: Boolean = false,
    val moveOwnMarkers: Boolean = true,
    val deleteOwnMarkers: Boolean = true,
    val skillTreeEnabled: Boolean = true
)
internal data class LanCharacterSnapshot(val ownerId: String, val payload: String)
internal data class LanLog(val time: Long, val actor: String, val action: String)
internal const val LAN_MAX_ROOM_MAPS = 12
internal data class LanRoomMapInfo(val id: String, val name: String, val localMapId: Long)
internal data class LanState(
    // While the host is moving this player, their local joystick is disabled.
    // Empty ID means every guest may move themselves normally.
    val masterMovingPlayerId: String = "",
    val inventoryRevision: Int = 0,
    val adventure: AdventureWorld = AdventureWorld(),
    val fog: LanFog = LanFog(),
    val conversation: LanConversation? = null,
    val quests: List<RpgQuest> = emptyList(),
    val questsVersion: Int = 0,
    val connected: Boolean = false,
    val status: String = "Не подключено",
    val myId: String = "",
    val mapName: String = "",
    val image: ByteArray? = null,
    val activeMapId: String = "",
    val roomId: String = "",
    val roomMaps: List<LanRoomMapInfo> = emptyList(),
    val roomMarkers: Map<String, List<LanMarker>> = emptyMap(),
    val players: List<LanPlayer> = emptyList(),
    val markers: List<LanMarker> = emptyList(),
    val permissions: LanPermissions = LanPermissions(),
    val logs: List<LanLog> = emptyList(),
    val characterSnapshots: List<LanCharacterSnapshot> = emptyList()
)

// Trial interaction range in normalized map coordinates. Independent of zoom and screen pixels.
internal const val LAN_INTERACTION_RADIUS = 0.085f
internal fun lanMarkerInRange(player: LanPlayer, marker: LanMarker): Boolean {
    val dx = player.x - marker.x
    val dy = player.y - marker.y
    return dx * dx + dy * dy <= LAN_INTERACTION_RADIUS * LAN_INTERACTION_RADIUS
}

internal data class LanMarker(
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    val x: Float,
    val y: Float,
    val ownerId: String = "host"
)

// Android can expose both cellular and hotspot addresses simultaneously.
// Never assume that the first private IPv4 address belongs to Wi-Fi.
private data class LanAddress(
    val interfaceName: String,
    val ip: String,
    val prefixLength: Int,
    val priority: Int,
    val category: String
)

private fun networkType(name: String): Pair<Int, String> {
    val n = name.lowercase(Locale.ROOT)
    return when {
        n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("pdp") ||
            n.startsWith("wwan") || n.startsWith("ccinet") -> -100 to "мобильная сеть"
        n.startsWith("tun") || n.startsWith("tap") || n.startsWith("ppp") -> -80 to "VPN / туннель"
        n.startsWith("ap") || n.contains("softap") || n.startsWith("swlan") -> 100 to "вероятно точка доступа"
        n.startsWith("wlan") || n.startsWith("wifi") || n.startsWith("wl") -> 90 to "Wi-Fi"
        n.startsWith("br") || n.contains("bridge") -> 85 to "сетевой мост"
        n.startsWith("rndis") || n.startsWith("usb") -> 75 to "USB-сеть"
        n.startsWith("eth") || n.startsWith("en") -> 70 to "Ethernet"
        else -> 20 to "неопознанный интерфейс"
    }
}

private fun localLanAddresses(): List<LanAddress> {
    val found = mutableListOf<LanAddress>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        while (interfaces.hasMoreElements()) {
            val network = interfaces.nextElement()
            try {
                if (!network.isUp || network.isLoopback) continue
                val name = network.name ?: "unknown"
                val (priority, category) = networkType(name)
                for (item in network.interfaceAddresses) {
                    val address = item.address
                    if (address is Inet4Address && address.isSiteLocalAddress && !address.isLoopbackAddress) {
                        val ip = address.hostAddress ?: continue
                        found.add(LanAddress(name, ip, item.networkPrefixLength.toInt(), priority, category))
                    }
                }
            } catch (_: Exception) {
                // Some Android interfaces may be temporarily unavailable.
            }
        }
    } catch (_: Exception) {
        // Continue with whatever addresses were already found.
    }
    return found.distinctBy { it.interfaceName to it.ip }
        .sortedWith(compareByDescending<LanAddress> { it.priority }.thenBy { it.interfaceName })
}

// A best-effort suggestion only. Hotspot interface names differ across Android devices.
// If only cellular addresses are visible, do not present one as the host's LAN address.
internal fun localLanIp(): String =
    localLanAddresses().firstOrNull { it.priority >= 70 }?.ip ?: "Не определён"

internal fun lanInterfacesReport(): String {
    val addresses = localLanAddresses()
    if (addresses.isEmpty()) return "Локальные IPv4-адреса не найдены"
    return addresses.joinToString("\n") { item ->
        val prefix = if (item.prefixLength in 0..32) "/${item.prefixLength}" else ""
        "${item.interfaceName}: ${item.ip}$prefix — ${item.category}"
    }
}

/**
 * Временная LAN-комната без внешнего сервера. Вся работа с сокетами — вне UI-потока.
 * Протокол: одна JSON-строка на сообщение, включая карту в Base64.
 */
internal class LanSession(
    context: Context,
    private val onState: (LanState) -> Unit,
    private val onMasterEdit: (LanMasterEdit) -> Boolean = { false }
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val peers = CopyOnWriteArrayList<Peer>()
    private val outbound = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val broadcast = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val lock = Any()
    private val players = linkedMapOf<String, LanPlayer>()
    // Only the host can change this lock; incoming guest moves are checked against it.
    private var masterMovingPlayerId = ""
    private data class RoomMap(val info: LanRoomMapInfo, val bytes: ByteArray)
    private val roomMaps = linkedMapOf<String, RoomMap>()
    private val markerSets = linkedMapOf<String, LinkedHashMap<String, LanMarker>>()
    private val positions = linkedMapOf<String, MutableMap<String, Pair<Float, Float>>>()
    private val fogByMap = linkedMapOf<String, LanFog>()
    private var conversation: LanConversation? = null
    private val markers: LinkedHashMap<String, LanMarker>
        get() = markerSets.getOrPut(activeMapId) { linkedMapOf() }
    private val roomId = LanRoomStorage.masterRoomId(appContext)
    private var adventure = AdventureStorage.load(appContext, roomId)
    private val openChests = mutableMapOf<String, String>()
    private val openShops = mutableMapOf<String, String>()
    private data class PendingDelivery(val playerId: String, val label: String,
        val chestMarkerId: String = "", val chestItemId: String = "")
    private val pendingDeliveries = linkedMapOf<String, PendingDelivery>()
    private val peerNotices = mutableMapOf<String, String>()
    private var campaignQuests = RpgQuestStorage.load(appContext, RpgQuestStorage.campaignKey(roomId))
    private var activeMapId = ""
    private var permissions = LanPermissions(skillTreeEnabled = appContext
        .getSharedPreferences("rpg_lan_cache", Context.MODE_PRIVATE)
        .getBoolean("campaign_skill_tree_enabled", true))
    private val logs = ArrayDeque<LanLog>()
    private val snapshots = linkedMapOf<String, String>()
    private val pendingEdits = linkedMapOf<String, Pair<String, String>>()
    @Volatile private var closed = false
    @Volatile private var server: ServerSocket? = null
    @Volatile private var client: Socket? = null
    @Volatile private var outgoing: BufferedWriter? = null
    @Volatile private var state = LanState()
    private var hostMode = false
    @Volatile private var clientTransport = "Ещё не выбирался"

    private fun markerSnapshots(): Map<String, List<LanMarker>> =
        markerSets.mapValues { it.value.values.toList() }

    private fun mapPacket(map: RoomMap): String = JSONObject()
        .put("type", "room_map").put("roomId", roomId)
        .put("id", map.info.id).put("name", map.info.name)
        .put("image", Base64.encodeToString(map.bytes, Base64.NO_WRAP)).toString()

    private fun mapSelectionPacket(): String = JSONObject()
        .put("type", "map_select").put("id", activeMapId).toString()

    /** Add one map to the running room; the host immediately switches everyone to it. */
    fun addMap(id: String, name: String, bytes: ByteArray, initialMarkers: List<LanMarker> = emptyList()): Boolean {
        if (!hostMode || closed || !state.connected || id.isBlank() || name.isBlank() ||
            bytes.isEmpty() || bytes.size > LAN_MAX_IMAGE) return false
        val map: RoomMap
        synchronized(lock) {
            if (id in roomMaps || roomMaps.size >= LAN_MAX_ROOM_MAPS) return false
            val localId = try { LanRoomStorage.saveMap(appContext, roomId, id, name, bytes) }
                catch (_: Exception) { return false }
            map = RoomMap(LanRoomMapInfo(id, name.take(80), localId), bytes)
            roomMaps[id] = map
            fogByMap[id] = LanWorldStorage.loadFog(appContext, roomId, id)
            val saved = LanRoomStorage.loadRoomMarkers(appContext, roomId, id)
            markerSets[id] = linkedMapOf<String, LanMarker>().apply {
                (saved ?: initialMarkers).forEach { marker ->
                    if (marker.id.isNotBlank()) put(marker.id, marker.copy(ownerId = "host"))
                }
            }
        }
        LanRoomStorage.saveRoomMarkers(appContext, roomId, id,
            synchronized(lock) { markerSets[id]?.values?.toList() ?: emptyList() })
        log("Мастер", "Добавил карту «${map.info.name}»")
        switchMap(id, broadcastChange = false)
        // Enqueue the image before the switch: all clients must receive the image first.
        val packet = mapPacket(map)
        val selectedPacket = JSONObject().put("type", "map_select").put("id", id).toString()
        val selectedMarkers = markersMessage(id)
        val selectedFog = fogPacket(id)
        broadcast.execute {
            peers.forEach { peer -> try {
                peer.send(packet)
                peer.send(selectedPacket)
                peer.send(selectedMarkers)
                peer.send(selectedFog)
            } catch (_: Exception) {} }
            publishPlayers()
        }
        return true
    }

    /** Switch the whole room to an already uploaded map. */
    fun switchMap(id: String, broadcastChange: Boolean = true): Boolean {
        if (!hostMode || closed) return false
        val target: RoomMap
        synchronized(lock) {
            target = roomMaps[id] ?: return false
            if (activeMapId == id) return true
            activeMapId = id
            conversation = null
            players.keys.toList().forEach { playerId ->
                val player = players[playerId] ?: return@forEach
                val location = positions[id]?.get(playerId) ?: (0.5f to 0.5f)
                players[playerId] = player.copy(x = location.first, y = location.second, mapId = id)
            }
            update { it.copy(activeMapId = id, roomId = roomId,
                mapName = target.info.name, image = target.bytes,
                roomMaps = roomMaps.values.map { map -> map.info },
                roomMarkers = markerSnapshots(), markers = markers.values.toList(),
                players = players.values.toList(), fog = fogByMap[id] ?: LanFog(), conversation = null) }
        }
        log("Мастер", "Открыл карту «${target.info.name}»")
        if (broadcastChange) {
            val selection = JSONObject().put("type", "map_select").put("id", id).toString()
            val closedDialog = conversationPacket(null)
            val markerData = markersMessage(id)
            val fogData = fogPacket(id)
            broadcast.execute {
            peers.forEach { peer -> try { peer.send(selection); peer.send(markerData); peer.send(fogData); peer.send(closedDialog) } catch (_: Exception) {} }
            publishPlayers()
            }
        }
        return true
    }

    /** Removes a map only from the current LAN room. Offline copies remain in the library. */
    fun removeMap(id: String): Boolean {
        if (!hostMode || closed || !state.connected) return false
        val removedName: String
        val nextId: String
        val currentMarkers: List<LanMarker>
        synchronized(lock) {
            val removed = roomMaps.remove(id) ?: return false
            removedName = removed.info.name
            markerSets.remove(id)
            fogByMap.remove(id)
            positions.remove(id)
            if (activeMapId == id) {
                activeMapId = roomMaps.keys.firstOrNull().orEmpty()
                players.keys.toList().forEach { playerId ->
                    val player = players[playerId] ?: return@forEach
                    val (x, y) = positions[activeMapId]?.get(playerId) ?: (0.5f to 0.5f)
                    players[playerId] = player.copy(x = x, y = y, mapId = activeMapId)
                }
            }
            nextId = activeMapId
            val selectedMap = roomMaps[nextId]
            currentMarkers = markerSets[nextId]?.values?.toList() ?: emptyList()
            update { it.copy(
                activeMapId = nextId,
                mapName = selectedMap?.info?.name.orEmpty(),
                image = selectedMap?.bytes,
                roomMaps = roomMaps.values.map { map -> map.info },
                roomMarkers = markerSnapshots(),
                markers = currentMarkers,
                fog = fogByMap[nextId] ?: LanFog(),
                players = players.values.toList()
            ) }
        }
        log("Мастер", "Убрал карту «$removedName» из комнаты")
        val packet = JSONObject().put("type", "room_map_remove")
            .put("id", id).put("activeId", nextId).toString()
        val selectedMarkers = if (nextId.isNotBlank()) markersMessage(nextId) else null
        broadcast.execute {
            peers.forEach { peer -> try {
                peer.send(packet)
                if (selectedMarkers != null) peer.send(selectedMarkers)
            } catch (_: Exception) {} }
            publishPlayers()
        }
        return true
    }

    fun connectionTransport(): String = clientTransport

    // 0.0.0.0 is a listening address, NOT an address a player should enter.
    fun listeningAddress(): String {
        val listening = server
        return if (listening != null && listening.isBound && !listening.isClosed) {
            "${listening.inetAddress.hostAddress}:${listening.localPort} (слушает)"
        } else {
            "Сервер не запущен"
        }
    }

    // Force a player connection through Wi-Fi if Android exposes a Wi-Fi network.
    // This is particularly useful when Android prefers cellular as its default route.
    private fun wifiSocketOrDefault(): Socket {
        return try {
            val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val wifi = manager?.allNetworks?.firstOrNull { network ->
                manager.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            if (wifi != null) {
                clientTransport = "Wi-Fi (принудительно)"
                wifi.socketFactory.createSocket()
            } else {
                clientTransport = "Маршрут Android по умолчанию (Wi-Fi не найден)"
                Socket()
            }
        } catch (_: Exception) {
            clientTransport = "Маршрут Android по умолчанию (ошибка выбора Wi-Fi)"
            Socket()
        }
    }

    private class Peer(val socket: Socket, val writer: BufferedWriter, val id: String) {
        val sendLock = Any()
        fun send(message: String) {
            synchronized(sendLock) { writer.write(message); writer.newLine(); writer.flush() }
        }
    }

    private fun log(actor: String, action: String) {
        synchronized(lock) {
            logs.add(LanLog(System.currentTimeMillis(), actor.take(40), action.take(160)))
            while (logs.size > 300) logs.removeFirst()
        }
        update { it.copy(logs = synchronized(lock) { logs.toList() }) }
    }

    private fun masterMovementPacket(): String = JSONObject()
        .put("type", "master_movement")
        .put("playerId", synchronized(lock) { masterMovingPlayerId })
        .toString()

    /** Temporarily seize one player's movement; pass null to give control back. */
    fun setMasterMovementTarget(playerId: String?) {
        if (!hostMode || closed) return
        val target = playerId.orEmpty()
        val changed = synchronized(lock) {
            if (target.isNotEmpty() && target !in players) return@synchronized false
            if (masterMovingPlayerId == target) return@synchronized false
            masterMovingPlayerId = target
            true
        }
        if (!changed) return
        update { it.copy(masterMovingPlayerId = target) }
        val packet = masterMovementPacket()
        broadcast.execute {
            if (!closed) peers.forEach { peer -> try { peer.send(packet) } catch (_: Exception) {} }
        }
    }

    private fun permissionMessage(): String = JSONObject().put("type", "permissions")
        .put("create", permissions.createMarkers)
        .put("moveOwn", permissions.moveOwnMarkers)
        .put("deleteOwn", permissions.deleteOwnMarkers)
        .put("skillTreeEnabled", permissions.skillTreeEnabled).toString()

    fun setPermissions(value: LanPermissions) {
        if (!hostMode || closed) return
        permissions = value
        appContext.getSharedPreferences("rpg_lan_cache", Context.MODE_PRIVATE).edit()
            .putBoolean("campaign_skill_tree_enabled", value.skillTreeEnabled).apply()
        update { it.copy(permissions = value) }
        log("Мастер", "Изменил разрешения: создание=${value.createMarkers}, перемещение=${value.moveOwnMarkers}, удаление=${value.deleteOwnMarkers}")
        val msg = permissionMessage()
        broadcast.execute { peers.forEach { try { it.send(msg) } catch (_: Exception) {} } }
    }

    // The host owns campaign quests. Clients only receive quests for their displayed name.
    private fun questsPacket(playerName: String): String = JSONObject()
        .put("type", "quest_list")
        .put("roomId", roomId)
        .put("quests", JSONArray(RpgQuestStorage.encode(campaignQuests.filter { q ->
            q.published && (q.recipient.isBlank() || q.recipient == playerName)
        }))).toString()

    fun setCampaignQuests(quests: List<RpgQuest>) {
        if (!hostMode || closed) return
        val cleaned = quests.take(200)
        synchronized(lock) { campaignQuests = cleaned }
        RpgQuestStorage.save(appContext, RpgQuestStorage.campaignKey(roomId), cleaned)
        update { it.copy(quests = cleaned) }
        broadcast.execute { peers.forEach { peer ->
            try { peer.send(questsPacket(players[peer.id]?.name.orEmpty())) } catch (_: Exception) { }
        } }
    }

    fun sendEvent(action: String) {
        if (action.isBlank() || closed) return
        if (hostMode) { log("Мастер", action); return }
        val writer = outgoing ?: return
        outbound.execute {
            try { synchronized(writer) {
                writer.write(JSONObject().put("type", "event").put("action", action.take(160)).toString())
                writer.newLine(); writer.flush()
            } } catch (_: Exception) {}
        }
    }

    // Character snapshots are accepted only for the socket's own player ID.
    // They are never broadcast to other players: only the host can observe them.
    fun sendCharacterSnapshot(payload: String) {
        if (hostMode || closed || !state.connected || payload.length > 24000) return
        val writer = outgoing ?: return
        outbound.execute {
            try {
                synchronized(writer) {
                    writer.write(JSONObject().put("type", "character_snapshot").put("payload", payload).toString())
                    writer.newLine(); writer.flush()
                }
            } catch (_: Exception) {}
        }
    }


    /** Only a host can send modifications, and only to a connected target player. */
    fun editCharacterAsMaster(
        playerId: String, characterId: Long, operation: String,
        data: JSONObject, actionLabel: String
    ): Boolean {
        if (!hostMode || closed || operation !in setOf(
                "identity", "level", "grant_xp", "set_xp", "max_xp", "auto_xp", "attribute",
                "item_save", "item_delete", "ability_save", "ability_delete", "equipment", "adventure_rest", "hero_story_pair",
                "skill_points", "skill_reset", "skill_learn"
            )) return false
        val target: Peer
        synchronized(lock) {
            target = peers.firstOrNull { it.id == playerId } ?: return false
            val snapshot = snapshots[playerId]?.let { try { JSONObject(it) } catch (_: Exception) { null } }
            if (snapshot == null || snapshot.optLong("id", -1L) != characterId) return false
        }
        val commandId = UUID.randomUUID().toString()
        val message = JSONObject().put("type", "master_edit")
            .put("commandId", commandId).put("characterId", characterId)
            .put("operation", operation).put("data", data).toString()
        if (message.length > 6000) return false
        synchronized(lock) { pendingEdits[commandId] = playerId to actionLabel.take(160) }
        broadcast.execute {
            try { target.send(message) }
            catch (_: Exception) {
                synchronized(lock) { pendingEdits.remove(commandId) }
                log("Мастер", "Не удалось отправить изменение персонажа")
            }
        }
        return true
    }

    private fun acknowledgeMasterEdit(commandId: String, accepted: Boolean) {
        val writer = outgoing ?: return
        outbound.execute {
            try {
                synchronized(writer) {
                    writer.write(JSONObject().put("type", "master_edit_result")
                        .put("commandId", commandId).put("accepted", accepted).toString())
                    writer.newLine(); writer.flush()
                }
            } catch (_: Exception) {}
        }
    }

    private fun receiveMasterEdit(message: JSONObject) {
        val id = message.optString("commandId").take(80)
        val operation = message.optString("operation")
        val characterId = message.optLong("characterId", -1L)
        val data = message.optJSONObject("data") ?: return
        if (id.isBlank() || characterId < 0 || data.toString().length > 5000) return
        val edit = LanMasterEdit(id, characterId, operation, data.toString())
        main.post {
            if (closed) return@post
            val accepted = try { onMasterEdit(edit) } catch (_: Exception) { false }
            acknowledgeMasterEdit(id, accepted)
        }
    }

    private fun receiveCharacterSnapshot(id: String, name: String, payload: String) {
        if (payload.length > 24000) return
        val current = try { JSONObject(payload) } catch (_: Exception) { return }
        val previous: JSONObject?
        synchronized(lock) {
            previous = snapshots[id]?.let { try { JSONObject(it) } catch (_: Exception) { null } }
            snapshots[id] = payload
        }
        update { it.copy(characterSnapshots = synchronized(lock) {
            snapshots.map { (owner, data) -> LanCharacterSnapshot(owner, data) }
        }) }
        val nameFromCharacter = current.optString("name").trim().take(40)
        var playerRenamed = false
        if (nameFromCharacter.isNotBlank()) synchronized(lock) {
            val old = players[id]
            if (old != null && old.name != nameFromCharacter) {
                players[id] = old.copy(name = nameFromCharacter)
                playerRenamed = true
            }
        }
        if (playerRenamed) broadcast.execute { if (!closed) publishPlayers() }
        // Re-send durable, idempotent receipts after reconnect or a lost acknowledgement.
        val characterId = current.optLong("id", -1L)
        synchronized(lock) { adventure.pendingShop.filter { it.characterId == characterId } }
            .forEach { sendShopReceipt(id, it) }
        if (previous != null) {
            val fields = listOf("name" to "Имя", "race" to "Раса", "profession" to "Класс",
                "level" to "Уровень", "experience" to "Опыт", "attributes" to "Характеристики",
                "inventory" to "Инвентарь", "equipment" to "Экипировка", "abilities" to "Способности",
                "tree" to "Древо навыков", "prestigePoints" to "Дополнительные очки")
            fields.forEach { (key, label) ->
                if (previous.opt(key)?.toString() != current.opt(key)?.toString()) {
                    val detail = if (key in listOf("level", "experience", "name", "race", "profession"))
                        ": ${previous.optString(key)} → ${current.optString(key)}" else ""
                    log(name, "Изменил $label$detail")
                }
            }
        } else log(name, "Передал данные персонажа «${current.optString("name").take(40)}»")
    }

    private fun update(transform: (LanState) -> LanState) {
        synchronized(lock) {
            state = transform(state)
            val snapshot = state
            main.post { if (!closed) onState(snapshot) }
        }
    }

    fun host(name: String, mapId: String, mapName: String, bytes: ByteArray,
             initialMarkers: List<LanMarker> = emptyList()) {
        Thread {
            try {
                require(bytes.isNotEmpty() && bytes.size <= LAN_MAX_IMAGE) { "Изображение больше 4 МБ или пустое" }
                require(mapId.isNotBlank()) { "Не задан идентификатор карты" }
                val localId = LanRoomStorage.saveMap(appContext, roomId, mapId, mapName, bytes)
                val listening = ServerSocket(LAN_PORT, 50, InetAddress.getByName("0.0.0.0"))
                if (closed) { listening.close(); return@Thread }
                server = listening
                hostMode = true
                val id = "host"
                synchronized(lock) {
                    activeMapId = mapId
                    roomMaps[mapId] = RoomMap(LanRoomMapInfo(mapId, mapName.take(80), localId), bytes)
                    val saved = LanRoomStorage.loadRoomMarkers(appContext, roomId, mapId)
                    fogByMap[mapId] = LanWorldStorage.loadFog(appContext, roomId, mapId)
                    markerSets[mapId] = linkedMapOf<String, LanMarker>().apply {
                        (saved ?: initialMarkers).forEach { marker ->
                            if (marker.id.isNotBlank()) put(marker.id, marker.copy(ownerId = "host"))
                        }
                    }
                    // The host moderates the room; it is not a player marker.
                }
                LanRoomStorage.saveRoomMarkers(appContext, roomId, mapId,
                    synchronized(lock) { markers.values.toList() })
                update { it.copy(connected = true, status = "Комната создана. TCP-сервер слушает порт $LAN_PORT",
                    myId = id, mapName = mapName, image = bytes, activeMapId = mapId, roomId = roomId,
                    roomMaps = roomMaps.values.map { map -> map.info }, roomMarkers = markerSnapshots(),
                    markers = markers.values.toList(), players = players.values.toList(), permissions = permissions,
                    fog = fogByMap[mapId] ?: LanFog(), quests = campaignQuests, adventure = adventure) }
                log("Мастер", "Создал комнату: $mapName")
                while (!closed) {
                    val socket = try { listening.accept() } catch (_: Exception) { break }
                    Thread {
                        var peer: Peer? = null
                        var nameClient = "Игрок"
                        try {
                            socket.soTimeout = 15000
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                            val hello = JSONObject(reader.readLine() ?: throw IllegalStateException("Нет приветствия"))
                            if (hello.optString("type") != "hello") throw IllegalStateException("Неверное приветствие")
                            val idClient = UUID.randomUUID().toString()
                            nameClient = hello.optString("name", "Игрок").trim().take(40).ifEmpty { "Игрок" }
                            val connectedPeer = Peer(socket, writer, idClient)
                            peer = connectedPeer
                            socket.soTimeout = 0
                            // Send every known map and its markers, then select the active one.
                            val existingMaps = synchronized(lock) { roomMaps.values.toList() }
                            existingMaps.forEach { roomMap -> connectedPeer.send(mapPacket(roomMap)) }
                            existingMaps.forEach { roomMap ->
                                connectedPeer.send(markersMessage(roomMap.info.id))
                                connectedPeer.send(fogPacket(roomMap.info.id))
                            }
                            synchronized(lock) {
                                // Reconcile changes made while the initial image set was sent.
                                val alreadySent = existingMaps.mapTo(hashSetOf()) { it.info.id }
                                alreadySent.filter { it !in roomMaps }.forEach { removedId ->
                                    connectedPeer.send(JSONObject().put("type", "room_map_remove")
                                        .put("id", removedId).put("activeId", "").toString())
                                }
                                roomMaps.values.filter { it.info.id !in alreadySent }.forEach { roomMap ->
                                    connectedPeer.send(mapPacket(roomMap))
                                    connectedPeer.send(markersMessage(roomMap.info.id))
                                    connectedPeer.send(fogPacket(roomMap.info.id))
                                }
                                connectedPeer.send(mapSelectionPacket())
                                connectedPeer.send(JSONObject().put("type", "welcome").put("id", idClient).toString())
                                connectedPeer.send(permissionMessage())
                                connectedPeer.send(masterMovementPacket())
                                connectedPeer.send(questsPacket(nameClient))
                                connectedPeer.send(adventurePacket(idClient))
                                conversation?.let { connectedPeer.send(conversationPacketForPeer(it, idClient)) }
                                players[idClient] = LanPlayer(idClient, nameClient, 0.5f, 0.5f, activeMapId)
                                peers.add(connectedPeer)
                            }
                            publishPlayers()
                            log(nameClient, "Подключился к комнате")
                            while (!closed) {
                                val line = reader.readLine() ?: break
                                if (line.length > 30000) continue
                                val message = try { JSONObject(line) } catch (_: Exception) { continue }
                                val action = message.optString("type")
                                if (action == "marker_add" || action == "marker_update" || action == "marker_delete") {
                                    applyMarkerMessage(message, idClient, nameClient)
                                }
                                if (action == "character_snapshot") {
                                    receiveCharacterSnapshot(idClient, nameClient, message.optString("payload"))
                                }
                                if (action == "dialog_command") {
                                    handleDialogCommand(idClient, message)
                                }
                                if (action == "adventure_action") {
                                    handleAdventureAction(idClient, message)
                                }
                                if (action == "adventure_delivery_ack") {
                                    val deliveryId = message.optString("deliveryId")
                                    val stored = synchronized(lock) { pendingDeliveries[deliveryId] }
                                    val receipt = synchronized(lock) { adventure.pendingShop.firstOrNull {
                                        it.id == deliveryId && it.characterId == charId(idClient)
                                    } }
                                    if (receipt != null) {
                                        if (message.optBoolean("accepted")) {
                                            synchronized(lock) { adventure = adventure.copy(pendingShop =
                                                adventure.pendingShop.filterNot { it.id == deliveryId }) }
                                            log("Магазин", "Покупка доставлена персонажу ${receipt.characterId} — ${receipt.cost} монет")
                                            publishAdventure()
                                        }
                                    } else if (stored?.playerId == idClient) {
                                        synchronized(lock) {
                                            pendingDeliveries.remove(deliveryId)
                                            if (message.optBoolean("accepted") && stored.chestMarkerId.isNotBlank()) {
                                                adventure = adventure.copy(objects = adventure.objects.map { obj ->
                                                    if (obj.markerId == stored.chestMarkerId)
                                                        obj.copy(items = obj.items.filterNot { it.id == stored.chestItemId })
                                                    else obj
                                                })
                                            }
                                        }
                                        publishAdventure()
                                    }
                                }
                                if (action == "event") {
                                    log(nameClient, message.optString("action").take(160))
                                }
                                if (action == "master_edit_result") {
                                    val commandId = message.optString("commandId")
                                    val previous = synchronized(lock) {
                                        pendingEdits[commandId]?.takeIf { it.first == idClient }?.also {
                                            pendingEdits.remove(commandId)
                                        }
                                    }
                                    if (previous != null) log("Мастер", if (message.optBoolean("accepted"))
                                        "${previous.second} — применено у $nameClient"
                                        else "${previous.second} — отклонено у $nameClient")
                                }
                                if (message.optString("type") == "move") {
                                    // The server is authoritative: a late joystick packet
                                    // cannot override a move being made by the host.
                                    if (synchronized(lock) { masterMovingPlayerId == idClient }) continue
                                    val x = message.optDouble("x", Double.NaN)
                                    val y = message.optDouble("y", Double.NaN)
                                    if (x.isFinite() && y.isFinite()) {
                                        synchronized(lock) {
                                            // Recheck under the same lock as host movement.
                                            if (masterMovingPlayerId == idClient) return@synchronized
                                            val old = players[idClient]
                                            if (old != null) {
                                                val playerMapId = old.mapId.ifBlank { activeMapId }
                                                val fog = fogByMap[playerMapId] ?: LanFog()
                                                if (!fog.allowedPath(old.x, old.y, x.toFloat(), y.toFloat())) return@synchronized
                                                val pos = x.toFloat().coerceIn(0f, 1f) to y.toFloat().coerceIn(0f, 1f)
                                                players[idClient] = old.copy(x = pos.first, y = pos.second)
                                                positions.getOrPut(old.mapId.ifBlank { activeMapId }) { linkedMapOf() }[idClient] = pos
                                            }
                                        }
                                        publishPlayers()
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            peer?.let {
                                synchronized(lock) {
                                    peers.remove(it); players.remove(it.id); snapshots.remove(it.id)
                                    if (masterMovingPlayerId == it.id) masterMovingPlayerId = ""
                                    pendingEdits.entries.removeAll { entry -> entry.value.first == it.id }
                                    openChests.remove(it.id)
                                    openShops.remove(it.id)
                                    pendingDeliveries.entries.removeAll { pending -> pending.value.playerId == it.id }
                                    if (adventure.trade?.let { trade -> it.id == trade.proposer || it.id == trade.recipient } == true)
                                        adventure = adventure.copy(trade = null)
                                    if (adventure.vote != null) adventure = adventure.copy(vote = null)
                                    conversation?.let { active ->
                                        if (it.id in active.participants) {
                                            conversation = active.copy(
                                                participants = active.participants - it.id,
                                                speakerId = if (active.speakerId == it.id) "" else active.speakerId)
                                        }
                                    }
                                }
                                update { old -> old.copy(characterSnapshots = synchronized(lock) { snapshots.map { (id, data) -> LanCharacterSnapshot(id, data) } }) }
                                publishPlayers()
                                broadcastAdventureOnDisconnect()
                                broadcastConversation()
                                log(nameClient, "Отключился")
                            }
                            try { socket.close() } catch (_: Exception) {}
                        }
                    }.start()
                }
            } catch (e: Exception) {
                update { it.copy(status = "Ошибка создания комнаты: ${e.message}") }
            }
        }.start()
    }

    fun join(ip: String, name: String) {
        Thread {
            try {
                val socket = wifiSocketOrDefault()
                client = socket  // Also close a failed connection attempt in finally.
                socket.connect(InetSocketAddress(ip, LAN_PORT), 8000)
                if (closed) { socket.close(); return@Thread }
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                outgoing = writer
                synchronized(writer) {
                    writer.write(JSONObject().put("type", "hello").put("name", name.take(40)).toString())
                    writer.newLine()
                    writer.flush()
                }
                update { it.copy(connected = true, status = "Подключено к $ip:$LAN_PORT") }
                while (!closed) {
                    val line = reader.readLine() ?: break
                    val message = JSONObject(line)
                    when (message.optString("type")) {
                        "room_map" -> {
                            val bytes = Base64.decode(message.getString("image"), Base64.DEFAULT)
                            if (bytes.isEmpty() || bytes.size > LAN_MAX_IMAGE) throw IllegalStateException("Карта слишком большая или пустая")
                            val remoteRoomId = message.optString("roomId")
                            val id = message.optString("id").take(100)
                            val name = message.optString("name").take(80)
                            if (id.isBlank() || remoteRoomId.isBlank()) continue
                            val localId = LanRoomStorage.saveMap(appContext, remoteRoomId, id, name, bytes)
                            synchronized(lock) {
                                roomMaps[id] = RoomMap(LanRoomMapInfo(id, name, localId), bytes)
                                fogByMap[id] = LanWorldStorage.loadFog(appContext, remoteRoomId, id)
                            }
                            update { it.copy(roomId = remoteRoomId,
                                roomMaps = synchronized(lock) { roomMaps.values.map { map -> map.info } }) }
                        }
                        "room_map_remove" -> {
                            val removedId = message.optString("id")
                            val requestedId = message.optString("activeId")
                            val nextMap: RoomMap?
                            val nextId: String
                            val nextMarkers: List<LanMarker>
                            synchronized(lock) {
                                roomMaps.remove(removedId)
                                markerSets.remove(removedId)
                                fogByMap.remove(removedId)
                                positions.remove(removedId)
                                nextId = if (requestedId in roomMaps) requestedId
                                    else if (activeMapId != removedId && activeMapId in roomMaps) activeMapId
                                    else roomMaps.keys.firstOrNull().orEmpty()
                                activeMapId = nextId
                                nextMap = roomMaps[nextId]
                                nextMarkers = markerSets[nextId]?.values?.toList() ?: emptyList()
                            }
                            update { it.copy(
                                activeMapId = nextId,
                                mapName = nextMap?.info?.name.orEmpty(),
                                image = nextMap?.bytes,
                                roomMaps = synchronized(lock) { roomMaps.values.map { map -> map.info } },
                                roomMarkers = synchronized(lock) { markerSnapshots() },
                                markers = nextMarkers, fog = synchronized(lock) { fogByMap[nextId] ?: LanFog() }
                            ) }
                        }
                        "map_select" -> {
                            val id = message.optString("id")
                            val target = synchronized(lock) {
                                roomMaps[id]?.also { activeMapId = id }
                            } ?: continue
                            update { it.copy(activeMapId = id, mapName = target.info.name,
                                image = target.bytes, markers = synchronized(lock) { markers.values.toList() },
                                roomMarkers = synchronized(lock) { markerSnapshots() },
                                fog = synchronized(lock) { fogByMap[id] ?: LanFog() }, conversation = null) }
                        }
                        "fog" -> {
                            val id = message.optString("mapId")
                            val raw = message.optString("revealed")
                            if (id.isNotBlank() && raw.length == 48 * 32 && raw.all { it == '0' || it == '1' }) {
                                val newFog = LanFog(message.optBoolean("enabled"), raw)
                                synchronized(lock) { fogByMap[id] = newFog }
                                LanWorldStorage.saveFog(appContext, state.roomId, id, newFog)
                                update { old -> if (old.activeMapId == id) old.copy(fog = newFog) else old }
                            }
                        }
                        "conversation" -> {
                            val participants = message.optJSONArray("participants") ?: JSONArray()
                            val current = if (message.optBoolean("active")) LanConversation(
                                message.optString("markerId"), message.optString("markerName"),
                                (0 until participants.length()).map { participants.optString(it) },
                                message.optString("speaker"), message.optBoolean("global"),
                                message.optString("line"),
                                (message.optJSONArray("history") ?: JSONArray()).let { values ->
                                    (0 until values.length().coerceAtMost(40)).map { values.optString(it).take(1000) }
                                }) else null
                            update { it.copy(conversation = current) }
                        }
                        "adventure_state" -> {
                            val world = AdventureWorld.parse(message.optJSONObject("world") ?: JSONObject())
                            update { it.copy(adventure = world) }
                        }
                        "adventure_delivery" -> {
                            val accepted = AdventureDelivery.apply(appContext, message)
                            if (accepted) update { it.copy(inventoryRevision = it.inventoryRevision + 1) }
                            val writer = outgoing
                            if (writer != null) outbound.execute { try { synchronized(writer) {
                                writer.write(JSONObject().put("type", "adventure_delivery_ack")
                                    .put("deliveryId", message.optString("deliveryId"))
                                    .put("accepted", accepted).toString())
                                writer.newLine(); writer.flush()
                            } } catch (_: Exception) {} }
                        }
                        "quest_list" -> {
                            val room = message.optString("roomId")
                            val quests = RpgQuestStorage.decode(message.optJSONArray("quests")?.toString() ?: "[]")
                            if (room.isNotBlank()) update { it.copy(roomId = room, quests = quests, questsVersion = it.questsVersion + 1) }
                        }
                        "welcome" -> update { it.copy(myId = message.optString("id")) }
                        "master_movement" -> update { it.copy(
                            masterMovingPlayerId = message.optString("playerId").take(100)
                        ) }
                        "master_edit" -> receiveMasterEdit(message)
                        "permissions" -> update { it.copy(permissions = LanPermissions(
                            message.optBoolean("create"), message.optBoolean("moveOwn", true),
                            message.optBoolean("deleteOwn", true),
                            message.optBoolean("skillTreeEnabled", true)
                        )) }
                        "markers" -> {
                            val array = message.getJSONArray("list")
                            val mapId = message.optString("mapId").ifBlank { activeMapId }
                            if (mapId.isBlank() || synchronized(lock) { mapId !in roomMaps }) continue
                            val list = (0 until array.length()).map { index ->
                                val item = array.getJSONObject(index)
                                LanMarker(item.getString("id"), item.getString("name"),
                                    item.optString("description"), item.optString("markerType", "Обычная"),
                                    item.getDouble("x").toFloat(), item.getDouble("y").toFloat(),
                                    item.optString("ownerId", "host"))
                            }
                            synchronized(lock) { markerSets[mapId] = linkedMapOf<String, LanMarker>().apply {
                                list.forEach { put(it.id, it) }
                            } }
                            LanRoomStorage.saveRoomMarkers(appContext, state.roomId, mapId, list)
                            update { old -> old.copy(
                                markers = if (old.activeMapId == mapId) list else old.markers,
                                roomMarkers = synchronized(lock) { markerSnapshots() }
                            ) }
                        }
                        "players" -> {
                            val array = message.getJSONArray("list")
                            val list = (0 until array.length()).map { index ->
                                val item = array.getJSONObject(index)
                                LanPlayer(item.getString("id"), item.getString("name"), item.getDouble("x").toFloat(), item.getDouble("y").toFloat(), item.optString("mapId", activeMapId))
                            }
                            update { it.copy(players = list) }
                        }
                    }
                }
                if (!closed) update { it.copy(connected = false, status = "Хост отключился", masterMovingPlayerId = "") }
            } catch (e: Exception) {
                val reason = when {
                    e is NoRouteToHostException || e.message?.contains("EHOSTUNREACH", ignoreCase = true) == true ->
                        "Нет маршрута до телефона мастера. Проверь адрес «Роутер» в Wi-Fi и ограничения точки доступа."
                    e is SocketTimeoutException ->
                        "Не удалось связаться с сервером за 8 секунд. Проверь IP и доступ к порту $LAN_PORT."
                    e is ConnectException ->
                        "Соединение отклонено. Проверь, запущена ли комната мастера."
                    else -> e.message ?: e.javaClass.simpleName
                }
                val technical = e.message?.take(180)?.let { "\nПодробности: $it" } ?: ""
                update { it.copy(connected = false, status = "Ошибка подключения: $reason$technical", masterMovingPlayerId = "") }
            } finally {
                try { client?.close() } catch (_: Exception) {}
                client = null
                outgoing = null
            }
        }.start()
    }

    private fun markersMessage(mapId: String = activeMapId): String {
        val array = JSONArray()
        synchronized(lock) {
            markerSets[mapId]?.values?.forEach { marker ->
                array.put(JSONObject().put("id", marker.id).put("name", marker.name)
                    .put("description", marker.description).put("markerType", marker.type)
                    .put("x", marker.x.toDouble()).put("y", marker.y.toDouble())
                    .put("ownerId", marker.ownerId))
            }
        }
        return JSONObject().put("type", "markers").put("mapId", mapId).put("list", array).toString()
    }

    private fun applyMarkerMessage(message: JSONObject, senderId: String = "host", senderName: String = "Мастер") {
        val action = message.optString("type")
        val id = message.optString("id").take(80)
        var event: String? = null
        var accepted = false
        val affectedMapId: String
        val snapshot: List<LanMarker>
        synchronized(lock) {
            if (message.optString("mapId") != activeMapId || activeMapId.isBlank()) return
            affectedMapId = activeMapId
            val old = markers[id]
            when (action) {
                "marker_delete" -> {
                    if (old != null && (senderId == "host" ||
                        old.type !in setOf("Сундук", "Дверь", "Дом", "Магазин", "Портал", "Лагерь") &&
                        (old.ownerId == senderId && permissions.deleteOwnMarkers))) {
                        markers.remove(id)
                        if (senderId == "host") {
                            adventure = adventure.copy(objects = adventure.objects.filterNot { it.markerId == id })
                            AdventureStorage.save(appContext, roomId, adventure)
                        }
                        event = "Удалил метку «${old.name}»"
                        accepted = true
                    }
                }
                "marker_add", "marker_update" -> {
                    val name = message.optString("name").trim().take(80)
                    val x = message.optDouble("x", Double.NaN)
                    val y = message.optDouble("y", Double.NaN)
                    val allowed = if (action == "marker_add") old == null &&
                        (senderId == "host" || permissions.createMarkers)
                    else old != null && (senderId == "host" ||
                        (old.ownerId == senderId && permissions.moveOwnMarkers))
                    val markerType = message.optString("markerType", "Обычная").take(40)
                    val specialAllowed = senderId == "host" ||
                        (markerType !in setOf("NPC", "Толпа", "Записка", "Сундук", "Дверь", "Дом", "Магазин", "Портал", "Лагерь") &&
                            old?.type !in setOf("NPC", "Толпа", "Записка", "Сундук", "Дверь", "Дом", "Магазин", "Портал", "Лагерь"))
                    if (allowed && specialAllowed && id.isNotBlank() && name.isNotBlank() && x.isFinite() && y.isFinite()) {
                        val owner = old?.ownerId ?: senderId
                        markers[id] = LanMarker(id, name,
                            message.optString("description").take(1000),
                            markerType,
                            x.toFloat().coerceIn(0f, 1f), y.toFloat().coerceIn(0f, 1f), owner)
                        accepted = true
                        if (action == "marker_add") {
                        event = "Создал метку «$name»"
                        if (senderId == "host" && markerType in setOf("Сундук", "Дверь", "Дом", "Магазин", "Портал")) {
                            adventure = adventure.copy(objects = adventure.objects +
                                AdventureObject(id, affectedMapId, markerType))
                        }
                    }
                    }
                }
            }
            snapshot = markers.values.toList()
        }
        if (!accepted) return
        if (senderId == "host") publishAdventure()
        event?.let { log(senderName, it) }
        update { current -> current.copy(
            markers = if (current.activeMapId == affectedMapId) snapshot else current.markers,
            roomMarkers = synchronized(lock) { markerSnapshots() }
        ) }
        val serialized = markersMessage(affectedMapId)
        broadcast.execute {
            LanRoomStorage.saveRoomMarkers(appContext, roomId, affectedMapId, snapshot)
            peers.forEach { peer -> try { peer.send(serialized) } catch (_: Exception) {} }
        }
    }

    fun addMarker(marker: LanMarker) {
        val message = JSONObject().put("type", "marker_add").put("id", marker.id)
            .put("name", marker.name).put("description", marker.description)
            .put("markerType", marker.type).put("x", marker.x.toDouble()).put("y", marker.y.toDouble())
        sendMarker(message)
    }

    fun updateMarker(marker: LanMarker) {
        val message = JSONObject().put("type", "marker_update").put("id", marker.id)
            .put("name", marker.name).put("description", marker.description)
            .put("markerType", marker.type).put("x", marker.x.toDouble()).put("y", marker.y.toDouble())
        sendMarker(message)
    }

    fun deleteMarker(id: String) {
        sendMarker(JSONObject().put("type", "marker_delete").put("id", id))
    }

    private fun sendMarker(message: JSONObject) {
        message.put("mapId", synchronized(lock) { activeMapId })
        if (hostMode) {
            applyMarkerMessage(message)
        } else {
            val writer = outgoing ?: return
            outbound.execute {
                try {
                    synchronized(writer) {
                        writer.write(message.toString())
                        writer.newLine()
                        writer.flush()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun publishPlayers() {
        if (closed) return
        val snapshot = synchronized(lock) { players.values.toList() }
        update { it.copy(players = snapshot) }
        val list = JSONArray()
        snapshot.forEach { player ->
            list.put(JSONObject().put("id", player.id).put("name", player.name).put("x", player.x.toDouble()).put("y", player.y.toDouble())
                .put("mapId", player.mapId))
        }
        val msg = JSONObject().put("type", "players").put("list", list).toString()
        peers.forEach { peer ->
            try { peer.send(msg) } catch (_: Exception) {
                synchronized(lock) { peers.remove(peer); players.remove(peer.id) }
                try { peer.socket.close() } catch (_: Exception) {}
            }
        }
    }

    fun movePlayerAsMaster(id: String, x: Float, y: Float) {
        if (!hostMode || closed) return
        synchronized(lock) {
            val old = players[id] ?: return
            val pos = x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)
            players[id] = old.copy(x = pos.first, y = pos.second)
            positions.getOrPut(old.mapId.ifBlank { activeMapId }) { linkedMapOf() }[id] = pos
        }
        update { it.copy(players = synchronized(lock) { players.values.toList() }) }
        broadcast.execute { if (!closed) publishPlayers() }
    }

    fun move(x: Float, y: Float) {
        if (!state.connected || state.masterMovingPlayerId == state.myId) return
        val px = x.coerceIn(0f, 1f)
        val py = y.coerceIn(0f, 1f)
        if (hostMode) {
            return // Only connected guests have player positions.
        } else {
            val writer = outgoing ?: return
            update { current ->
                current.copy(players = current.players.map { player ->
                    if (player.id == current.myId) player.copy(x = px, y = py) else player
                })
            }
            outbound.execute {
                // A queued move must not slip out after the host acquires control.
                if (state.masterMovingPlayerId == state.myId) return@execute
                try {
                    synchronized(writer) {
                        writer.write(JSONObject().put("type", "move").put("x", px.toDouble()).put("y", py.toDouble()).toString())
                        writer.newLine()
                        writer.flush()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun fogPacket(id: String): String {
        val fog = synchronized(lock) { fogByMap[id] ?: LanFog() }
        return JSONObject().put("type", "fog").put("mapId", id)
            .put("enabled", fog.enabled).put("revealed", fog.revealed).toString()
    }

    /** Host-only fog edits. Clients receive the authoritative mask. */
    fun setFogEnabled(enabled: Boolean) {
        if (!hostMode || activeMapId.isBlank()) return
        val fog = synchronized(lock) { fogByMap[activeMapId] ?: LanFog() }
        publishFog(fog.copy(enabled = enabled))
    }

    fun brushFog(x: Float, y: Float, erase: Boolean) {
        if (!hostMode || activeMapId.isBlank()) return
        val fog = synchronized(lock) { fogByMap[activeMapId] ?: LanFog() }
        publishFog(fog.brush(x, y, erase))
    }

    private fun publishFog(fog: LanFog) {
        val id = synchronized(lock) {
            fogByMap[activeMapId] = fog
            activeMapId
        }
        LanWorldStorage.saveFog(appContext, roomId, id, fog)
        update { it.copy(fog = fog) }
        val packet = fogPacket(id)
        broadcast.execute { peers.forEach { peer -> try { peer.send(packet) } catch (_: Exception) {} } }
    }

    private fun conversationPacket(value: LanConversation?): String = JSONObject().apply {
        put("type", "conversation")
        put("active", value != null)
        if (value != null) {
            put("markerId", value.markerId)
            put("markerName", value.markerName)
            put("participants", JSONArray(value.participants))
            put("speaker", value.speakerId)
            put("global", value.global)
            put("line", value.masterLine)
            put("history", JSONArray(value.history))
        }
    }.toString()

    private fun conversationPacketForPeer(value: LanConversation?, peerId: String): String {
        if (value == null || peerId in value.participants) return conversationPacket(value)
        // An observer learns that someone is speaking with this NPC, but receives no dialogue content.
        return conversationPacket(value.copy(participants = emptyList(), speakerId = "",
            masterLine = "", history = emptyList()))
    }

    private fun broadcastConversation() {
        val snapshot = synchronized(lock) { conversation }
        update { it.copy(conversation = snapshot) }
        broadcast.execute {
            peers.forEach { peer -> try { peer.send(conversationPacketForPeer(snapshot, peer.id)) }
                catch (_: Exception) {} }
        }
    }

    /** Players request to join; host validates everything and owns the speaking lock. */
    fun dialogCommand(action: String, markerId: String = "", line: String = "") {
        val data = JSONObject().put("type", "dialog_command").put("action", action)
            .put("markerId", markerId).put("line", line.take(1000))
        if (hostMode) handleDialogCommand("host", data)
        else {
            val writer = outgoing ?: return
            outbound.execute { try { synchronized(writer) {
                writer.write(data.toString()); writer.newLine(); writer.flush()
            } } catch (_: Exception) {} }
        }
    }

    private fun handleDialogCommand(actorId: String, message: JSONObject) {
        if (!hostMode) return
        val action = message.optString("action")
        synchronized(lock) {
            val current = conversation
            when (action) {
                "open", "global" -> {
                    val markerId = message.optString("markerId")
                    val targetMapId = if (actorId == "host") activeMapId else players[actorId]?.mapId ?: return
                    val marker = markerSets[targetMapId]?.get(markerId) ?: return
                    if (marker.type != "NPC" && marker.type != "Толпа") return
                    if (actorId != "host") {
                        if (!(fogByMap[targetMapId] ?: LanFog()).visible(marker.x, marker.y)) return
                        val player = players[actorId] ?: return
                        if (!lanMarkerInRange(player, marker)) return
                    }
                    if (action == "global" && actorId != "host") return
                    if (current != null && current.markerId != markerId) return
                    val allPlayers = if (action == "global") players.keys.toList()
                        else ((current?.participants ?: emptyList()) + actorId + "host").distinct()
                    conversation = LanConversation(markerId, marker.name, allPlayers,
                        current?.speakerId ?: "", action == "global" || current?.global == true,
                        current?.masterLine ?: marker.description,
                        current?.history ?: emptyList())
                }
                "join" -> if (current != null && current.markerId == message.optString("markerId") && actorId in players) {
                    val player = players[actorId] ?: return
                    val marker = markerSets[player.mapId]?.get(current.markerId) ?: return
                    if (!(fogByMap[player.mapId] ?: LanFog()).visible(marker.x, marker.y)) return
                    if (!lanMarkerInRange(player, marker)) return
                    conversation = current.copy(participants = (current.participants + actorId).distinct())
                }
                "leave" -> if (current != null && actorId in current.participants) {
                    val remaining = current.participants - actorId
                    conversation = if (remaining.none { it != "host" } && !current.global) null
                        else current.copy(participants = remaining,
                            speakerId = if (current.speakerId == actorId) "" else current.speakerId)
                }
                "speak" -> if (current != null && actorId in current.participants && current.speakerId.isBlank()) {
                    conversation = current.copy(speakerId = actorId)
                }
                "release" -> if (current != null && (actorId == current.speakerId || actorId == "host")) {
                    conversation = current.copy(speakerId = "")
                }
                "line" -> if (current != null && actorId == "host") {
                    val line = message.optString("line").take(1000)
                    if (line.isNotBlank()) conversation = current.copy(masterLine = line,
                        history = (current.history + line).takeLast(40))
                }
                "close" -> if (actorId == "host") conversation = null
                else -> return
            }
        }
        broadcastConversation()
    }

    private fun broadcastAdventureOnDisconnect() {
        if (hostMode) publishAdventure(false)
    }

    // Adventure actions run only on the host. Clients may request, never mutate world state.
    private fun adventurePacket(viewerId: String): String = synchronized(lock) {
        JSONObject().put("type", "adventure_state")
            .put("world", adventure.json(viewerId, openChests[viewerId].orEmpty(),
                openShops[viewerId].orEmpty(), charId(viewerId)).apply {
                put("notice", peerNotices[viewerId].orEmpty())
            }).toString()
    }

    private fun publishAdventure(persist: Boolean = true) {
        if (persist) AdventureStorage.save(appContext, roomId, adventure.copy(vote = null, trade = null, notice = ""))
        update { it.copy(adventure = adventure) }
        broadcast.execute { peers.forEach { peer ->
            try { synchronized(lock) { peer.send(adventurePacket(peer.id)) } } catch (_: Exception) { }
        } }
    }

    /** Wallets belong to the campaign. Only the master may credit or correct them. */
    fun setPlayerCoins(playerId: String, amount: Int) {
        if (!hostMode || closed || amount !in 0..1_000_000_000) return
        val characterId = synchronized(lock) { charId(playerId) }
        if (characterId <= 0L || adventure.pendingShop.any { it.characterId == characterId }) return
        synchronized(lock) {
            adventure = adventure.copy(wallets = adventure.wallets + (characterId.toString() to amount))
        }
        publishAdventure()
        log("Мастер", "Монеты персонажа ${characterId}: $amount")
    }

    private fun sendShopReceipt(playerId: String, purchase: AdventurePurchase) {
        val peer = peers.firstOrNull { it.id == playerId } ?: return
        val packet = JSONObject().put("type", "adventure_delivery")
            .put("deliveryId", purchase.id).put("characterId", purchase.characterId)
            .put("operation", "shop_buy")
            .put("items", JSONArray().apply { purchase.items.forEach { put(it.json()) } }).toString()
        broadcast.execute { try { peer.send(packet) } catch (_: Exception) { } }
    }

    fun editAdventureObject(value: AdventureObject) {
        if (!hostMode || value.markerId.isBlank()) return
        val marker = synchronized(lock) { markerSets[value.mapId]?.get(value.markerId) } ?: return
        if (marker.type != value.kind || value.items.size > 100 ||
            value.items.any { it.price !in 0..1_000_000 || it.stock !in 0..999 }) return
        synchronized(lock) {
            adventure = adventure.copy(objects = (adventure.objects.filterNot { it.markerId == value.markerId } + value))
        }
        publishAdventure()
        log("Мастер", "Изменил объект «${marker.name}»")
    }

    private fun inventoryFor(id: String): List<JSONObject> {
        val snapshot = snapshots[id]?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyList()
        val a = snapshot.optJSONArray("inventory") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    private fun charId(id: String): Long = snapshots[id]?.let {
        runCatching { JSONObject(it).optLong("id", -1L) }.getOrNull()
    } ?: -1L

    private fun playerNearObject(id: String, obj: AdventureObject): Boolean {
        val player = players[id] ?: return false
        if (player.mapId != obj.mapId) return false
        val marker = markerSets[obj.mapId]?.get(obj.markerId) ?: return false
        return adventureDistanceNear(player.x, player.y, marker) &&
            (fogByMap[obj.mapId] ?: LanFog()).visible(marker.x, marker.y)
    }

    private fun hasKey(id: String, obj: AdventureObject): Boolean = !obj.locked ||
        inventoryFor(id).any { it.optString("description").contains("[KEY:${obj.keyId.ifBlank { obj.markerId }}]") }

    private fun deliver(id: String, operation: String, data: JSONObject, label: String,
                        chestMarkerId: String = "", chestItemId: String = "") {
        val peer = peers.firstOrNull { it.id == id } ?: return
        val characterId = charId(id)
        if (characterId <= 0L) return
        val deliveryId = UUID.randomUUID().toString()
        val message = JSONObject().put("type", "adventure_delivery")
            .put("deliveryId", deliveryId).put("characterId", characterId)
            .put("operation", operation)
        data.keys().forEach { key -> message.put(key, data.get(key)) }
        pendingDeliveries[deliveryId] = PendingDelivery(id, label, chestMarkerId, chestItemId)
        broadcast.execute { try { peer.send(message.toString()) } catch (_: Exception) { } }
    }

    fun adventureCommand(action: String, markerId: String = "", targetId: String = "",
                         itemIds: List<Long> = emptyList(), cart: Map<String, Int> = emptyMap()) {
        val data = JSONObject().put("type", "adventure_action").put("action", action)
            .put("markerId", markerId).put("targetId", targetId)
            .put("items", JSONArray(itemIds))
            .put("cart", JSONArray().apply { cart.forEach { (id, quantity) ->
                put(JSONObject().put("id", id).put("quantity", quantity))
            } })
        if (hostMode) handleAdventureAction("host", data)
        else {
            val writer = outgoing ?: return
            outbound.execute { try { synchronized(writer) {
                writer.write(data.toString()); writer.newLine(); writer.flush()
            } } catch (_: Exception) { } }
        }
    }

    /**
     * Create an exit on the target map for every entrance/portal the first time
     * it is used. Keep the same marker ID so master edits (including movement)
     * survive subsequent visits and reconnects. Exits never spawn another exit.
     */
    private fun ensureReturnExit(source: AdventureObject) {
        if (isAutoReturnMarker(source.markerId) || source.targetMapId !in roomMaps ||
            source.mapId !in roomMaps) return
        val sourceMarker = markerSets[source.mapId]?.get(source.markerId) ?: return
        val exitId = AUTO_RETURN_MARKER_PREFIX + source.markerId
        val targetMarkers = markerSets.getOrPut(source.targetMapId) { linkedMapOf() }
        if (exitId in targetMarkers) return
        targetMarkers[exitId] = LanMarker(exitId, "↩ Выход", "Вернуться: ${roomMaps[source.mapId]?.info?.name ?: "предыдущая карта"}",
            "Дверь", source.x.coerceIn(0f,1f), source.y.coerceIn(0f,1f), "host")
        adventure = adventure.copy(objects = adventure.objects.filterNot { it.markerId == exitId } +
            AdventureObject(exitId, source.targetMapId, "Дверь", targetMapId = source.mapId,
                x = sourceMarker.x, y = sourceMarker.y))
        val saved = targetMarkers.values.toList()
        LanRoomStorage.saveRoomMarkers(appContext, roomId, source.targetMapId, saved)
        update { old -> old.copy(
            roomMarkers = markerSnapshots(),
            markers = if (old.activeMapId == source.targetMapId) saved else old.markers
        ) }
        val packet = markersMessage(source.targetMapId)
        broadcast.execute { peers.forEach { peer -> try { peer.send(packet) } catch (_: Exception) {} } }
        log("Мастер", "Создан обратный выход на карте «${roomMaps[source.targetMapId]?.info?.name}»")
    }

    /** Personal arrival does not change the host's camera or everybody else's map. */
    private fun travelPlayer(playerId: String, destination: String, x: Float, y: Float,
                             source: AdventureObject? = null) {
        if (source != null) ensureReturnExit(source)
        val player = players[playerId] ?: return
        val target = roomMaps[destination] ?: return
        val fog = fogByMap[destination] ?: LanFog()
        if (fog.enabled && !fog.visible(x, y)) {
            // Reveal only the landing point; keep all previously explored/hidden cells intact.
            val safeArrival = fog.brush(x, y, erase = true, radius = 0.05f)
            fogByMap[destination] = safeArrival
            LanWorldStorage.saveFog(appContext, roomId, destination, safeArrival)
        }
        players[playerId] = player.copy(mapId = destination, x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f))
        positions.getOrPut(destination) { linkedMapOf() }[playerId] = x to y
        val peer = peers.firstOrNull { it.id == playerId } ?: return
        broadcast.execute { try {
            peer.send(JSONObject().put("type", "map_select").put("id", destination).toString())
            peer.send(markersMessage(destination))
            peer.send(fogPacket(destination))
        } catch (_: Exception) {} }
    }

    private fun resolveVote() {
        val vote = adventure.vote ?: return
        if (vote.no.isNotEmpty()) { adventure = adventure.copy(vote = null, notice = "Групповой переход отклонён") ; return }
        if (!vote.voters.all { it in vote.yes }) return
        val obj = adventure.objects.firstOrNull { it.markerId == vote.markerId } ?: return
        val stillFar = vote.voters.filterNot { id -> playerNearObject(id, obj) || id in vote.remoteNeedsScroll }
        if (stillFar.isNotEmpty()) {
            adventure = adventure.copy(notice = "Не все участники рядом. Дальние игроки могут применить свиток телепортации")
            return
        }
        vote.voters.forEach { travelPlayer(it, vote.targetMapId, obj.x, obj.y, obj) }
        adventure = adventure.copy(vote = null, notice = "Группа переместилась на другую карту")
        publishPlayers()
    }

    /** Called by both local master buttons and socket messages. */
    private fun handleAdventureAction(actor: String, data: JSONObject) {
        if (!hostMode || closed) return
        val action = data.optString("action")
        val markerId = data.optString("markerId")
        val target = data.optString("targetId")
        synchronized(lock) {
            peerNotices.remove(actor)
            val obj = adventure.objects.firstOrNull { it.markerId == markerId }
            val player = players[actor]
            fun refuse(reason: String) {
                if (actor != "host") peerNotices[actor] = reason.take(180)
                else adventure = adventure.copy(notice = reason.take(180))
            }
            when (action) {
                "open_chest" -> {
                    if (obj?.kind != "Сундук" || !playerNearObject(actor, obj)) return
                    if (!hasKey(actor, obj)) { refuse("Сундук заперт — нужен ключ"); publishAdventure(false); return }
                    openChests[actor] = markerId
                    adventure = adventure.copy(objects = adventure.objects.map {
                        if (it.markerId == markerId) it.copy(opened = true) else it
                    }, notice = "Сундук открыт")
                }
                "close_chest" -> openChests.remove(actor)
                "open_shop" -> {
                    if (obj == null || !obj.isShop() || !playerNearObject(actor, obj)) return
                    if (!hasKey(actor, obj)) {
                        refuse("Магазин заперт — нужен ключ"); publishAdventure(false); return
                    }
                    openShops[actor] = markerId
                }
                "close_shop" -> openShops.remove(actor)
                "shop_buy" -> {
                    if (obj == null || !obj.isShop() || openShops[actor] != markerId) return
                    if (!playerNearObject(actor, obj)) {
                        openShops.remove(actor)
                        refuse("Подойди ближе к магазину"); publishAdventure(false); return
                    }
                    if (!hasKey(actor, obj)) { refuse("Магазин заперт — нужен ключ"); publishAdventure(false); return }
                    val characterId = charId(actor)
                    if (characterId <= 0L) { refuse("Сначала выбери персонажа"); return }
                    if (adventure.pendingShop.any { it.characterId == characterId }) {
                        refuse("Предыдущая покупка ещё доставляется"); publishAdventure(false); return
                    }
                    val lines = data.optJSONArray("cart") ?: return
                    if (lines.length() !in 1..30) return
                    val order = linkedMapOf<String, Int>()
                    for (i in 0 until lines.length()) {
                        val entry = lines.optJSONObject(i) ?: return
                        val id = entry.optString("id")
                        val count = entry.optInt("quantity", 0)
                        if (id.isBlank() || id in order || count !in 1..99) return
                        order[id] = count
                    }
                    if (order.values.sum() > 100) return
                    val goods = order.map { (id, count) ->
                        val item = obj.items.firstOrNull { it.id == id }
                        if (item == null || item.stock < count || item.price <= 0) {
                            refuse("Товар закончился или больше не продаётся"); publishAdventure(false); return
                        }
                        item to count
                    }
                    val costLong = goods.sumOf { (item, count) -> item.price.toLong() * count }
                    val walletKey = characterId.toString()
                    val balance = adventure.wallets[walletKey] ?: 0
                    if (costLong <= 0L || costLong > balance) {
                        refuse("Недостаточно монет: нужно $costLong, доступно $balance"); publishAdventure(false); return
                    }
                    val purchased = goods.flatMap { (item, count) ->
                        List(count) { item.copy(id = UUID.randomUUID().toString(), stock = 1) }
                    }
                    val receipt = AdventurePurchase(UUID.randomUUID().toString(), characterId,
                        markerId, purchased, costLong.toInt())
                    adventure = adventure.copy(
                        wallets = adventure.wallets + (walletKey to (balance - costLong.toInt())),
                        objects = adventure.objects.map { record -> if (record.markerId != markerId) record
                            else record.copy(items = record.items.map { item ->
                                item.copy(stock = item.stock - (order[item.id] ?: 0))
                            }) },
                        pendingShop = adventure.pendingShop + receipt,
                        notice = "Покупка на ${receipt.cost} монет оформлена")
                    // Persist the debit, stock reservation and receipt before any network delivery.
                    AdventureStorage.save(appContext, roomId, adventure)
                    sendShopReceipt(actor, receipt)
                    log("Магазин", "Заказ на ${receipt.cost} монет от персонажа $characterId")
                }
                "take" -> {
                    if (obj?.kind != "Сундук" || openChests[actor] != markerId || !playerNearObject(actor,obj)) return
                    if (charId(actor) <= 0L) { refuse("Сначала выбери персонажа"); return }
                    val item = obj.items.firstOrNull { it.id == target } ?: return
                    if (pendingDeliveries.values.any { it.chestItemId == item.id && it.chestMarkerId == markerId }) return
                    deliver(actor, "add", JSONObject().put("item", item.json()), "Предмет ${item.name}", markerId, item.id)
                    adventure = adventure.copy(notice = "Передача предмета: ${item.name}")
                }
                "enter", "group_vote" -> {
                    if (obj == null || obj.kind !in setOf("Дверь", "Дом", "Портал", "Магазин") || !playerNearObject(actor,obj)) return
                    if (!hasKey(actor,obj)) { refuse("Дверь закрыта. Необходим ключ"); publishAdventure(false); return }
                    if (obj.isShop()) { refuse("Здесь магазин. Нажми «Торговать»"); return }
                    if (obj.targetMapId !in roomMaps) { refuse("Мастер ещё не назначил карту назначения"); publishAdventure(false); return }
                    if (action == "enter") {
                        travelPlayer(actor, obj.targetMapId, obj.x, obj.y, obj)
                        refuse("Переход выполнен")
                        publishPlayers()
                    } else if (adventure.vote == null && player != null) {
                        adventure = adventure.copy(vote = AdventureVote(obj.markerId, obj.mapId, actor,
                            obj.targetMapId, players.keys.toList(), yes = setOf(actor)), notice = "Предложен групповой переход")
                        resolveVote()
                    }
                }
                "vote_yes", "vote_no" -> {
                    val vote = adventure.vote ?: return
                    if (actor !in vote.voters || actor in vote.yes || actor in vote.no) return
                    adventure = adventure.copy(vote = if (action == "vote_yes") vote.copy(yes = vote.yes + actor)
                        else vote.copy(no = vote.no + actor))
                    resolveVote()
                }
                "use_scroll" -> {
                    val vote = adventure.vote ?: return
                    if (actor !in vote.yes || actor in vote.remoteNeedsScroll) return
                    val source = adventure.objects.firstOrNull { it.markerId == vote.markerId } ?: return
                    if (playerNearObject(actor, source)) return
                    val key = charId(actor).takeIf { it > 0L }?.toString() ?: return
                    val used = adventure.scrollUses[key] ?: 0
                    if (used >= 2) { refuse("Свиток телепортации использован дважды за день"); publishAdventure(false); return }
                    adventure = adventure.copy(scrollUses = adventure.scrollUses + (key to used + 1),
                        vote = vote.copy(remoteNeedsScroll = vote.remoteNeedsScroll + actor))
                    resolveVote()
                }
                "trade_offer" -> {
                    if (player == null || target == actor || target !in players || adventure.trade != null) return
                    val selected = data.optJSONArray("items") ?: JSONArray()
                    val ids = (0 until selected.length()).map { selected.optLong(it) }.distinct().take(20)
                    if (ids.isEmpty() || ids.any { id -> inventoryFor(actor).none {
                        it.optLong("id") == id && it.optString("name") != "Журнал приключений"
                    } }) return
                    if (ids.any { id -> inventoryFor(actor).any { it.optLong("id") == id && it.optString("name") == "Журнал приключений" } }) return
                    adventure = adventure.copy(trade = AdventureTrade(proposer = actor, recipient = target, proposerItems = ids,
                        proposerNames = inventoryFor(actor).filter { it.optLong("id") in ids }.map { it.optString("name").take(100) }))
                }
                "trade_counter" -> {
                    val trade = adventure.trade ?: return
                    if (actor != trade.recipient) return
                    val array = data.optJSONArray("items") ?: JSONArray()
                    val ids = (0 until array.length()).map { array.optLong(it) }.distinct().take(20)
                    if (ids.any { id -> inventoryFor(actor).none { it.optLong("id") == id && it.optString("name") != "Журнал приключений" } }) return
                    adventure = adventure.copy(trade = trade.copy(recipientItems = ids,
                        recipientNames = inventoryFor(actor).filter { it.optLong("id") in ids }.map { it.optString("name").take(100) },
                        proposerConfirmed = false, recipientConfirmed = false))
                }
                "trade_confirm" -> {
                    val trade = adventure.trade ?: return
                    if (actor != trade.proposer && actor != trade.recipient) return
                    val updated = if (actor == trade.proposer) trade.copy(proposerConfirmed = true)
                        else trade.copy(recipientConfirmed = true)
                    adventure = adventure.copy(trade = updated)
                    if (updated.proposerConfirmed && updated.recipientConfirmed) {
                        val a = inventoryFor(trade.proposer)
                        val b = inventoryFor(trade.recipient)
                        val offerA = a.filter { it.optLong("id") in trade.proposerItems }
                        val offerB = b.filter { it.optLong("id") in trade.recipientItems }
                        if (offerA.size != trade.proposerItems.size || offerB.size != trade.recipientItems.size ||
                            (offerA + offerB).any { it.optString("name") == "Журнал приключений" }) {
                            adventure = adventure.copy(trade = null, notice = "Обмен отменён: предметы изменились")
                        } else {
                            // Each receiver gets a fresh unique local ID to avoid collisions.
                            fun exchanged(base: List<JSONObject>, leaving: List<Long>, arrivals: List<JSONObject>): JSONObject =
                                JSONObject().put("expectedIds", JSONArray(leaving))
                                    .put("items", JSONArray().apply {
                                        base.filter { it.optLong("id") !in leaving }.forEach { put(it) }
                                        arrivals.forEach { item -> put(JSONObject(item.toString())
                                            .put("id", UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)) }
                                    })
                            deliver(trade.proposer, "replace", exchanged(a, trade.proposerItems, offerB), "Обмен")
                            deliver(trade.recipient, "replace", exchanged(b, trade.recipientItems, offerA), "Обмен")
                            adventure = adventure.copy(trade = null, notice = "Обмен подтверждён")
                        }
                    }
                }
                "trade_cancel" -> {
                    val trade = adventure.trade ?: return
                    if (actor in setOf(trade.proposer, trade.recipient, "host"))
                        adventure = adventure.copy(trade = null, notice = "Обмен отменён")
                }
                "camp" -> {
                    if (player == null || adventure.phase != "day" || adventure.campMarkerId.isNotBlank()) return
                    val group = players.values.toList()
                    if (group.isEmpty() || group.any { it.mapId != player.mapId ||
                        kotlin.math.hypot(it.x - player.x, it.y - player.y) > LAN_INTERACTION_RADIUS }) {
                        refuse("Для лагеря вся группа должна собраться рядом"); publishAdventure(false); return
                    }
                    val id = UUID.randomUUID().toString()
                    val camp = LanMarker(id, "Лагерь", "Место отдыха группы", "Лагерь", player.x, player.y, "host")
                    markerSets.getOrPut(player.mapId) { linkedMapOf() }[id] = camp
                    adventure = adventure.copy(campMarkerId = id, campMapId = player.mapId, notice = "Лагерь разбит")
                    LanRoomStorage.saveRoomMarkers(appContext, roomId, player.mapId, markerSets[player.mapId]!!.values.toList())
                    broadcast.execute { peers.forEach { try { it.send(markersMessage(player.mapId)) } catch (_: Exception) {} } }
                    update { it.copy(markers = if (it.activeMapId == player.mapId) markerSets[player.mapId]!!.values.toList() else it.markers,
                        roomMarkers = markerSnapshots()) }
                }
                "end_day" -> {
                    if (actor != "host" || adventure.campMarkerId.isBlank() || adventure.phase != "day") return
                    adventure = adventure.copy(phase = "night", notice = "Ночь. Группа отдыхает в лагере")
                }
                "start_day" -> {
                    if (actor != "host" || adventure.phase != "night") return
                    val campMap = adventure.campMapId
                    markerSets[campMap]?.remove(adventure.campMarkerId)
                    if (campMap.isNotBlank()) {
                        LanRoomStorage.saveRoomMarkers(appContext, roomId, campMap,
                            markerSets[campMap]?.values?.toList() ?: emptyList())
                        broadcast.execute { peers.forEach { try { it.send(markersMessage(campMap)) } catch (_: Exception) {} } }
                    }
                    adventure = adventure.copy(day = adventure.day + 1, phase = "day",
                        campMarkerId = "", campMapId = "", scrollUses = emptyMap(),
                        notice = "Начался новый день. Здоровье и способности восстановлены")
                    peers.forEach { peer ->
                        val cid = charId(peer.id)
                        if (cid > 0) editCharacterAsMaster(peer.id, cid, "adventure_rest", JSONObject(), "Восстановление после отдыха")
                    }
                    update { it.copy(roomMarkers = markerSnapshots(), markers = markerSets[activeMapId]?.values?.toList() ?: emptyList()) }
                }
                else -> return
            }
            Unit
        }
        publishAdventure()
    }

    fun giveHeroStory(playerId: String, positiveName: String, positive: String,
                      negativeName: String, negative: String): Boolean {
        if (!hostMode || adventure.phase != "night" || adventure.campMarkerId.isBlank()) return false
        val id = charId(playerId)
        if (id <= 0 || listOf(positiveName, negativeName, positive, negative).any { it.isBlank() }) return false
        val payload = JSONObject().put("goodName", positiveName).put("goodEffect", positive)
            .put("badName", negativeName).put("badEffect", negative)
        if (!editCharacterAsMaster(playerId, id, "hero_story_pair", payload,
                "Выдал/обновил дар и недостаток истории героя")) return false
        // This flag still marks the story as told. It no longer blocks an explicit
        // master-triggered replay from the World panel.
        adventure = adventure.copy(heroStories = adventure.heroStories + id.toString())
        publishAdventure()
        return true
    }

    fun close() {
        if (hostMode) AdventureStorage.save(appContext, roomId, adventure)
        closed = true
        try { server?.close() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        peers.forEach { try { it.socket.close() } catch (_: Exception) {} }
        peers.clear()
        outbound.shutdownNow()
        broadcast.shutdownNow()
    }
}
