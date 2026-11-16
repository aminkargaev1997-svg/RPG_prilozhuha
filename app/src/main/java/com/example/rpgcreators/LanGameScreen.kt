package com.example.rpgcreators

import android.graphics.BitmapFactory
import android.net.Uri
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.min
import kotlin.math.max
import java.util.UUID

@Composable
fun LanGameScreen(
    modifier: Modifier = Modifier,
    characters: List<Character>,
    onBack: () -> Unit,
    isMaster: Boolean = false,
    onCharacterUpdate: (Character) -> Unit = {},
    preselectedCharacterId: Long? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val maps = remember {
        mutableStateListOf<GameMap>().apply { addAll(CharacterStorage.loadMaps(context)) }
    }
    var selectedCharacterId by remember { mutableStateOf<Long?>(null) }
    var selectedMapId by remember { mutableStateOf(maps.firstOrNull()?.id) }
    var ip by remember { mutableStateOf("") }
    val discovery = remember(context) { LanRoomDiscovery(context) }
    var discoveredRooms by remember { mutableStateOf<List<DiscoveredLanRoom>>(emptyList()) }
    var showManualIp by remember { mutableStateOf(false) }
    var discoveringRooms by remember { mutableStateOf(true) }
    val providedGuestCharacter = characters.firstOrNull { it.id == preselectedCharacterId }
    var guestName by remember(preselectedCharacterId) {
        mutableStateOf(providedGuestCharacter?.name ?: characters.firstOrNull()?.name ?: "Игрок")
    }
    var mode by remember { mutableStateOf(if (isMaster) "host" else "guest") }
    var showPermissions by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showExperience by remember { mutableStateOf(false) }
    var xpInput by remember { mutableStateOf("") }
    var movingPlayerId by remember { mutableStateOf<String?>(null) }
    var started by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf("map") }
    var journalReturnPage by remember { mutableStateOf("map") }
    var fullscreen by remember { mutableStateOf(false) }
    var mapCombatExpanded by remember { mutableStateOf(false) }
    var mapCombatDialog by remember { mutableStateOf(false) }
    LaunchedEffect(fullscreen) {
        if (!fullscreen) mapCombatDialog = false
    }
    // Camera belongs to the viewport, not to world coordinates. All map children
    // (markers, players, fog) are drawn in the same local, unscaled map space.
    var cameraZoom by remember { mutableFloatStateOf(1f) }
    var cameraPan by remember { mutableStateOf(Offset.Zero) }

    var showRoomInfo by remember { mutableStateOf(false) }
    var moveMarkerId by remember { mutableStateOf<String?>(null) }
    var movingOwnPlayer by remember { mutableStateOf(false) }
    var previewPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var lastMoveSentAt by remember { mutableLongStateOf(0L) }
    var selectedPlayer by remember { mutableStateOf<LanPlayer?>(null) }
    var observingPlayerId by remember { mutableStateOf<String?>(null) }
    var observationTab by remember { mutableStateOf("character") }
    var selectedMarker by remember { mutableStateOf<LanMarker?>(null) }
    var editingCreatureHealth by remember { mutableStateOf<LanMarker?>(null) }
    var creatureMaxInput by remember { mutableStateOf("") }
    var creatureCurrentInput by remember { mutableStateOf("") }

    var adventureEditor by remember { mutableStateOf<LanMarker?>(null) }
    var chestId by remember { mutableStateOf<String?>(null) }
    var shopId by remember { mutableStateOf<String?>(null) }
    var showWalletEditor by remember { mutableStateOf(false) }
    var tradeTargetId by remember { mutableStateOf<String?>(null) }
    var tradeSent by remember { mutableStateOf(false) }
    var tradeSeen by remember { mutableStateOf(false) }
    var heroStoryPlayerId by remember { mutableStateOf<String?>(null) }
    var adventureNotice by remember { mutableStateOf("") }
    var editMarker by remember { mutableStateOf<LanMarker?>(null) }
    var detectionZoneEdit by remember { mutableStateOf<LanMarker?>(null) }
    var zoneRadiusX by remember { mutableFloatStateOf(0.035f) }
    var zoneRadiusY by remember { mutableFloatStateOf(0.035f) }
    var fogEditing by remember { mutableStateOf(false) }
    var fogErase by remember { mutableStateOf(true) }
    var lastFogBrushAt by remember { mutableLongStateOf(0L) }
    var npcLineInput by remember { mutableStateOf("") }
    var addingMarker by remember { mutableStateOf(false) }
    var pendingPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var markerName by remember { mutableStateOf("") }
    var markerDescription by remember { mutableStateOf("") }
    var markerType by remember { mutableStateOf("Обычная") }
    var markerHidden by remember { mutableStateOf(false) }
    var showMarkerCatalog by remember { mutableStateOf(false) }
    var mapBeingAdded by remember { mutableStateOf<Uri?>(null) }
    var newMapName by remember { mutableStateOf("") }
    var showAddMapChooser by remember { mutableStateOf(false) }
    var roomMapMenu by remember { mutableStateOf(false) }
    // A small handle remains visible; selecting a section replaces the tools in the sidebar.
    var masterToolsOpen by remember { mutableStateOf(false) }
    var masterSectionMenu by remember { mutableStateOf(true) }
    var masterToolsSection by remember { mutableStateOf("maps") }
    var mapForRoomRemoval by remember { mutableStateOf<LanRoomMapInfo?>(null) }
    var loadingMap by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var selectedGuestCharacterId by remember(preselectedCharacterId) {
        mutableStateOf(providedGuestCharacter?.id ?: characters.firstOrNull()?.id)
    }
    val activeCharacter = characters.firstOrNull {
        it.id == (if (mode == "host") selectedCharacterId else selectedGuestCharacterId)
    }
    var state by remember { mutableStateOf(LanState()) }
    var masterEditRevision by remember { mutableIntStateOf(0) }
    val latestCharacterUpdate = rememberUpdatedState(onCharacterUpdate)
    val session = remember(context) {
        LanSession(context.applicationContext, onState = { state = it }, onMasterEdit = { edit ->
            val applied = applyLanMasterEdit(context, edit) { updated ->
                latestCharacterUpdate.value(updated)
            }
            if (applied) masterEditRevision++
            applied
        })
    }
    val uploadRoomMap: (GameMap, Boolean) -> Unit = { map, startRoom ->
        if (!loadingMap) {
            loadingMap = true
            scope.launch {
                try {
                    val bytes = LanRoomStorage.readImage(context, map.imageUri)
                    val initialMarkers = activeCharacter?.let { owner ->
                        CharacterStorage.loadCharacterMapMarkers(context, owner.id, map.id)
                            .filter { it.id != LanRoomStorage.playerPositionId() }.map { marker ->
                            LanMarker(UUID.randomUUID().toString(), marker.name, marker.description,
                                marker.type, marker.x, marker.y, "host")
                        }
                    } ?: emptyList()
                    if (startRoom) {
                        started = true
                        session.host("Мастер", LanRoomStorage.stableRoomMapKey(map.name, bytes), map.name, bytes, initialMarkers)
                    } else if (!withContext(Dispatchers.IO) {
                            session.addMap(LanRoomStorage.stableRoomMapKey(map.name, bytes), map.name, bytes, initialMarkers)
                        }) {
                        error("Карта уже в комнате или достигнут лимит $LAN_MAX_ROOM_MAPS карт")
                    }
                } catch (error: Exception) {
                    mapError = error.message ?: "Не удалось добавить карту"
                    if (startRoom) started = false
                } finally { loadingMap = false }
            }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
            newMapName = ""
            mapBeingAdded = uri
        }
    }
    val activity = remember(context) {
        var current: Context = context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        current as? Activity
    }
    // The network map is the only landscape-only screen. Restoring portrait
    // explicitly matters on devices with auto-rotate switched off.
    DisposableEffect(fullscreen, page, activity) {
        val view = activity?.window?.decorView
        val previousUi = view?.systemUiVisibility
        if (fullscreen && page == "map") {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            view?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            view?.systemUiVisibility = 0
        }
        onDispose {
            // Returning from fullscreen or leaving LAN should never strand the
            // ordinary character screens in landscape orientation.
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            view?.systemUiVisibility = if (fullscreen && page == "map") 0 else (previousUi ?: 0)
        }
    }
    // Discovery is active only before joining; the master advertises while hosting.
    DisposableEffect(discovery, started, mode, state.connected, discoveringRooms) {
        if (mode == "host" && state.connected) {
            discovery.advertise("RPG Creators — мастер")
        } else if (mode != "host" && !started && discoveringRooms) {
            discovery.discover { discoveredRooms = it }
        } else {
            discovery.stop()
        }
        onDispose { discovery.stop() }
    }
    DisposableEffect(discovery) { onDispose { discovery.stop() } }
    DisposableEffect(session) { onDispose { session.close() } }

    LaunchedEffect(state.adventure.notice) {
        val notice = state.adventure.notice
        if (notice.contains("нужен ключ", true) || notice.contains("закрыта", true) ||
            notice.contains("не назначил", true) || notice.contains("дважды", true) ||
            notice.contains("Не все участники", true) ||
            notice.contains("Недостаточно монет", true) ||
            notice.contains("доставляется", true)) adventureNotice = notice
    }
    LaunchedEffect(state.adventure.trade?.id) {
        if (state.adventure.trade != null) tradeSeen = true
        else if (tradeSeen) {
            tradeTargetId = null
            tradeSent = false
            tradeSeen = false
        }
    }

    val bitmap = remember(state.image) {
        state.image?.let { bytes ->
            try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            catch (_: Exception) { null }
        }
    }

    // Each player's phone keeps an offline network copy of every received map.
    // Markers stay separate for each character, including the last player position.
    LaunchedEffect(state.roomMaps, state.roomMarkers, state.players, state.activeMapId,
        state.myId, activeCharacter?.id, state.roomId) {
        val character = activeCharacter ?: return@LaunchedEffect
        if (state.roomId.isBlank()) return@LaunchedEffect
        delay(180)
        val snapshots = state.roomMaps.mapNotNull { info ->
            val sharedMarkers = state.roomMarkers[info.id] ?: return@mapNotNull null
            Triple(info, sharedMarkers, if (info.id == state.activeMapId)
                state.players.firstOrNull { it.id == state.myId } else null)
        }
        withContext(Dispatchers.IO) {
            snapshots.forEach { (info, sharedMarkers, ownPosition) ->
                val markers = sharedMarkers.map { item ->
                    MapMarker(
                        id = LanRoomStorage.markerId(state.roomId, info.id, item.id),
                        name = item.name, description = item.description,
                        type = item.type, x = item.x, y = item.y
                    )
                }.toMutableList()
                val old = CharacterStorage.loadCharacterMapMarkers(context, character.id, info.localMapId)
                    .firstOrNull { it.id == LanRoomStorage.playerPositionId() }
                val lastPosition = ownPosition?.let { position ->
                    MapMarker(id = LanRoomStorage.playerPositionId(),
                        name = "Последняя позиция: ${character.name}",
                        description = "Место персонажа в сетевой игре", type = "Игрок",
                        x = position.x, y = position.y)
                } ?: old
                if (lastPosition != null) markers.add(lastPosition)
                CharacterStorage.saveCharacterMapMarkers(context, character.id, info.localMapId, markers)
            }
        }
    }
    // Mirror purchases and master grants from the authoritative campaign wallet.
    // Wait for the host to acknowledge the character's local currency ledger first,
    // otherwise the initial empty wallet packet could overwrite pending coin deposits.
    LaunchedEffect(state.adventure.wallets, state.adventure.walletLedgerCopper,
        state.connected, activeCharacter?.id, isMaster) {
        val id = activeCharacter?.id ?: return@LaunchedEffect
        if (!isMaster && state.connected) {
            val key = id.toString()
            val hostGold = state.adventure.wallets[key] ?: return@LaunchedEffect
            val hostLedger = state.adventure.walletLedgerCopper[key] ?: 0L
            if (hostLedger == CharacterStorage.walletLedgerCopper(context, id)) {
                CharacterStorage.mirrorHostWallet(context, id, hostGold)
            }
        }
    }

    // Keep each player's most recently received campaign journal available offline.
    LaunchedEffect(state.questsVersion, state.roomId, activeCharacter?.id) {
        if (!isMaster && state.questsVersion > 0 && state.roomId.isNotBlank() && activeCharacter != null) {
            RpgQuestStorage.save(context,
                RpgQuestStorage.clientKey(state.roomId, activeCharacter.id), state.quests)
        }
    }
    LaunchedEffect(state.permissions.skillTreeEnabled) {
        if (!state.permissions.skillTreeEnabled && page == "talents") page = "map"
    }
    LaunchedEffect(state.activeMapId) {
        // Changing maps ends any temporary host control.
        if (isMaster) session.setMasterMovementTarget(null)
        movingPlayerId = null
        movingOwnPlayer = false
        moveMarkerId = null
        previewPosition = null
        selectedMarker = null
        if (!isMaster && shopId != null) session.adventureCommand("close_shop")
        shopId = null
        chestId = null
        pendingPoint = null
        addingMarker = false
        fogEditing = false
    }
    LaunchedEffect(state.image) {
        if (state.image == null) fullscreen = false
    }

    val currentCharacter by rememberUpdatedState(activeCharacter)
    // Send fresh local character state periodically while connected. This also captures
    // changes made by existing screens that persist directly to CharacterStorage.
    LaunchedEffect(state.connected, state.myId, activeCharacter?.id, isMaster) {
        if (!isMaster && state.connected && state.myId.isNotBlank()) {
            var lastPayload = ""
            while (true) {
                val character = currentCharacter
                if (character != null) {
                    val latest = CharacterStorage.loadCharacters(context).firstOrNull { it.id == character.id } ?: character
                    val data = JSONObject().apply {
                        put("id", latest.id); put("autoExperience", latest.autoExperience)
                        put("name", latest.name); put("race", latest.race)
                        put("profession", latest.profession); put("level", latest.level)
                        put("experience", latest.experience)
                        put("experienceRequired", latest.experienceRequired)
                        put("prestigePoints", latest.prestigePoints)
                        put("initiativeBonus", DndSheetStorage.load(context, latest.id).initiativeBonus)
                        put("health", AdventureVitalsStorage.currentHealth(context, latest.id))
                        put("healthMax", AdventureVitalsStorage.effectiveMaxHealth(context, latest.id))
                        val talentState = SkillTreeStorage.load(context, latest.id)
                        put("tree", JSONObject().put("nodes", JSONArray(talentState.unlocked.toList()))
                            .put("granted", talentState.grantedPoints))
                        put("inventory", JSONArray().apply {
                            CharacterStorage.loadInventory(context, latest.id).forEach { item ->
                                put(JSONObject().put("id", item.id).put("name", item.name).put("quantity", item.quantity)
                                    .put("description", item.description).put("category", item.category)
                                    .put("effects", ItemEffectEngine.toJson(item.effects)))
                            }
                        })
                        put("abilities", JSONArray().apply {
                            CharacterStorage.loadAbilities(context, latest.id).forEach { ability ->
                                put(JSONObject().put("id", ability.id).put("name", ability.name).put("description", ability.description)
                                    .put("activator", ability.activatorDescription)
                                    .put("passive", ability.passiveDescription).put("used", ability.isUsed)
                                    .put("origin", ability.origin).put("spellLevel", ability.spellLevel))
                            }
                        })
                        val allAttributes = linkedMapOf(
                            "Сила" to 10, "Ловкость" to 10, "Телосложение" to 10,
                            "Интеллект" to 10, "Мудрость" to 10, "Харизма" to 10
                        )
                        allAttributes.putAll(CharacterStorage.loadAttributes(context, latest.id))
                        put("attributes", JSONObject(allAttributes))
                        put("equipment", JSONObject(CharacterStorage.loadEquipment(context, latest.id)))
                        put("walletLedgerCopper", CharacterStorage.walletLedgerCopper(context, latest.id))
                    }.toString()
                    if (data != lastPayload) { session.sendCharacterSnapshot(data); lastPayload = data }
                }
                delay(900)
            }
        }
    }

    if (isMaster && observingPlayerId != null) {
        val playerId = observingPlayerId!!
        val observed = state.players.firstOrNull { it.id == playerId }
        val payload = state.characterSnapshots.firstOrNull { it.ownerId == playerId }?.payload
        val snapshot = remember(payload) {
            try { payload?.let { JSONObject(it) } } catch (_: Exception) { null }
        }
        MasterObservationDialog(
            playerId = playerId,
            playerName = observed?.name ?: "Игрок",
            snapshot = snapshot,
            initialTab = observationTab,
            onClose = { observingPlayerId = null },
            onEdit = { operation, data, description ->
                val characterId = snapshot?.optLong("id", -1L) ?: -1L
                session.editCharacterAsMaster(playerId, characterId, operation, data, description)
            }
        )
    }

    if (showRoomInfo) {
        AlertDialog(
            onDismissRequest = { showRoomInfo = false },
            title = { Text("Информация о комнате") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Все участники должны быть в одной Wi-Fi сети или точке доступа.")
                    Text(state.status)
                    if (mode == "host" && started) {
                        Text("Сервер: ${session.listeningAddress()}")
                        val suggestedIp = localLanIp()
                        if (suggestedIp != "Не определён") {
                            Text("Возможный адрес мастера: $suggestedIp:$LAN_PORT")
                        } else {
                            Text("IP точки доступа не удалось определить автоматически.")
                        }
                        Text("При раздаче Wi-Fi с телефона мастера проверь поле «Роутер» в настройках Wi-Fi телефона игрока. Это обычно адрес мастера.")
                        Text("0.0.0.0 — адрес прослушивания сервера, вводить его игроку нельзя.")
                    } else if (mode == "guest" && started) {
                        Text("Хост: $ip:$LAN_PORT")
                        Text("Маршрут игрока: ${session.connectionTransport()}")
                    }
                    Text("Локальные IPv4-интерфейсы:", fontWeight = FontWeight.Bold)
                    Text(lanInterfacesReport(), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showRoomInfo = false }) { Text("Закрыть") } }
        )
    }

    mapError?.let { error ->
        AlertDialog(
            onDismissRequest = { mapError = null },
            title = { Text("Не удалось открыть карту") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { mapError = null }) { Text("Понятно") } }
        )
    }

    mapBeingAdded?.let { uri ->
        AlertDialog(
            onDismissRequest = { mapBeingAdded = null },
            title = { Text("Добавить карту") },
            text = {
                OutlinedTextField(value = newMapName, onValueChange = { newMapName = it.take(80) },
                    label = { Text("Название карты") }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = newMapName.isNotBlank() && !loadingMap, onClick = {
                    val map = GameMap(name = newMapName.trim(), imageUri = uri.toString())
                    maps.add(map)
                    CharacterStorage.saveMaps(context, maps.toList())
                    selectedMapId = map.id
                    mapBeingAdded = null
                    if (started && state.connected && isMaster) uploadRoomMap(map, false)
                }) { Text(if (started) "Добавить в комнату" else "Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { mapBeingAdded = null }) { Text("Отмена") }
            }
        )
    }

    if (showAddMapChooser && isMaster && state.connected) {
        val sourceMaps = maps.filter { source ->
            state.roomMaps.none { it.localMapId == source.id || it.name == source.name }
        }
        AlertDialog(
            onDismissRequest = { showAddMapChooser = false },
            title = { Text("Новая карта комнаты") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        showAddMapChooser = false
                        imagePicker.launch(arrayOf("image/*"))
                    }, modifier = Modifier.fillMaxWidth()) { Text("🖼 Загрузить фото") }
                    Text("Или выбери карту из библиотеки:")
                    if (sourceMaps.isEmpty()) Text("Других карт пока нет")
                    sourceMaps.forEach { map ->
                        OutlinedButton(
                            enabled = !loadingMap,
                            onClick = {
                                showAddMapChooser = false
                                uploadRoomMap(map, false)
                            }, modifier = Modifier.fillMaxWidth()
                        ) { Text(map.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddMapChooser = false }) { Text("Закрыть") } }
        )
    }

    mapForRoomRemoval?.let { map ->
        AlertDialog(
            onDismissRequest = { mapForRoomRemoval = null },
            title = { Text("Убрать карту из комнаты?") },
            text = { Text("«${map.name}» исчезнет из текущей сетевой игры у всех игроков. Сохранённая карта и локальные метки на телефонах останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    if (!session.removeMap(map.id)) mapError = "Не удалось удалить карту из комнаты"
                    mapForRoomRemoval = null
                }) { Text("Удалить из комнаты", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { mapForRoomRemoval = null }) { Text("Отмена") }
            }
        )
    }

    if (showExperience && !isMaster && activeCharacter != null) {
        AlertDialog(
            onDismissRequest = { showExperience = false },
            title = { Text("Добавить опыт") },
            text = { OutlinedTextField(value = xpInput,
                onValueChange = { xpInput = it.filter(Char::isDigit).take(9) },
                label = { Text("Опыт") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = (xpInput.toIntOrNull() ?: 0) > 0, onClick = {
                    val amount = xpInput.toIntOrNull() ?: 0
                    val updated = activeCharacter.addExperience(amount,
                        SkillTreeStorage.load(context, activeCharacter.id).grantedPoints)
                    onCharacterUpdate(updated)
                    session.sendEvent("Получил $amount XP, уровень ${activeCharacter.level} → ${updated.level}")
                    xpInput = ""
                    showExperience = false
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { showExperience = false }) { Text("Отмена") } }
        )
    }

    if (showPermissions && isMaster) {
        AlertDialog(
            onDismissRequest = { showPermissions = false },
            title = { Text("Разрешения игроков") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Создавать метки", Modifier.weight(1f))
                        Switch(checked = state.permissions.createMarkers,
                            onCheckedChange = { session.setPermissions(state.permissions.copy(createMarkers = it)) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Двигать свои метки", Modifier.weight(1f))
                        Switch(checked = state.permissions.moveOwnMarkers,
                            onCheckedChange = { session.setPermissions(state.permissions.copy(moveOwnMarkers = it)) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Удалять свои метки", Modifier.weight(1f))
                        Switch(checked = state.permissions.deleteOwnMarkers,
                            onCheckedChange = { session.setPermissions(state.permissions.copy(deleteOwnMarkers = it)) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Использовать древо навыков", Modifier.weight(1f))
                        Switch(checked = state.permissions.skillTreeEnabled,
                            onCheckedChange = { session.setPermissions(state.permissions.copy(skillTreeEnabled = it)) })
                    }
                    Text("Отключённое древо скрыто для игроков этой сетевой кампании; личные навыки не удаляются.", fontSize = 12.sp)
                    Text("Мастер может управлять всеми метками.", fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showPermissions = false }) { Text("Готово") } }
        )
    }
    if (showLogs && isMaster) {
        AlertDialog(
            onDismissRequest = { showLogs = false },
            title = { Text("Журнал комнаты") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.logs.isEmpty()) Text("Событий пока нет")
                    state.logs.asReversed().forEach { entry ->
                        Text("${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(entry.time))} · ${entry.actor}: ${entry.action}")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLogs = false }) { Text("Закрыть") } }
        )
    }

    if (adventureNotice.isNotBlank()) AlertDialog(
        onDismissRequest = { adventureNotice = "" },
        title = { Text("Взаимодействие") }, text = { Text(adventureNotice) },
        confirmButton = { TextButton(onClick = { adventureNotice = "" }) { Text("Понятно") } }
    )

    adventureEditor?.let { marker ->
        val record = state.adventure.objects.firstOrNull { it.markerId == marker.id }
        AdventureObjectEditor(marker = marker, mapId = state.activeMapId,
            maps = state.roomMaps, current = record,
            doors = state.adventure.objects.filter { it.kind in setOf("Дверь", "Дом", "Магазин") },
            onClose = { adventureEditor = null },
            onSave = { session.editAdventureObject(it); adventureEditor = null })
    }

    if (!isMaster && chestId != null) {
        val marker = state.markers.firstOrNull { it.id == chestId }
        val record = state.adventure.objects.firstOrNull { it.markerId == chestId }
        if (marker == null || record == null) {
            chestId = null
        } else if (state.adventure.openChestId != chestId) {
            AlertDialog(onDismissRequest = { chestId = null },
                title = { Text("Сундук: ${marker.name}") },
                text = { Text(state.adventure.notice.ifBlank { "Ожидание ответа мастера…" }) },
                confirmButton = { TextButton(onClick = { chestId = null }) { Text("Закрыть") } })
        } else AdventureChestDialog(marker.name, record.items,
            onTake = { id -> session.adventureCommand("take", marker.id, id) },
            onClose = { session.adventureCommand("close_chest"); chestId = null })
    }

    if (!isMaster && shopId != null && activeCharacter != null) {
        val marker = state.markers.firstOrNull { it.id == shopId }
        val record = state.adventure.objects.firstOrNull { it.markerId == shopId }
        if (marker == null || record == null || !record.isShop()) {
            shopId = null
        } else if (state.adventure.openShopId != shopId) {
            AlertDialog(onDismissRequest = { session.adventureCommand("close_shop"); shopId = null },
                title = { Text("Магазин: ${marker.name}") },
                text = { Text(state.adventure.notice.ifBlank { "Ожидаем подтверждения мастера…" }) },
                confirmButton = { TextButton(onClick = {
                    session.adventureCommand("close_shop"); shopId = null
                }) { Text("Закрыть") } })
        } else AdventureShopDialog(title = marker.name, goods = record.items,
            balance = state.adventure.wallets[activeCharacter.id.toString()] ?: 0,
            waitingDelivery = state.adventure.pendingShop.any { it.characterId == activeCharacter.id },
            onBuy = { cart -> session.adventureCommand("shop_buy", marker.id, cart = cart) },
            onClose = { session.adventureCommand("close_shop"); shopId = null })
    }

    if (isMaster && showWalletEditor) {
        val walletPlayers = state.players.mapNotNull { player ->
            val id = state.characterSnapshots.firstOrNull { it.ownerId == player.id }
                ?.payload?.let { runCatching { JSONObject(it).optLong("id", -1L) }.getOrNull() }
            if (id != null && id > 0L) Triple(player.id, player.name, id.toString()) else null
        }
        AdventureWalletEditor(players = walletPlayers,
            balances = state.adventure.wallets,
            onSave = { playerId, coins -> session.setPlayerCoins(playerId, coins) },
            onClose = { showWalletEditor = false })
    }

    LaunchedEffect(shopId, state.players, state.activeMapId, state.markers) {
        if (!isMaster && shopId != null) {
            val id = shopId ?: return@LaunchedEffect
            val marker = state.markers.firstOrNull { it.id == id }
            val mine = state.players.firstOrNull { it.id == state.myId }
            if (marker != null && mine != null &&
                (mine.mapId != state.activeMapId || !lanMarkerInRange(mine, marker))) {
                session.adventureCommand("close_shop")
                shopId = null
            }
        }
    }

    val incomingTrade = state.adventure.trade?.takeIf {
        !isMaster && state.myId in setOf(it.proposer, it.recipient)
    }
    if (!isMaster && (incomingTrade != null || tradeTargetId != null) && activeCharacter != null) {
        val trade = incomingTrade
        val target = tradeTargetId?.let { id -> state.players.firstOrNull { it.id == id } }
        AdventureTradeDialog(myId = state.myId, targetName = target?.name ?: "Игрок",
            myInventory = CharacterStorage.loadInventory(context, activeCharacter.id), trade = trade,
            onPropose = { ids ->
                val recipientId = tradeTargetId
                if (recipientId != null) { session.adventureCommand("trade_offer", targetId = recipientId, itemIds = ids); tradeSent = true }
            },
            onCounter = { ids -> session.adventureCommand("trade_counter", itemIds = ids) },
            onConfirm = { session.adventureCommand("trade_confirm") },
            onCancel = {
                if (trade != null) session.adventureCommand("trade_cancel")
                tradeTargetId = null; tradeSent = false
            })
    }

    if (!isMaster && state.adventure.vote?.voters?.contains(state.myId) == true) {
        val vote = state.adventure.vote!!
        val marker = state.roomMarkers[vote.mapId]?.firstOrNull { it.id == vote.markerId }
        val mine = state.players.firstOrNull { it.id == state.myId }
        val isFar = marker != null && mine != null &&
            (mine.mapId != vote.mapId || !adventureDistanceNear(mine.x, mine.y, marker))
        AdventureVoteDialog(name = marker?.name ?: "Переход", vote = vote, myId = state.myId,
            showScroll = isFar, scrollRemaining = (2 - (state.adventure.scrollUses[activeCharacter?.id?.toString()] ?: 0)).coerceAtLeast(0),
            onYes = { session.adventureCommand("vote_yes") },
            onNo = { session.adventureCommand("vote_no") },
            onScroll = { session.adventureCommand("use_scroll") })
    }

    if (isMaster && heroStoryPlayerId != null) {
        val playerId = heroStoryPlayerId!!
        val playerName = state.players.firstOrNull { it.id == playerId }?.name ?: "Игрок"
        AdventureHeroStoryDialog(playerName,
            onClose = { heroStoryPlayerId = null },
            onSave = { goodName, good, badName, bad ->
                if (session.giveHeroStory(playerId, goodName, good, badName, bad)) heroStoryPlayerId = null
            })
    }

    selectedPlayer?.let { selection ->
        val player = state.players.firstOrNull { it.id == selection.id } ?: selection
        AlertDialog(
            onDismissRequest = { selectedPlayer = null },
            title = { Text(player.name) },
            text = {
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    Text(if (player.id == state.myId) "Твой персонаж" else "Игрок в комнате")
                    if (!isMaster && player.id == state.myId) {
                        Text("Твой персонаж всегда управляется джойстиком на карте. Ничего включать не нужно.",
                            fontSize = 12.sp)
                    }
                    if (!isMaster && player.id != state.myId && activeCharacter != null &&
                        state.players.firstOrNull { it.id == state.myId }?.let { mine ->
                            mine.mapId == player.mapId &&
                                kotlin.math.hypot(mine.x - player.x, mine.y - player.y) <= LAN_INTERACTION_RADIUS
                        } == true) {
                        TextButton(onClick = { tradeTargetId = player.id; selectedPlayer = null }) {
                            Text("⇄ Предложить обмен")
                        }
                    }
                    if (player.maxHp > 0) {
                        Text("Здоровье: ${player.hp.coerceIn(0, player.maxHp)}/${player.maxHp}",
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    if (isMaster && player.id != state.myId && player.maxHp > 0) {
                        val charId = state.characterSnapshots.firstOrNull { it.ownerId == player.id }
                            ?.let { runCatching { JSONObject(it.payload).optLong("id", -1L) }.getOrNull() } ?: -1L
                        if (charId > 0L) {
                            Text("Быстро изменить HP", fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(-2, -5, -10, -15).forEach { amount ->
                                    OutlinedButton(onClick = { session.editCharacterAsMaster(player.id, charId,
                                        "adjust_health", JSONObject().put("delta", amount), "HP ${player.name}: $amount") },
                                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                                        Text("$amount", fontSize = 11.sp)
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(2, 5, 10, 15).forEach { amount ->
                                    OutlinedButton(onClick = { session.editCharacterAsMaster(player.id, charId,
                                        "adjust_health", JSONObject().put("delta", amount), "HP ${player.name}: +$amount") },
                                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                                        Text("+$amount", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                    if (isMaster && player.id != state.myId) {
                        TextButton(onClick = {
                            observingPlayerId = player.id; observationTab = "character"; selectedPlayer = null
                        }) { Text("Управлять персонажем") }
                    }
                }
            },
            confirmButton = {
                if (isMaster) {
                    TextButton(onClick = {
                        session.setMasterMovementTarget(player.id)
                        moveMarkerId = null
                        movingPlayerId = player.id
                        movingOwnPlayer = true
                        previewPosition = player.x to player.y
                        selectedPlayer = null
                    }) { Text("Переместить") }
                } else {
                    TextButton(onClick = { selectedPlayer = null }) { Text("Закрыть") }
                }
            },
            dismissButton = {
                if (isMaster) {
                    TextButton(onClick = { selectedPlayer = null }) { Text("Отмена") }
                }
            }
        )
    }

    val activeConversation = state.conversation
    val conversationMember = activeConversation != null &&
        (isMaster || state.myId in activeConversation.participants)
    if (conversationMember && activeConversation != null) {
        AlertDialog(
            onDismissRequest = { if (isMaster) session.dialogCommand("close") else session.dialogCommand("leave") },
            title = { Text("💬 ${activeConversation.markerName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text(if (activeConversation.global) "Обращение ко всей группе" else "Разговор с NPC")
                    val marker = state.markers.firstOrNull { it.id == activeConversation.markerId }
                    if (marker != null) Text(marker.description.ifBlank { "Разговор начался." })
                    activeConversation.history.forEach { line ->
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium) {
                            Text("${activeConversation.markerName}: $line", Modifier.padding(10.dp))
                        }
                    }
                    val participants = activeConversation.participants.mapNotNull { id ->
                        state.players.firstOrNull { it.id == id }?.name ?: if (id == "host") "Мастер" else null
                    }
                    Text("Участники: ${participants.joinToString().ifBlank { "Мастер" }}", fontSize = 12.sp)
                    if (marker?.type != "Толпа") {
                        val speaker = activeConversation.speakerId
                        val speakerName = state.players.firstOrNull { it.id == speaker }?.name ?: "Мастер"
                        Text(if (speaker.isNotBlank()) "Сейчас говорит: $speakerName" else "Можно говорить")
                        if (!isMaster) {
                            Button(
                                enabled = speaker.isBlank() || speaker == state.myId,
                                onClick = { session.dialogCommand(if (speaker == state.myId) "release" else "speak") }
                            ) { Text(if (speaker == state.myId) "Завершить реплику" else "Сказать") }
                        } else {
                            if (speaker.isNotBlank()) TextButton(onClick = { session.dialogCommand("release") }) {
                                Text("Освободить право говорить")
                            }
                        }
                    }
                    if (isMaster) {
                        OutlinedTextField(value = npcLineInput, onValueChange = { npcLineInput = it.take(1000) },
                            label = { Text("Новая реплика NPC") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        TextButton(onClick = { session.dialogCommand("line", line = npcLineInput); npcLineInput = "" },
                            enabled = npcLineInput.isNotBlank()) { Text("Показать реплику участникам") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { session.dialogCommand(if (isMaster) "close" else "leave") }) {
                    Text(if (isMaster) "Завершить разговор" else "Выйти из разговора")
                }
            }
        )
    }

    editMarker?.let { marker ->
        var editedName by remember(marker.id) { mutableStateOf(marker.name) }
        var editedDescription by remember(marker.id) { mutableStateOf(marker.description) }
        var hidden by remember(marker.id) { mutableStateOf(marker.hidden) }
        var revealMode by remember(marker.id) { mutableStateOf(marker.revealMode) }
        var revealShape by remember(marker.id) { mutableStateOf(marker.revealShape) }
        var revealTo by remember(marker.id) { mutableStateOf(marker.revealTo) }
        var showDiscovery by remember(marker.id) { mutableStateOf(marker.hidden) }
        val edited = marker.copy(name = editedName.trim(), description = editedDescription,
            hidden = hidden, revealMode = revealMode, revealShape = revealShape,
            revealTo = revealTo)
        Dialog(onDismissRequest = { editMarker = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxWidth(0.86f).fillMaxHeight(0.9f), shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Редактор: ${marker.name}", style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedTextField(editedName, { editedName = it.take(80) },
                            label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(editedDescription, { editedDescription = it.take(1000) },
                            label = { Text("Описание") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                        HorizontalDivider()
                        TextButton(onClick = { showDiscovery = !showDiscovery }) {
                            Text(if (showDiscovery) "▾ Скрытность и обнаружение" else "▸ Скрытность и обнаружение")
                        }
                        if (showDiscovery) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = hidden, onCheckedChange = { hidden = it })
                                Text("Скрытая метка (видна мастеру)")
                            }
                            if (hidden) {
                                Text("Когда раскрывать", style = MaterialTheme.typography.labelMedium)
                                listOf("zone" to "Игрок входит в область", "touch" to "Игрок наступает на метку",
                                    "manual" to "Только мастер вручную").forEach { (key, title) ->
                                    Row(Modifier.fillMaxWidth().clickable { revealMode = key },
                                        verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = revealMode == key, onClick = { revealMode = key })
                                        Text(title, fontSize = 13.sp)
                                    }
                                }
                                Text("Кому показывать", style = MaterialTheme.typography.labelMedium)
                                listOf("all" to "Всей группе", "one" to "Только обнаружившему").forEach { (key, title) ->
                                    Row(Modifier.fillMaxWidth().clickable { revealTo = key },
                                        verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = revealTo == key, onClick = { revealTo = key })
                                        Text(title, fontSize = 13.sp)
                                    }
                                }
                                if (revealMode == "zone") {
                                    Text("Форма области", style = MaterialTheme.typography.labelMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(selected = revealShape == "circle", onClick = { revealShape = "circle" },
                                            label = { Text("Круг") })
                                        FilterChip(selected = revealShape == "rect", onClick = { revealShape = "rect" },
                                            label = { Text("Прямоугольник") })
                                    }
                                    OutlinedButton(onClick = {
                                        session.updateMarker(edited)
                                        detectionZoneEdit = edited
                                        zoneRadiusX = marker.revealRadiusX
                                        zoneRadiusY = marker.revealRadiusY
                                        editMarker = null
                                    }, modifier = Modifier.fillMaxWidth()) { Text("◎ Нарисовать область на карте") }
                                    Text("При настройке перетащи палец от центра метки к границе зоны.",
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedButton(onClick = {
                                    session.updateMarker(edited.copy(revealedAll = true))
                                    editMarker = null
                                }, modifier = Modifier.fillMaxWidth()) { Text("Раскрыть всем сейчас") }
                                OutlinedButton(onClick = {
                                    session.updateMarker(edited.copy(revealedAll = false))
                                    session.setMarkerDiscovery(marker.id, state.activeMapId, false)
                                    editMarker = null
                                }, modifier = Modifier.fillMaxWidth()) { Text("Снова скрыть (сброс обнаружения)") }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editMarker = null }) { Text("Отмена") }
                        Button(enabled = editedName.isNotBlank(), onClick = {
                            session.updateMarker(edited)
                            editMarker = null
                        }) { Text("Сохранить") }
                    }
                }
            }
        }
    }

    editingCreatureHealth?.let { selected ->
        val live = state.markers.firstOrNull { it.id == selected.id } ?: selected
        AlertDialog(onDismissRequest = { editingCreatureHealth = null },
            title = { Text("Точное здоровье: ${live.name}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = creatureCurrentInput,
                    onValueChange = { creatureCurrentInput = it.filter(Char::isDigit).take(6) },
                    label = { Text("Текущее HP") }, singleLine = true)
                OutlinedTextField(value = creatureMaxInput,
                    onValueChange = { creatureMaxInput = it.filter(Char::isDigit).take(6) },
                    label = { Text("Максимальное HP") }, singleLine = true)
            } },
            confirmButton = { TextButton(onClick = {
                val maximum = (creatureMaxInput.toIntOrNull() ?: 10).coerceIn(1, 100000)
                val currentHp = (creatureCurrentInput.toIntOrNull() ?: maximum).coerceIn(0, maximum)
                session.updateMarker(live.copy(hp = currentHp, maxHp = maximum))
                editingCreatureHealth = null
            }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { editingCreatureHealth = null }) { Text("Отмена") } })
    }

    selectedMarker?.let { previousMarker ->
        val marker = state.markers.firstOrNull { it.id == previousMarker.id } ?: previousMarker
        val isAdventureObject = marker.type in setOf("Сундук", "Дверь", "Дом", "Портал", "Магазин")
        val canMove = isMaster || (marker.ownerId == state.myId && state.permissions.moveOwnMarkers)
        val canDelete = isMaster || (marker.ownerId == state.myId && state.permissions.deleteOwnMarkers)
        Dialog(onDismissRequest = { selectedMarker = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.76f).heightIn(max = 430.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${markerIcon(marker.type)} ${marker.name}",
                        style = MaterialTheme.typography.titleLarge)
                    // Long marker descriptions and actions scroll together; no text is lost.
                    Column(Modifier.heightIn(max = 315.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(marker.description.ifBlank { "Тип: ${marker.type}" },
                            style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider()
                        if (isMaster && marker.type !in setOf("Сундук", "Дверь", "Дом", "Магазин", "Портал", "Записка", "Лагерь")) {
                            val maximum = marker.maxHp.takeIf { it > 0 } ?: 10
                            val currentHp = marker.hp.takeIf { it >= 0 }?.coerceIn(0, maximum) ?: maximum
                            Text("❤ $currentHp/$maximum HP", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            Text("Урон", fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(2, 5, 10, 15).forEach { amount ->
                                    OutlinedButton(onClick = {
                                        session.updateMarker(marker.copy(hp = (currentHp - amount).coerceAtLeast(0), maxHp = maximum))
                                    }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                                        Text("−$amount", fontSize = 11.sp)
                                    }
                                }
                            }
                            Text("Лечение", fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(2, 5, 10, 15).forEach { amount ->
                                    OutlinedButton(onClick = {
                                        session.updateMarker(marker.copy(hp = (currentHp + amount).coerceAtMost(maximum), maxHp = maximum))
                                    }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                                        Text("+$amount", fontSize = 11.sp)
                                    }
                                }
                            }
                            TextButton(onClick = {
                                creatureMaxInput = maximum.toString()
                                creatureCurrentInput = currentHp.toString()
                                editingCreatureHealth = marker
                                selectedMarker = null
                            }) { Text("Задать точное HP и максимум") }
                        } else if (marker.maxHp > 0) {
                            Text("❤ ${marker.hp.coerceIn(0, marker.maxHp)}/${marker.maxHp} HP",
                                fontWeight = FontWeight.Bold)
                        }
                        if (marker.type == "NPC" || marker.type == "Толпа") {
                            val same = state.conversation?.markerId == marker.id
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    session.dialogCommand(if (isMaster) "open" else if (same) "join" else "open", marker.id)
                                    selectedMarker = null
                                }, modifier = Modifier.weight(1f)) {
                                    Text(if (same) "Присоединиться" else if (marker.type == "Толпа") "Послушать" else "Поговорить",
                                        maxLines = 2)
                                }
                                if (isMaster) OutlinedButton(onClick = {
                                    session.dialogCommand("global", marker.id)
                                    selectedMarker = null
                                }, modifier = Modifier.weight(1f)) { Text("Ко всем") }
                            }
                        }
                        if (isAdventureObject) {
                            if (isMaster) OutlinedButton(onClick = {
                                adventureEditor = marker; selectedMarker = null
                            }, modifier = Modifier.fillMaxWidth()) { Text("Настроить объект") }
                            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val obj = state.adventure.objects.firstOrNull { it.markerId == marker.id }
                                    if (marker.type == "Сундук") {
                                        chestId = marker.id
                                        session.adventureCommand("open_chest", marker.id)
                                    } else if (obj?.isShop() == true) {
                                        shopId = marker.id
                                        session.adventureCommand("open_shop", marker.id)
                                    } else session.adventureCommand("enter", marker.id)
                                    selectedMarker = null
                                }, modifier = Modifier.weight(1f)) {
                                    val isShop = state.adventure.objects.firstOrNull { it.markerId == marker.id }?.isShop() == true
                                    Text(if (marker.type == "Сундук") "Открыть"
                                        else if (isShop) "Торговать"
                                        else if (isAutoReturnMarker(marker.id)) "Выйти" else "Войти")
                                }
                                if (marker.type != "Сундук" &&
                                    state.adventure.objects.firstOrNull { it.markerId == marker.id }?.isShop() != true) {
                                    OutlinedButton(onClick = {
                                        session.adventureCommand("group_vote", marker.id)
                                        selectedMarker = null
                                    }, modifier = Modifier.weight(1f)) { Text("Группой") }
                                }
                            }
                        }
                        if (isMaster || canMove || canDelete) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isMaster) TextButton(onClick = {
                                    editMarker = marker; selectedMarker = null
                                }, modifier = Modifier.weight(1f)) { Text("Правка") }
                                if (canMove) TextButton(onClick = {
                                    // Editing a map marker releases the previously controlled player.
                                    if (isMaster) session.setMasterMovementTarget(null)
                                    moveMarkerId = marker.id
                                    movingOwnPlayer = false
                                    movingPlayerId = null
                                    previewPosition = marker.x to marker.y
                                    selectedMarker = null
                                }, modifier = Modifier.weight(1f)) { Text("Двигать") }
                                if (canDelete) TextButton(onClick = {
                                    session.deleteMarker(marker.id)
                                    selectedMarker = null
                                }, modifier = Modifier.weight(1f)) {
                                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { selectedMarker = null }) { Text("Закрыть") }
                    }
                }
            }
        }
    }

    pendingPoint?.let { point ->
        var typesExpanded by remember { mutableStateOf(false) }
        Dialog(onDismissRequest = { pendingPoint = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.78f).fillMaxHeight(0.89f),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Новая метка", style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(markerName, { markerName = it.take(80) },
                            label = { Text("Название") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(markerDescription, { markerDescription = it.take(1000) },
                            label = { Text("Описание") }, minLines = 2,
                            modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { showMarkerCatalog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("📚 База меток и противников", maxLines = 2)
                        }
                        if (isMaster) Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = markerHidden, onCheckedChange = { markerHidden = it })
                            Text("Скрыть от игроков до обнаружения", fontSize = 12.sp)
                        }
                        Text("Тип метки", style = MaterialTheme.typography.labelMedium)
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { typesExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("${markerIcon(markerType)} $markerType", maxLines = 2)
                            }
                            DropdownMenu(expanded = typesExpanded, onDismissRequest = { typesExpanded = false }) {
                                RpgCatalog.markerTypes.filter { it != "Магазин" && (isMaster || it !in setOf("NPC", "Толпа", "Записка", "Сундук", "Дверь", "Дом", "Магазин", "Портал", "Лагерь")) }.forEach { type ->
                                    DropdownMenuItem(text = { Text("${markerIcon(type)} $type") }, onClick = {
                                        markerType = type
                                        typesExpanded = false
                                    })
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { pendingPoint = null }) { Text("Отмена") }
                        Button(enabled = markerName.isNotBlank(), onClick = {
                            session.addMarker(LanMarker(UUID.randomUUID().toString(),
                                markerName.trim(), markerDescription.trim(), markerType, point.first, point.second,
                                hidden = isMaster && markerHidden))
                            markerHidden = false
                            markerName = ""
                            markerDescription = ""
                            pendingPoint = null
                            addingMarker = false
                        }) { Text("Сохранить") }
                    }
                }
            }
        }
    }

    if (showMarkerCatalog && pendingPoint != null) RpgCatalogPickerDialog(
        title = "Шаблоны меток и противников",
        entries = RpgCatalog.markerPresets.filter {
            it.name != "Магазин" && (isMaster || it.markerType !in setOf("NPC", "Толпа", "Записка"))
        },
        onSelect = { preset ->
            markerName = preset.name
            markerDescription = preset.description.take(300)
            markerType = preset.markerType
            showMarkerCatalog = false
        },
        onDismiss = { showMarkerCatalog = false }
    )

    // A different room map gets its own initial "fit" view.
    LaunchedEffect(state.activeMapId) {
        cameraZoom = 1f
        cameraPan = Offset.Zero
    }

    val mapContent: @Composable (Boolean) -> Unit = { expanded ->
        val image = bitmap
        if (image != null) {
                    val aspect = image.width.toFloat() / image.height.coerceAtLeast(1)
                    // The viewport changes size when the master's drawer is opened.
                    // Unlike fillMaxWidth().aspectRatio(), fitting BOTH axes keeps the
                    // entire map in view at zoom=1 regardless of the panel width.
                    BoxWithConstraints(
                        (if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(320.dp))
                            .clipToBounds()
                            .background(Color(0xFF171B21))
                            .pointerInput(state.activeMapId, addingMarker, fogEditing, detectionZoneEdit?.id) {
                                if (!addingMarker && !fogEditing && detectionZoneEdit == null) {
                                    detectTransformGestures { centroid, pan, zoom, _ ->
                                        val previousZoom = cameraZoom
                                        val nextZoom = (previousZoom * zoom).coerceIn(1f, 5f)
                                        val scaledRatio = nextZoom / previousZoom
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val adjustedPan = cameraPan + pan +
                                            (centroid - center - cameraPan) * (1f - scaledRatio)
                                        val fittedW = min(size.width.toFloat(), size.height * aspect)
                                        val fittedH = fittedW / aspect
                                        val maxX = max(0f, (fittedW * nextZoom - size.width) / 2f)
                                        val maxY = max(0f, (fittedH * nextZoom - size.height) / 2f)
                                        cameraZoom = nextZoom
                                        cameraPan = Offset(
                                            adjustedPan.x.coerceIn(-maxX, maxX),
                                            adjustedPan.y.coerceIn(-maxY, maxY)
                                        )
                                    }
                                }
                            }
                    ) {
                        val density = LocalDensity.current
                        val viewportW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                        val viewportH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                        val widthPx = min(viewportW, viewportH * aspect).coerceAtLeast(1f)
                        val heightPx = (widthPx / aspect).coerceAtLeast(1f)
                        val imageWidth = with(density) { widthPx.toDp() }
                        val imageHeight = with(density) { heightPx.toDp() }
                        val maxCameraX = max(0f, (widthPx * cameraZoom - viewportW) / 2f)
                        val maxCameraY = max(0f, (heightPx * cameraZoom - viewportH) / 2f)
                        Box(
                            Modifier.align(Alignment.Center)
                                .size(imageWidth, imageHeight)
                                .graphicsLayer {
                                    scaleX = cameraZoom
                                    scaleY = cameraZoom
                                    translationX = cameraPan.x.coerceIn(-maxCameraX, maxCameraX)
                                    translationY = cameraPan.y.coerceIn(-maxCameraY, maxCameraY)
                                    transformOrigin = TransformOrigin.Center
                                }
                                .border(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                        Image(
                            bitmap = image,
                            contentDescription = "Общая карта",
                            modifier = Modifier.fillMaxSize().then(
                                if (addingMarker && (isMaster || state.permissions.createMarkers)) {
                                    Modifier.pointerInput(session, addingMarker) {
                                        detectTapGestures { offset ->
                                            pendingPoint = (offset.x / size.width).coerceIn(0f, 1f) to
                                                (offset.y / size.height).coerceIn(0f, 1f)
                                        }
                                    }
                                } else Modifier
                            ),
                            contentScale = ContentScale.FillBounds
                        )
                        state.markers.filter { isMaster || state.fog.visible(it.x, it.y) }.forEach { marker ->
                            val drawX = if (moveMarkerId == marker.id) previewPosition?.first ?: marker.x else marker.x
                            val drawY = if (moveMarkerId == marker.id) previewPosition?.second ?: marker.y else marker.y
                            val smoothX by animateFloatAsState(
                                targetValue = drawX,
                                animationSpec = tween(durationMillis = 110, easing = LinearEasing),
                                label = "markerX"
                            )
                            val smoothY by animateFloatAsState(
                                targetValue = drawY,
                                animationSpec = tween(durationMillis = 110, easing = LinearEasing),
                                label = "markerY"
                            )
                            Box(
                                Modifier.offset {
                                    IntOffset(
                                        (smoothX * widthPx).roundToInt() - 14.dp.roundToPx(),
                                        (smoothY * heightPx).roundToInt() - 14.dp.roundToPx()
                                    )
                                }
                                    .size(28.dp).clip(CircleShape)
                                    .background(if (isMaster && marker.hidden && !marker.revealedAll)
                                        Color(0xFF7258A7) else Color(0xFFFFC107))
                                    .border(1.dp, Color.White, CircleShape)
                                    .clickable {
                                        val interactive = marker.type in setOf("NPC", "Толпа", "Записка", "Сундук", "Дверь", "Дом", "Портал", "Магазин")
                                        val me = state.players.firstOrNull { it.id == state.myId }
                                        if (isMaster || !interactive ||
                                            (me != null && lanMarkerInRange(me, marker))) {
                                            selectedMarker = marker
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(if (isMaster && marker.hidden && !marker.revealedAll)
                                "◈" else markerIcon(marker.type), fontSize = 15.sp) }
                            if (marker.maxHp > 0) {
                                Box(Modifier.offset {
                                    IntOffset((smoothX * widthPx).roundToInt() - 26.dp.roundToPx(),
                                        (smoothY * heightPx).roundToInt() + 16.dp.roundToPx())
                                }.width(52.dp).background(Color(0xD9001219), MaterialTheme.shapes.small),
                                    contentAlignment = Alignment.Center) {
                                    Text("${marker.hp.coerceIn(0, marker.maxHp)}/${marker.maxHp}",
                                        fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold,
                                        maxLines = 1)
                                }
                            }
                        }
                        state.players.filter { (it.mapId.isBlank() || it.mapId == state.activeMapId) &&
                            (isMaster || state.fog.visible(it.x, it.y)) }.forEach { player ->
                            val mine = player.id == state.myId
                            val previewPlayer = (movingOwnPlayer && movingPlayerId == player.id) ||
                                (!isMaster && moveMarkerId == null && player.id == state.myId)
                            val drawX = if (previewPlayer) previewPosition?.first ?: player.x else player.x
                            val drawY = if (previewPlayer) previewPosition?.second ?: player.y else player.y
                            val smoothX by animateFloatAsState(
                                targetValue = drawX,
                                animationSpec = tween(durationMillis = 110, easing = LinearEasing),
                                label = "playerX"
                            )
                            val smoothY by animateFloatAsState(
                                targetValue = drawY,
                                animationSpec = tween(durationMillis = 110, easing = LinearEasing),
                                label = "playerY"
                            )
                            Box(
                                Modifier.offset {
                                    IntOffset(
                                        (smoothX * widthPx).roundToInt() - 13.dp.roundToPx(),
                                        (smoothY * heightPx).roundToInt() - 13.dp.roundToPx()
                                    )
                                }
                                    .size(26.dp).clip(CircleShape)
                                    .background(if (mine) Color(0xFF00BFA5) else Color(0xFFE53935))
                                    .border(2.dp, Color.White, CircleShape)
                                    .clickable { selectedPlayer = player }
                            )
                            if (player.maxHp > 0) {
                                Box(Modifier.offset {
                                    IntOffset((smoothX * widthPx).roundToInt() - 28.dp.roundToPx(),
                                        (smoothY * heightPx).roundToInt() + 15.dp.roundToPx())
                                }.width(56.dp).background(Color(0xD9001219), MaterialTheme.shapes.small),
                                    contentAlignment = Alignment.Center) {
                                    Text("${player.hp.coerceIn(0, player.maxHp)}/${player.maxHp}",
                                        fontSize = 10.sp, color = Color.White,
                                        fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                        if (state.fog.enabled) {
                            val fogPaint = if (isMaster) Color(0xB0000711) else Color(0xF700030A)
                            val fogModifier = if (isMaster && fogEditing) {
                                Modifier.fillMaxSize()
                                    .pointerInput(state.activeMapId, fogEditing, fogErase) {
                                        detectTapGestures { point ->
                                            session.brushFog((point.x / size.width).coerceIn(0f, 1f),
                                                (point.y / size.height).coerceIn(0f, 1f), fogErase)
                                        }
                                    }
                                    .pointerInput(state.activeMapId, fogEditing, fogErase) {
                                        detectDragGestures(onDragStart = { point ->
                                            session.brushFog((point.x / size.width).coerceIn(0f, 1f),
                                                (point.y / size.height).coerceIn(0f, 1f), fogErase)
                                        }) { change, _ ->
                                            change.consume()
                                            val now = android.os.SystemClock.uptimeMillis()
                                            if (now - lastFogBrushAt >= 45L) {
                                                lastFogBrushAt = now
                                                session.brushFog((change.position.x / size.width).coerceIn(0f, 1f),
                                                    (change.position.y / size.height).coerceIn(0f, 1f), fogErase)
                                            }
                                        }
                                    }
                            } else Modifier.fillMaxSize()
                            Canvas(fogModifier) {
                                val tileW = size.width / 48f
                                val tileH = size.height / 32f
                                for (row in 0 until 32) for (col in 0 until 48) {
                                    if (state.fog.revealed.getOrNull(row * 48 + col) != '1') {
                                        drawRect(fogPaint, topLeft = Offset(col * tileW, row * tileH),
                                            size = androidx.compose.ui.geometry.Size(tileW + .35f, tileH + .35f))
                                    }
                                }
                            }
                        }
                        if (isMaster && detectionZoneEdit != null && detectionZoneEdit!!.id in
                            state.markers.map { it.id }) {
                            val zone = detectionZoneEdit!!
                            Canvas(Modifier.fillMaxSize()
                                .pointerInput(zone.id, zone.revealShape) {
                                    detectTapGestures { point ->
                                        zoneRadiusX = kotlin.math.abs(point.x / size.width - zone.x)
                                            .coerceIn(0.005f, 1f)
                                        zoneRadiusY = kotlin.math.abs(point.y / size.height - zone.y)
                                            .coerceIn(0.005f, 1f)
                                    }
                                }
                                .pointerInput(zone.id, zone.revealShape) {
                                    detectDragGestures(onDragStart = { point ->
                                        zoneRadiusX = kotlin.math.abs(point.x / size.width - zone.x)
                                            .coerceIn(0.005f, 1f)
                                        zoneRadiusY = kotlin.math.abs(point.y / size.height - zone.y)
                                            .coerceIn(0.005f, 1f)
                                    }) { change, _ ->
                                        change.consume()
                                        zoneRadiusX = kotlin.math.abs(change.position.x / size.width - zone.x)
                                            .coerceIn(0.005f, 1f)
                                        zoneRadiusY = kotlin.math.abs(change.position.y / size.height - zone.y)
                                            .coerceIn(0.005f, 1f)
                                    }
                                }
                            ) {
                                val rx = zoneRadiusX * size.width
                                val ry = zoneRadiusY * size.height
                                val center = Offset(zone.x * size.width, zone.y * size.height)
                                val paint = Color(0xFFE9B65D)
                                if (zone.revealShape == "rect") {
                                    drawRect(paint.copy(alpha = 0.18f), Offset(center.x - rx, center.y - ry), Size(rx*2, ry*2))
                                    drawRect(paint, Offset(center.x - rx, center.y - ry), Size(rx*2, ry*2), style = Stroke(width = 2.dp.toPx()))
                                } else {
                                    drawOval(paint.copy(alpha = 0.18f), Offset(center.x - rx, center.y - ry), Size(rx*2, ry*2))
                                    drawOval(paint, Offset(center.x - rx, center.y - ry), Size(rx*2, ry*2), style = Stroke(width = 2.dp.toPx()))
                                }
                                drawCircle(Color.White, radius = 5.dp.toPx(), center = center)
                            }
                            Button(onClick = {
                                session.updateMarker(zone.copy(revealRadiusX = zoneRadiusX,
                                    revealRadiusY = zoneRadiusY))
                                detectionZoneEdit = null
                            }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                Text("✓ Сохранить область", fontSize = 11.sp)
                            }
                        }
                        } // Transform image, markers, players and fog as a single map.
                    } // Camera viewport (clipped to available area).
        }
    }

    // Interactions are offered next to the map only to a nearby player.
    // The server separately validates range for network conversations.
    val ownPosition = state.players.firstOrNull { it.id == state.myId }
    val nearbyInteractions = if (!isMaster && ownPosition != null) {
        state.markers.filter { marker ->
            marker.type in setOf("NPC", "Толпа", "Записка", "Сундук", "Дверь", "Дом", "Магазин", "Портал") &&
                (ownPosition.mapId.isBlank() || ownPosition.mapId == state.activeMapId) &&
                state.fog.visible(marker.x, marker.y) && lanMarkerInRange(ownPosition, marker)
        }.sortedBy { marker ->
            val dx = ownPosition.x - marker.x
            val dy = ownPosition.y - marker.y
            dx * dx + dy * dy
        }
    } else emptyList()
    var interactionMenuExpanded by remember { mutableStateOf(false) }
    val interactWith: (LanMarker) -> Unit = { marker ->
        if (marker.type == "Записка") selectedMarker = marker
        else if (marker.type == "Сундук") {
            chestId = marker.id
            session.adventureCommand("open_chest", marker.id)
        } else if (marker.type in setOf("Дверь", "Дом", "Магазин", "Портал")) {
            if (state.adventure.objects.firstOrNull { it.markerId == marker.id }?.isShop() == true) {
                shopId = marker.id
                session.adventureCommand("open_shop", marker.id)
            } else selectedMarker = marker
        } else {
            val current = state.conversation
            val action = if (current?.markerId == marker.id) "join" else "open"
            session.dialogCommand(action, marker.id)
        }
        interactionMenuExpanded = false
    }
    val nearbyControl: @Composable () -> Unit = {
        if (!isMaster && nearbyInteractions.isNotEmpty()) {
            Column(
                Modifier.width(138.dp).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Рядом", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                val closest = nearbyInteractions.first()
                Text(closest.name, fontSize = 11.sp, maxLines = 2)
                val actionText = when (closest.type) {
                    "NPC" -> if (state.conversation?.markerId == closest.id) "Присоединиться" else "Говорить"
                    "Толпа" -> "Подслушать"
                    "Сундук" -> "Открыть"
                    "Дверь", "Дом", "Магазин", "Портал" -> if (isAutoReturnMarker(closest.id)) "Выйти" else "Войти"
                    else -> "Прочитать"
                }
                if (nearbyInteractions.size == 1) {
                    Button(onClick = { interactWith(closest) }, modifier = Modifier.fillMaxWidth()) {
                        Text(actionText, fontSize = 11.sp)
                    }
                } else {
                    Box {
                        Button(onClick = { interactionMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Взаимодействовать", fontSize = 10.sp)
                        }
                        DropdownMenu(expanded = interactionMenuExpanded,
                            onDismissRequest = { interactionMenuExpanded = false }) {
                            nearbyInteractions.forEach { marker ->
                                DropdownMenuItem(
                                    text = { Text("${markerIcon(marker.type)} ${marker.name}", maxLines = 2) },
                                    onClick = { interactWith(marker) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    val campControl: @Composable () -> Unit = {
        val myPlayer = state.players.firstOrNull { it.id == state.myId }
        if (!isMaster && myPlayer != null && state.adventure.phase == "day" &&
            state.adventure.campMarkerId.isBlank() && state.players.all { other ->
                other.mapId == myPlayer.mapId &&
                    kotlin.math.hypot(other.x - myPlayer.x, other.y - myPlayer.y) <= LAN_INTERACTION_RADIUS
            }) {
            OutlinedButton(onClick = { session.adventureCommand("camp") },
                modifier = Modifier.fillMaxWidth()) { Text("🔥 Разбить лагерь", fontSize = 11.sp) }
        }
    }
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(state.masterMovingPlayerId, state.myId, isMaster) {
        if (!isMaster && state.masterMovingPlayerId.isNotEmpty() &&
            state.masterMovingPlayerId == state.myId) {
            // Cancel any local drag preview as soon as the host takes over.
            previewPosition = null
        }
    }
    val canCreateMarker = isMaster || state.permissions.createMarkers
    val moveControl: @Composable () -> Unit = {
        // A guest controls their own player as soon as they join. Editing a marker
        // temporarily takes over this joystick; "Готово" returns to player control.
        val lockedByMaster = !isMaster && state.connected && state.myId.isNotBlank() &&
            state.masterMovingPlayerId == state.myId
        val battleFighter = state.combat.fighters.firstOrNull {
            it.kind == "player" && it.sourceId == state.myId
        }
        val battleMovementLocked = !isMaster && state.combat.phase == "active" &&
            battleFighter != null &&
            (state.combat.currentId != battleFighter.id || !battleFighter.movementAllowed)
        val guestCanWalk = !isMaster && state.connected && state.myId.isNotBlank() &&
            !lockedByMaster && !battleMovementLocked && state.players.any { it.id == state.myId }
        val controlsPlayer = movingOwnPlayer || (guestCanWalk && moveMarkerId == null)
        val controlledPlayerId = if (movingOwnPlayer) movingPlayerId else if (guestCanWalk && moveMarkerId == null)
            state.myId else null
        val active = !lockedByMaster && (controlsPlayer || moveMarkerId != null)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                when {
                    lockedByMaster -> "Мастер перемещает тебя"
                    battleMovementLocked -> "⚔ Сначала выбери «Перемещение»"
                    controlsPlayer && !isMaster -> "Твой персонаж"
                    controlsPlayer -> "Персонаж"
                    moveMarkerId != null -> "Выбранная метка"
                    else -> "Выбери точку"
                },
                fontSize = 12.sp
            )
            Surface(
                modifier = Modifier.size(110.dp)
                    .pointerInput(active, lockedByMaster, controlsPlayer, controlledPlayerId, moveMarkerId) {
                        if (active) {
                            detectDragGestures(
                                onDragEnd = {
                                    // Always send the last drag position, even if the 40 ms
                                    // network throttle skipped the final movement event.
                                    previewPosition?.let { point ->
                                        if (controlsPlayer) {
                                            if (isMaster) session.movePlayerAsMaster(controlledPlayerId ?: "", point.first, point.second)
                                            else session.move(point.first, point.second)
                                        } else {
                                            latestState.markers.firstOrNull { it.id == moveMarkerId }
                                                ?.let { session.updateMarker(it.copy(x = point.first, y = point.second)) }
                                        }
                                    }
                                    // Free-movement mode has no "Готово" button: the
                                    // preview must not remain stale after a drag or teleport.
                                    previewPosition = null
                                },
                                onDragCancel = { previewPosition = null }
                            ) { change, dragAmount ->
                                change.consume()
                                val dx = dragAmount.x * 0.45f / size.width.coerceAtLeast(1)
                                val dy = dragAmount.y * 0.45f / size.height.coerceAtLeast(1)
                                val current = previewPosition ?: if (controlsPlayer) {
                                    latestState.players.firstOrNull { it.id == controlledPlayerId }
                                        ?.let { it.x to it.y }
                                } else {
                                    latestState.markers.firstOrNull { it.id == moveMarkerId }
                                        ?.let { it.x to it.y }
                                }
                                if (current != null) {
                                    val nextX = (current.first + dx).coerceIn(0f, 1f)
                                    val nextY = (current.second + dy).coerceIn(0f, 1f)
                                    val allowed = !controlsPlayer || isMaster ||
                                        latestState.fog.allowedPath(current.first, current.second, nextX, nextY)
                                    if (!allowed) return@detectDragGestures
                                    previewPosition = nextX to nextY
                                    val now = android.os.SystemClock.uptimeMillis()
                                    if (now - lastMoveSentAt >= 40L) {
                                        lastMoveSentAt = now
                                        if (controlsPlayer) {
                                            if (isMaster) session.movePlayerAsMaster(controlledPlayerId ?: "", nextX, nextY)
                                            else session.move(nextX, nextY)
                                        } else {
                                            latestState.markers.firstOrNull { it.id == moveMarkerId }
                                                ?.let { session.updateMarker(it.copy(x = nextX, y = nextY)) }
                                        }
                                    }
                                }
                            }
                        }
                    },
                shape = CircleShape,
                color = if (active) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 5.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "✥", fontSize = 54.sp,
                        color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
            }
            // Guest movement is permanent; only temporary master / marker movement
            // needs an explicit exit action.
            if (active && (moveMarkerId != null || movingOwnPlayer)) {
                TextButton(onClick = {
                    previewPosition?.let { point ->
                        if (controlsPlayer) {
                            if (isMaster) session.movePlayerAsMaster(controlledPlayerId ?: "", point.first, point.second)
                            else session.move(point.first, point.second)
                        } else {
                            latestState.markers.firstOrNull { it.id == moveMarkerId }
                                ?.let { session.updateMarker(it.copy(x = point.first, y = point.second)) }
                        }
                    }
                    if (isMaster && movingOwnPlayer) session.setMasterMovementTarget(null)
                    moveMarkerId = null
                    movingOwnPlayer = false
                    movingPlayerId = null
                    previewPosition = null
                }) { Text("Готово") }
            }
        }
    }

    // Keep the current map in place. Only the optional combat HUD is expanded.
    // Without an image we retain the v26 standalone combat screen as a fallback.
    val openCombatOnMap: () -> Unit = {
        if (state.connected && bitmap != null) {
            page = "map"
            fullscreen = true
            mapCombatExpanded = true
            if (state.combat.phase != "active") mapCombatDialog = true
        } else {
            fullscreen = false
            page = "combat"
        }
    }

    val roomMapControls: @Composable () -> Unit = {
        if (isMaster) {
            Box {
                OutlinedButton(onClick = { roomMapMenu = true }) {
                    Text("🗺 ${state.mapName.ifBlank { "Нет карты" }}", maxLines = 2)
                }
                DropdownMenu(expanded = roomMapMenu, onDismissRequest = { roomMapMenu = false }) {
                    state.roomMaps.forEach { info ->
                        DropdownMenuItem(
                            text = { Text(if (info.id == state.activeMapId) "✓ ${info.name}" else info.name) },
                            onClick = {
                                roomMapMenu = false
                                session.previewMap(info.id)
                            }
                        )
                    }
                }
            }
            if (session.isPreparingMap()) {
                Text("Подготовка: карта скрыта от игроков до публикации", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary)
                Button(onClick = { session.publishPreparedMap() },
                    enabled = state.activeMapId.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Показать карту всем")
                }
            } else {
                OutlinedButton(onClick = { session.returnMapToPreparation() },
                    enabled = state.activeMapId.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Вернуть карту в подготовку")
                }
            }
            Button(onClick = { showAddMapChooser = true },
                enabled = !loadingMap && state.roomMaps.size < LAN_MAX_ROOM_MAPS) {
                Text(if (loadingMap) "Загрузка..." else "＋ Карта")
            }
            OutlinedButton(
                enabled = state.activeMapId.isNotBlank(),
                onClick = {
                    mapForRoomRemoval = state.roomMaps.firstOrNull { it.id == state.activeMapId }
                }
            ) { Text("− Удалить") }
        } else {
            Text("🗺 ${state.mapName.ifBlank { "Нет карты" }}", fontSize = 13.sp)
        }
    }

    if (page == "combat" && state.connected) {
        val ownBonus = activeCharacter?.let { character ->
            val dex = CharacterStorage.loadAttributes(context, character.id)["Ловкость"] ?: 10
            (Math.floorDiv(dex - 10, 2) + DndSheetStorage.load(context, character.id).initiativeBonus)
                .coerceIn(-99, 99)
        } ?: 0
        Dialog(onDismissRequest = { page = "map" },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                CombatScreen(
                    lan = state, isMaster = isMaster, ownInitiativeBonus = ownBonus,
                    onBack = { page = "map" }, onPrepare = { session.prepareCombat() },
                    onAddPlayer = { session.addCombatPlayer(it) },
                    onAddMarker = { session.addCombatMarker(it) },
                    onAddCustom = { name, bonus -> session.addCombatCustom(name, bonus) },
                    onRemove = { session.removeCombatFighter(it) },
                    onMasterRoll = { id, roll, bonus -> session.masterCombatInitiative(id, roll, bonus) },
                    onPlayerRoll = { session.combatCommand("roll", roll = it) },
                    onStart = { session.startCombat() },
                    onFinish = { session.finishCombat() },
                    onNext = { if (isMaster) session.nextCombatTurn() else session.combatCommand("next") },
                    onPrevious = { session.previousCombatTurn() },
                    onRefund = { session.refundCombatAction() },
                    onAction = { action, note ->
                        if (isMaster) session.masterCombatAction(action, note)
                        else session.combatCommand("act", label = action, details = note)
                    }
                )
            }
        }
        return
    }

    if (page == "notes" && state.connected && !isMaster && activeCharacter != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { page = journalReturnPage },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize()) {
                RpgPersonalNotesScreen(activeCharacter.id, onBack = { page = journalReturnPage })
            }
        }
        return
    }

    if (page == "quests" && state.connected) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest={page=journalReturnPage},
            properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)
        ) {
            Surface(Modifier.fillMaxSize()) {
                if(isMaster) {
                    RpgQuestJournalScreen(
                        quests=state.quests, onBack={page=journalReturnPage},
                        onSave={session.setCampaignQuests(it)},
                        recipients=state.players.map { it.name }, title="Задания кампании · Мастер"
                    )
                } else {
                    val id = activeCharacter?.id
                    val cached = if(id!=null && state.roomId.isNotBlank())
                        RpgQuestStorage.load(context,RpgQuestStorage.clientKey(state.roomId,id))
                    else emptyList()
                    RpgQuestJournalScreen(quests=if(state.questsVersion>0) state.quests else cached,
                        onBack={page=journalReturnPage}, title="Задания кампании")
                }
            }
        }
        return
    }

    if (fullscreen && state.connected && bitmap != null && page == "map" &&
        !showAddMapChooser && mapBeingAdded == null && mapError == null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullscreen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMaster) {
                    // Drawer is in the Row, not an overlay. The map gets the remaining width.
                    Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.width(26.dp).fillMaxHeight(),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                TextButton(
                                    onClick = {
                                        masterToolsOpen = !masterToolsOpen
                                        if (masterToolsOpen) masterSectionMenu = true
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text(if (masterToolsOpen) "‹" else "›", fontSize = 24.sp) }
                            }
                        }
                        if (masterToolsOpen) {
                            Column(
                                Modifier.width(164.dp).fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .verticalScroll(rememberScrollState()).padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (masterSectionMenu) {
                                    Text("Разделы", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    listOf(
                                        Triple("maps", "🗺 Карты", "Карты"),
                                        Triple("markers", "📍 Метки", "Метки"),
                                        Triple("players", "👥 Игроки", "Игроки"),
                                        Triple("combat", "⚔ Бой", "Бой"),
                                        Triple("quests", "📖 Задания", "Задания"),
                                        Triple("world", "🌍 Мир", "Мир"),
                                        Triple("fog", "☁ Туман", "Туман"),
                                        Triple("admin", "⚙ Комната", "Комната")
                                    ).forEach { (id, label, _) ->
                                        TextButton(onClick = {
                                            masterToolsSection = id
                                            masterSectionMenu = false
                                            // Editing the fog is an explicit tool, never left active behind another section.
                                            if (id != "fog") fogEditing = false
                                            if (id != "markers") addingMarker = false
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Text(label, fontSize = 13.sp)
                                        }
                                    }
                                } else {
                                    TextButton(onClick = { masterSectionMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text("‹ Разделы", fontSize = 13.sp)
                                    }
                                    when (masterToolsSection) {
                                        "maps" -> {
                                            Text("Карты", fontWeight = FontWeight.Bold)
                                            roomMapControls()
                                        }
                                        "markers" -> {
                                            Text("Метки", fontWeight = FontWeight.Bold)
                                            Button(onClick = {
                                                addingMarker = !addingMarker
                                                fogEditing = false
                                            }, modifier = Modifier.fillMaxWidth()) {
                                                Text(if (addingMarker) "Отмена" else "＋ Метка")
                                            }
                                            if (addingMarker) Text("Нажми точку на карте", fontSize = 11.sp)
                                            state.markers.forEach { marker ->
                                                TextButton(onClick = { selectedMarker = marker }, modifier = Modifier.fillMaxWidth()) {
                                                    Text("${markerIcon(marker.type)} ${marker.name}", maxLines = 2, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                        "quests" -> {
                                            Text("Задания", fontWeight = FontWeight.Bold)
                                            Button(onClick={journalReturnPage="map";page="quests"},
                                                modifier=Modifier.fillMaxWidth()) { Text("📖 Открыть редактор") }
                                            Text("Создание, цели и статусы",fontSize=11.sp)
                                        }
                                        "world" -> {
                                            Text("Мир и отдых", fontWeight = FontWeight.Bold)
                                            OutlinedButton(onClick = { showWalletEditor = true },
                                                modifier = Modifier.fillMaxWidth()) {
                                                Text("🪙 Монеты игроков", fontSize = 12.sp)
                                            }
                                            Text("День ${state.adventure.day} · ${if (state.adventure.phase == "night") "Ночь" else "День"}", fontSize = 12.sp)
                                            if (state.adventure.phase == "day") {
                                                Text(if (state.adventure.campMarkerId.isBlank()) "Ожидание лагеря" else "Лагерь готов", fontSize = 11.sp)
                                                Button(onClick = { session.adventureCommand("end_day") },
                                                    enabled = state.adventure.campMarkerId.isNotBlank(),
                                                    modifier = Modifier.fillMaxWidth()) { Text("Завершить день", fontSize = 12.sp) }
                                            } else {
                                                Button(onClick = { session.adventureCommand("start_day") },
                                                    modifier = Modifier.fillMaxWidth()) { Text("Начать день", fontSize = 12.sp) }
                                                Text("История появления героя", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                state.players.forEach { player ->
                                                    val id = state.characterSnapshots.firstOrNull { it.ownerId == player.id }
                                                        ?.payload?.let { runCatching { JSONObject(it).optLong("id", -1L) }.getOrNull() }
                                                    if (id != null && id > 0) {
                                                        TextButton(onClick = { heroStoryPlayerId = player.id }, modifier = Modifier.fillMaxWidth()) {
                                                            Text((if (id.toString() in state.adventure.heroStories) "↻ Повторить · " else "＋ История · ") + player.name, fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        "combat" -> {
                                            Text("Боевой режим", fontWeight = FontWeight.Bold)
                                            Text(when (state.combat.phase) {
                                                "active" -> "Раунд ${state.combat.round} · ${state.combat.current?.name.orEmpty()}"
                                                "setup" -> "Сбор инициативы"
                                                else -> "Бой не начат"
                                            }, fontSize = 12.sp)
                                            Button(onClick = openCombatOnMap,
                                                modifier = Modifier.fillMaxWidth()) { Text("⚔ Пульт боя") }
                                        }
                                        "players" -> {
                                            Text("Игроки", fontWeight = FontWeight.Bold)
                                            if (state.players.isEmpty()) Text("Нет подключённых игроков", fontSize = 12.sp)
                                            state.players.forEach { player ->
                                                TextButton(onClick = { selectedPlayer = player }, modifier = Modifier.fillMaxWidth()) {
                                                    Text(player.name, fontSize = 12.sp, maxLines = 2)
                                                }
                                            }
                                        }
                                        "fog" -> {
                                            Text("Туман войны", fontWeight = FontWeight.Bold)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Вкл.", fontSize = 12.sp)
                                                Switch(checked = state.fog.enabled, onCheckedChange = {
                                                    session.setFogEnabled(it)
                                                    if (!it) fogEditing = false
                                                })
                                            }
                                            if (state.fog.enabled) {
                                                Button(
                                                    onClick = {
                                                        fogEditing = if (fogEditing && fogErase) false else true
                                                        fogErase = true
                                                        addingMarker = false
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(if (fogEditing && fogErase) "✓ Завершить стирание" else "⌫ Стирать туман", fontSize = 12.sp)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        fogEditing = if (fogEditing && !fogErase) false else true
                                                        fogErase = false
                                                        addingMarker = false
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(if (fogEditing && !fogErase) "✓ Завершить покрытие" else "＋ Вернуть туман", fontSize = 12.sp)
                                                }
                                                Text(
                                                    when {
                                                        fogEditing && fogErase -> "Активен ластик: проводи пальцем по карте"
                                                        fogEditing -> "Активно покрытие: проводи пальцем по карте"
                                                        else -> "Редактирование выключено. Выбери инструмент выше"
                                                    },
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        else -> {
                                            Text("Комната", fontWeight = FontWeight.Bold)
                                            TextButton(onClick = { showPermissions = true }) { Text("⚙ Права") }
                                            TextButton(onClick = { showLogs = true }) { Text("☷ Логи") }
                                            TextButton(onClick = { showRoomInfo = true }) { Text("ⓘ Информация") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!isMaster) Column(Modifier.width(118.dp), verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    if (nearbyInteractions.isNotEmpty()) nearbyControl()
                    campControl()
                    OutlinedButton(onClick = openCombatOnMap,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.combat.phase == "active") "⚔ Раунд ${state.combat.round}" else "⚔ Бой", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick={journalReturnPage="map";page="quests"}, modifier=Modifier.fillMaxWidth()) {
                        Text("📖 Журнал",fontSize=12.sp)
                    }
                    OutlinedButton(onClick={journalReturnPage="map";page="notes"}, modifier=Modifier.fillMaxWidth()) {
                        Text("✎ Заметки",fontSize=12.sp)
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    mapContent(true) // unchanged image, markers, fog, touch gestures and camera
                    MapCombatOverlay(
                        lan = state,
                        isMaster = isMaster,
                        expanded = mapCombatExpanded,
                        onToggle = { mapCombatExpanded = !mapCombatExpanded },
                        onManage = { mapCombatDialog = true },
                        onAction = { action, note ->
                            if (isMaster) session.masterCombatAction(action, note)
                            else session.combatCommand("act", label = action, details = note)
                        },
                        onNext = { if (isMaster) session.nextCombatTurn() else session.combatCommand("next") },
                        onPrevious = { session.previousCombatTurn() },
                        onRefund = { session.refundCombatAction() }
                    )
                }
                Column(
                    Modifier.width(128.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { fullscreen = false }) { Text("Свернуть") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Камера", fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = { cameraZoom = (cameraZoom / 1.35f).coerceAtLeast(1f) }) {
                                Text("−", fontSize = 20.sp)
                            }
                            TextButton(onClick = { cameraZoom = (cameraZoom * 1.35f).coerceAtMost(5f) }) {
                                Text("+", fontSize = 20.sp)
                            }
                        }
                        TextButton(onClick = { cameraZoom = 1f; cameraPan = Offset.Zero }) {
                            Text("Вместить", fontSize = 11.sp)
                        }
                    }
                    moveControl()
                    Text("Игроков: ${state.players.size}", fontSize = 11.sp)
                }
            }
        }
    }

    // In landscape the initiative setup and advanced battle controls are an overlay
    // over the original map, not a separate screen or a replacement for the joystick.
    if (mapCombatDialog && fullscreen && page == "map" && state.connected) {
        val ownBonus = activeCharacter?.let { character ->
            val dex = CharacterStorage.loadAttributes(context, character.id)["Ловкость"] ?: 10
            (Math.floorDiv(dex - 10, 2) + DndSheetStorage.load(context, character.id).initiativeBonus)
                .coerceIn(-99, 99)
        } ?: 0
        Dialog(onDismissRequest = { mapCombatDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                Modifier.fillMaxWidth(.82f).fillMaxHeight(.94f),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 8.dp
            ) {
                CombatScreen(
                    lan = state, isMaster = isMaster, ownInitiativeBonus = ownBonus,
                    onBack = { mapCombatDialog = false },
                    onPrepare = { session.prepareCombat() },
                    onAddPlayer = { session.addCombatPlayer(it) },
                    onAddMarker = { session.addCombatMarker(it) },
                    onAddCustom = { name, bonus -> session.addCombatCustom(name, bonus) },
                    onRemove = { session.removeCombatFighter(it) },
                    onMasterRoll = { id, roll, bonus -> session.masterCombatInitiative(id, roll, bonus) },
                    onPlayerRoll = { session.combatCommand("roll", roll = it) },
                    onStart = { session.startCombat(); mapCombatDialog = false },
                    onFinish = { session.finishCombat(); mapCombatDialog = false },
                    onNext = { if (isMaster) session.nextCombatTurn() else session.combatCommand("next") },
                    onPrevious = { session.previousCombatTurn() },
                    onRefund = { session.refundCombatAction() },
                    onAction = { action, note ->
                        if (isMaster) session.masterCombatAction(action, note)
                        else session.combatCommand("act", label = action, details = note)
                    }
                )
            }
        }
    }

    if (!isMaster && state.connected && page != "map") {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Сетевая игра", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showRoomInfo = true }) { Text("ⓘ Инфо") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = false, onClick = { page = "map" }, label = { Text("Карта") })
                FilterChip(selected = page == "combat", onClick = openCombatOnMap, label = { Text("⚔ Бой") })
                if (!isMaster) {
                    FilterChip(selected = page == "game", onClick = { page = "game" }, label = { Text("Персонаж") })
                    FilterChip(selected = page == "abilities", onClick = { page = "abilities" }, label = { Text("Способности") })
                    FilterChip(selected=false,onClick={journalReturnPage=page;page="quests"},label={Text("Журнал")})
                    FilterChip(selected=false,onClick={journalReturnPage=page;page="notes"},label={Text("Заметки")})
                    if (state.permissions.skillTreeEnabled) FilterChip(
                        selected = page == "talents", onClick = { page = "talents" }, label = { Text("Древо") })
                }
            }
            if (page == "game" && activeCharacter != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Уровень ${activeCharacter.level} · ${activeCharacter.experience}/${activeCharacter.experienceRequired} XP", Modifier.weight(1f))
                    TextButton(onClick = { showExperience = true }) { Text("+ XP") }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (activeCharacter != null) {
                    if (page == "game") {
                        key(masterEditRevision, state.inventoryRevision, activeCharacter.id) {
                        GameScreen(
                            character = activeCharacter,
                            onBack = { page = "map" },
                            onAbilities = { page = "abilities" },
                            onJournal = { journalReturnPage="game";page="quests" },
                            onNotes = { journalReturnPage="game";page="notes" },
                            onMaps = { page = "map" },
                            showNavigation = false,
                            onEvent = { session.sendEvent(it) }
                        )
                        }
                    } else if (page == "talents" && state.permissions.skillTreeEnabled) {
                        SkillTreeScreen(character = activeCharacter, onBack = { page = "game" },
                            onUpdated = { session.sendEvent("Изучил навык древа") })
                    } else {
                        key(masterEditRevision, activeCharacter.id) {
                        AbilitiesScreen(
                            character = activeCharacter,
                            onBack = { page = "game" },
                            onEvent = { session.sendEvent(it) }
                        )
                        }
                    }
                } else {
                    Text("Выбери персонажа для доступа к игровым функциям.")
                }
            }
        }
        return
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Сетевая игра", modifier = Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showRoomInfo = true }) {
                Text("ⓘ", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (!started) {
            Text(if (isMaster) "Создание комнаты мастера" else "Подключение к комнате мастера")
            if (mode == "host") {
                Text("Выбери карту из библиотеки:")
                var mapMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { mapMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(maps.firstOrNull { it.id == selectedMapId }?.name ?: "Нет карт")
                    }
                    DropdownMenu(expanded = mapMenu, onDismissRequest = { mapMenu = false }) {
                        maps.forEach { map ->
                            DropdownMenuItem(text = { Text(map.name) }, onClick = {
                                selectedMapId = map.id
                                mapMenu = false
                            })
                        }
                    }
                }
                OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth()) { Text("＋ Загрузить новую карту") }
                if (characters.isNotEmpty()) {
                    var sourceMenu by remember { mutableStateOf(false) }
                    Text("Перенос меток из личной карты (не персонаж мастера):", fontSize = 13.sp)
                    Box {
                        OutlinedButton(onClick = { sourceMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(characters.firstOrNull { it.id == selectedCharacterId }?.name ?: "Без переноса меток")
                        }
                        DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                            DropdownMenuItem(text = { Text("Без переноса меток") }, onClick = {
                                selectedCharacterId = null
                                sourceMenu = false
                            })
                            characters.forEach { character ->
                                DropdownMenuItem(text = { Text(character.name) }, onClick = {
                                    selectedCharacterId = character.id
                                    sourceMenu = false
                                })
                            }
                        }
                    }
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedMapId != null && !loadingMap,
                    onClick = {
                        val map = maps.firstOrNull { it.id == selectedMapId } ?: return@Button
                        uploadRoomMap(map, true)
                    }
                ) { Text(if (loadingMap) "Подготовка карты..." else "Создать комнату") }
                if (maps.isEmpty()) Text("Добавь изображение прямо здесь — общая библиотека тоже пополнится.")

            } else {
                if (providedGuestCharacter != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Играем за ${providedGuestCharacter.name}", fontWeight = FontWeight.SemiBold)
                            Text("${providedGuestCharacter.race} · ${providedGuestCharacter.profession} · уровень ${providedGuestCharacter.level}",
                                style = MaterialTheme.typography.bodySmall)
                            Text("Другого героя можно выбрать в списке персонажей.",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    Text("Персонаж участника")
                    var guestMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { guestMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(characters.firstOrNull { it.id == selectedGuestCharacterId }?.name ?: "Выбери персонажа")
                        }
                        DropdownMenu(expanded = guestMenu, onDismissRequest = { guestMenu = false }) {
                            characters.forEach { character ->
                                DropdownMenuItem(text = { Text(character.name) }, onClick = {
                                    selectedGuestCharacterId = character.id
                                    guestName = character.name
                                    guestMenu = false
                                })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = guestName,
                        onValueChange = { guestName = it.take(40) },
                        label = { Text("Имя игрока") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                Text("Найденные комнаты в локальной сети", fontWeight = FontWeight.SemiBold)
                Text("Подключитесь к Wi-Fi или точке доступа мастера. Поиск выполняется автоматически.",
                    style = MaterialTheme.typography.bodySmall)
                if (discoveredRooms.isEmpty()) {
                    Text(if (discoveringRooms) "Поиск комнат… Если мастер уже создал комнату, она появится здесь."
                         else "Поиск остановлен. Нажмите «Искать снова».")
                } else {
                    discoveredRooms.forEach { room ->
                        OutlinedButton(
                            onClick = {
                                ip = room.address
                                started = true
                                session.join(room.address, guestName.trim(), selectedGuestCharacterId ?: 0L)
                            },
                            enabled = guestName.isNotBlank() && selectedGuestCharacterId != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text("${room.name}  ·  Подключиться")
                                Text("${room.address} · ${room.via}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = {
                    discoveringRooms = !discoveringRooms
                    if (discoveringRooms) discoveredRooms = emptyList()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (discoveringRooms) "Остановить поиск" else "Искать снова")
                }
                TextButton(onClick = { showManualIp = !showManualIp }) {
                    Text(if (showManualIp) "Скрыть ручное подключение" else "Дополнительно: подключение по IP")
                }
                if (showManualIp) {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it.trim().take(64) },
                        label = { Text("IP телефона мастера") },
                        placeholder = { Text("192.168.1.100") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = ip.isNotBlank() && guestName.isNotBlank() && selectedGuestCharacterId != null,
                        onClick = {
                            started = true
                            session.join(ip.trim(), guestName.trim(), selectedGuestCharacterId ?: 0L)
                        }
                    ) { Text("Подключиться по IP") }
                }
            }
        }
        if (!state.connected && started) {
            Text(state.status, color = MaterialTheme.colorScheme.error)
        }
        if (state.connected) {
            // Horizontal scrolling keeps every navigation action reachable on
            // narrow portrait phones without forcing the user to rotate.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = page == "map", onClick = { page = "map" }, label = { Text("Общая карта") })
                FilterChip(selected = page == "combat", onClick = openCombatOnMap,
                    label = { Text(if (state.combat.phase == "active") "⚔ Р${state.combat.round}" else "⚔ Бой") })
                FilterChip(selected = page == "game", onClick = { page = "game" }, label = { Text("Персонаж") })
                FilterChip(selected = page == "abilities", onClick = { page = "abilities" }, label = { Text("Способности") })
                if (!isMaster && activeCharacter != null) {
                    FilterChip(selected = page == "notes", onClick = {
                        journalReturnPage = "map"; page = "notes"
                    }, label = { Text("Заметки") })
                    FilterChip(selected = page == "quests", onClick = {
                        journalReturnPage = "map"; page = "quests"
                    }, label = { Text("Задания") })
                    if (state.permissions.skillTreeEnabled) FilterChip(
                        selected = page == "talents", onClick = { page = "talents" },
                        label = { Text("Древо") })
                }
            }
            if (isMaster) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPermissions = true }) { Text("⚙ Права") }
                OutlinedButton(onClick = { showLogs = true }) { Text("☷ Журнал") }
                OutlinedButton(onClick={journalReturnPage="map";page="quests"}){Text("📖 Задания")}
            }
            Text("Карта: ${state.mapName.ifBlank { "не выбрана" }}", fontWeight = FontWeight.SemiBold)
            roomMapControls()
            if (bitmap != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { fullscreen = true }) { Text("⛶ На весь экран") }
                    if (canCreateMarker) Button(onClick = { addingMarker = !addingMarker; fogEditing = false }) {
                        Text(if (addingMarker) "Отмена" else "＋ Метка")
                    }
                }
                if (isMaster) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Туман войны")
                        Switch(checked = state.fog.enabled, onCheckedChange = {
                            session.setFogEnabled(it)
                            if (!it) fogEditing = false
                        })
                        if (state.fog.enabled) {
                            TextButton(onClick = {
                                fogEditing = !(fogEditing && fogErase)
                                fogErase = true
                                addingMarker = false
                            }) {
                                Text(if (fogEditing && fogErase) "✓ Завершить стирание" else "⌫ Стирать туман")
                            }
                            TextButton(onClick = {
                                fogEditing = !(fogEditing && !fogErase)
                                fogErase = false
                                addingMarker = false
                            }) {
                                Text(if (fogEditing && !fogErase) "✓ Завершить покрытие" else "＋ Вернуть туман")
                            }
                        }
                    }
                }
                if (addingMarker) Text("Выбери место на карте для новой метки.")
                if (fogEditing) Text("Проводи пальцем по карте: ${if (fogErase) "открыть область" else "вернуть туман"}.")
                if (!isMaster) {
                    if (nearbyInteractions.isNotEmpty()) nearbyControl()
                    campControl()
                }
                mapContent(false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { cameraZoom = (cameraZoom / 1.35f).coerceAtLeast(1f) }) { Text("−") }
                    TextButton(onClick = { cameraZoom = (cameraZoom * 1.35f).coerceAtMost(5f) }) { Text("+") }
                    TextButton(onClick = { cameraZoom = 1f; cameraPan = Offset.Zero }) { Text("Вместить карту") }
                }
                moveControl()
                Text("Игроков: ${state.players.size} · Меток: ${state.markers.size}")
                state.players.forEach { player ->
                    Text("${if (player.id == state.myId) "●" else "○"} ${player.name}")
                }
                if (state.markers.isNotEmpty()) {
                    Text("Отметки", fontWeight = FontWeight.Bold)
                    state.markers.filter { isMaster || state.fog.visible(it.x, it.y) }.forEach { marker ->
                        Text("${markerIcon(marker.type)} ${marker.name}", Modifier.clickable { selectedMarker = marker })
                    }
                }
            } else {
                Text("В комнате пока нет карты. Мастер может загрузить следующую.")
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("← В главное меню") }
        if (started) {
            Text("Для выхода из комнаты вернись в главное меню. Соединение будет закрыто.")
        }
    }
}

private fun markerIcon(type: String): String = RpgCatalog.markerIcon(type)
