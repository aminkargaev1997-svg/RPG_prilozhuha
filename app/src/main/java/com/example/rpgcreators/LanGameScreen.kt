package com.example.rpgcreators

import android.graphics.BitmapFactory
import android.net.Uri
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.util.UUID

@Composable
fun LanGameScreen(
    modifier: Modifier = Modifier,
    characters: List<Character>,
    onBack: () -> Unit,
    isMaster: Boolean = false,
    onCharacterUpdate: (Character) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val maps = remember { CharacterStorage.loadMaps(context) }
    var selectedCharacterId by remember { mutableStateOf(characters.firstOrNull()?.id) }
    var selectedMapId by remember { mutableStateOf(maps.firstOrNull()?.id) }
    var ip by remember { mutableStateOf("") }
    var guestName by remember { mutableStateOf(characters.firstOrNull()?.name ?: "Игрок") }
    var mode by remember { mutableStateOf(if (isMaster) "host" else "guest") }
    var showPermissions by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showExperience by remember { mutableStateOf(false) }
    var xpInput by remember { mutableStateOf("") }
    var movingPlayerId by remember { mutableStateOf<String?>(null) }
    var started by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf("map") }
    var fullscreen by remember { mutableStateOf(false) }
    var showRoomInfo by remember { mutableStateOf(false) }
    var moveMarkerId by remember { mutableStateOf<String?>(null) }
    var movingOwnPlayer by remember { mutableStateOf(false) }
    var previewPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var lastMoveSentAt by remember { mutableLongStateOf(0L) }
    var selectedPlayer by remember { mutableStateOf<LanPlayer?>(null) }
    var observingPlayerId by remember { mutableStateOf<String?>(null) }
    var observationTab by remember { mutableStateOf("character") }
    var selectedMarker by remember { mutableStateOf<LanMarker?>(null) }
    var addingMarker by remember { mutableStateOf(false) }
    var pendingPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var markerName by remember { mutableStateOf("") }
    var markerDescription by remember { mutableStateOf("") }
    var markerType by remember { mutableStateOf("Обычная") }
    var selectedGuestCharacterId by remember { mutableStateOf(characters.firstOrNull()?.id) }
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
    val activity = remember(context) {
        var current: Context = context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        current as? Activity
    }
    DisposableEffect(fullscreen, activity) {
        val oldOrientation = activity?.requestedOrientation
        val oldUi = activity?.window?.decorView?.systemUiVisibility
        if (fullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            activity?.window?.decorView?.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    )
        }
        onDispose {
            if (oldOrientation != null) activity?.requestedOrientation = oldOrientation
            if (oldUi != null) activity?.window?.decorView?.systemUiVisibility = oldUi
        }
    }
    DisposableEffect(session) { onDispose { session.close() } }

    val bitmap = remember(state.image) {
        state.image?.let { bytes ->
            try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            catch (_: Exception) { null }
        }
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
                        put("inventory", JSONArray().apply {
                            CharacterStorage.loadInventory(context, latest.id).forEach { item ->
                                put(JSONObject().put("id", item.id).put("name", item.name).put("quantity", item.quantity)
                                    .put("description", item.description))
                            }
                        })
                        put("abilities", JSONArray().apply {
                            CharacterStorage.loadAbilities(context, latest.id).forEach { ability ->
                                put(JSONObject().put("id", ability.id).put("name", ability.name).put("description", ability.description)
                                    .put("activator", ability.activatorDescription)
                                    .put("passive", ability.passiveDescription).put("used", ability.isUsed))
                            }
                        })
                        val allAttributes = linkedMapOf(
                            "Сила" to 10, "Ловкость" to 10, "Телосложение" to 10,
                            "Интеллект" to 10, "Мудрость" to 10, "Харизма" to 10
                        )
                        allAttributes.putAll(CharacterStorage.loadAttributes(context, latest.id))
                        put("attributes", JSONObject(allAttributes))
                        put("equipment", JSONObject(CharacterStorage.loadEquipment(context, latest.id)))
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
                    val updated = activeCharacter.addExperience(amount)
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

    selectedPlayer?.let { player ->
        AlertDialog(
            onDismissRequest = { selectedPlayer = null },
            title = { Text(player.name) },
            text = {
                Column {
                    Text(if (player.id == state.myId) "Твой персонаж" else "Игрок в комнате")
                    if (isMaster && player.id != state.myId) {
                        TextButton(onClick = {
                            observingPlayerId = player.id; observationTab = "character"; selectedPlayer = null
                        }) { Text("Управлять персонажем") }
                    }
                }
            },
            confirmButton = {
                if (player.id == state.myId || isMaster) {
                    TextButton(onClick = {
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
                if (player.id == state.myId || isMaster) {
                    TextButton(onClick = { selectedPlayer = null }) { Text("Отмена") }
                }
            }
        )
    }

    selectedMarker?.let { marker ->
        AlertDialog(
            onDismissRequest = { selectedMarker = null },
            title = { Text("${markerIcon(marker.type)} ${marker.name}") },
            text = { Text(marker.description.ifBlank { "Тип: ${marker.type}" }) },
            confirmButton = {
                Row {
                    if (isMaster || (marker.ownerId == state.myId && state.permissions.moveOwnMarkers)) TextButton(onClick = {
                        moveMarkerId = marker.id
                        movingOwnPlayer = false
                        previewPosition = marker.x to marker.y
                        selectedMarker = null
                    }) { Text("Двигать") }
                    if (isMaster || (marker.ownerId == state.myId && state.permissions.deleteOwnMarkers)) TextButton(onClick = {
                        session.deleteMarker(marker.id)
                        selectedMarker = null
                    }) { Text("Удалить") }
                }
            },
            dismissButton = { TextButton(onClick = { selectedMarker = null }) { Text("Закрыть") } }
        )
    }

    pendingPoint?.let { point ->
        AlertDialog(
            onDismissRequest = { pendingPoint = null },
            title = { Text("Новая метка") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = markerName, onValueChange = { markerName = it.take(80) },
                        label = { Text("Название") }, singleLine = true
                    )
                    OutlinedTextField(
                        value = markerDescription, onValueChange = { markerDescription = it.take(300) },
                        label = { Text("Описание") }
                    )
                    var typesExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { typesExpanded = true }) { Text("${markerIcon(markerType)} $markerType") }
                        DropdownMenu(expanded = typesExpanded, onDismissRequest = { typesExpanded = false }) {
                            listOf("Обычная", "Сундук", "Враг", "NPC", "Задание", "Босс", "Опасность", "Другое").forEach { type ->
                                DropdownMenuItem(text = { Text("${markerIcon(type)} $type") }, onClick = {
                                    markerType = type
                                    typesExpanded = false
                                })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = markerName.isNotBlank(), onClick = {
                    session.addMarker(LanMarker(
                        UUID.randomUUID().toString(), markerName.trim(), markerDescription.trim(),
                        markerType, point.first, point.second
                    ))
                    markerName = ""
                    markerDescription = ""
                    pendingPoint = null
                    addingMarker = false
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { pendingPoint = null }) { Text("Отмена") } }
        )
    }

    val mapContent: @Composable () -> Unit = {
        val image = bitmap
        if (image != null) {
            val aspect = image.width.toFloat() / image.height.coerceAtLeast(1)
            BoxWithConstraints(
                Modifier.fillMaxWidth().aspectRatio(aspect)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                Image(
                    bitmap = image,
                    contentDescription = "Общая карта",
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(session, addingMarker) {
                            detectTapGestures { offset ->
                                val x = (offset.x / size.width).coerceIn(0f, 1f)
                                val y = (offset.y / size.height).coerceIn(0f, 1f)
                                if (addingMarker && (isMaster || state.permissions.createMarkers)) {
                                    pendingPoint = x to y
                                }
                            }
                        },
                    contentScale = ContentScale.FillBounds
                )
                state.markers.forEach { marker ->
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
                            .background(Color(0xFFFFC107))
                            .border(1.dp, Color.White, CircleShape)
                            .clickable { selectedMarker = marker },
                        contentAlignment = Alignment.Center
                    ) { Text(markerIcon(marker.type), fontSize = 15.sp) }
                }
                state.players.forEach { player ->
                    val mine = player.id == state.myId
                    val drawX = if (movingOwnPlayer && movingPlayerId == player.id) previewPosition?.first ?: player.x else player.x
                    val drawY = if (movingOwnPlayer && movingPlayerId == player.id) previewPosition?.second ?: player.y else player.y
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
                }
            }
        }
    }

    val latestState by rememberUpdatedState(state)
    val canCreateMarker = isMaster || state.permissions.createMarkers
    val moveControl: @Composable () -> Unit = {
        val active = movingOwnPlayer || moveMarkerId != null
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                when {
                    movingOwnPlayer -> "Персонаж"
                    moveMarkerId != null -> "Выбранная метка"
                    else -> "Выбери точку"
                },
                fontSize = 12.sp
            )
            Surface(
                modifier = Modifier.size(110.dp)
                    .pointerInput(active, moveMarkerId, movingOwnPlayer) {
                        if (active) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val dx = dragAmount.x * 0.45f / size.width.coerceAtLeast(1)
                                val dy = dragAmount.y * 0.45f / size.height.coerceAtLeast(1)
                                val current = previewPosition ?: if (movingOwnPlayer) {
                                    latestState.players.firstOrNull { it.id == movingPlayerId }
                                        ?.let { it.x to it.y }
                                } else {
                                    latestState.markers.firstOrNull { it.id == moveMarkerId }
                                        ?.let { it.x to it.y }
                                }
                                if (current != null) {
                                    val nextX = (current.first + dx).coerceIn(0f, 1f)
                                    val nextY = (current.second + dy).coerceIn(0f, 1f)
                                    previewPosition = nextX to nextY
                                    val now = android.os.SystemClock.uptimeMillis()
                                    if (now - lastMoveSentAt >= 40L) {
                                        lastMoveSentAt = now
                                        if (movingOwnPlayer) {
                                            if (isMaster) session.movePlayerAsMaster(movingPlayerId ?: "", nextX, nextY)
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
            if (active) {
                TextButton(onClick = {
                    previewPosition?.let { point ->
                        if (movingOwnPlayer) {
                            if (isMaster) session.movePlayerAsMaster(movingPlayerId ?: "", point.first, point.second)
                            else session.move(point.first, point.second)
                        } else {
                            latestState.markers.firstOrNull { it.id == moveMarkerId }
                                ?.let { session.updateMarker(it.copy(x = point.first, y = point.second)) }
                        }
                    }
                    moveMarkerId = null
                    movingOwnPlayer = false
                    movingPlayerId = null
                    previewPosition = null
                }) { Text("Готово") }
            }
        }
    }

    if (fullscreen && state.connected && bitmap != null && page == "map") {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullscreen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.width(120.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Метки", fontWeight = FontWeight.Bold)
                    if (isMaster) {
                        TextButton(onClick = { showPermissions = true }) { Text("⚙ Права") }
                        TextButton(onClick = { showLogs = true }) { Text("☷ Логи") }
                    }
                    if (canCreateMarker) Button(onClick = { addingMarker = !addingMarker }) {
                        Text(if (addingMarker) "Отмена" else "＋ Метка")
                    }
                    if (addingMarker) Text("Нажми на карту", fontSize = 11.sp)
                    state.markers.forEach { marker ->
                        TextButton(onClick = { selectedMarker = marker }) {
                            Text("${markerIcon(marker.type)} ${marker.name}", maxLines = 2)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    mapContent()
                }
                Column(
                    Modifier.width(128.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { fullscreen = false }) { Text("Свернуть") }
                    moveControl()
                    Text("Игроков: ${state.players.size}", fontSize = 11.sp)
                }
            }
        }
    }

    if (!isMaster && state.connected && bitmap != null && page != "map") {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Сетевая игра", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showRoomInfo = true }) { Text("ⓘ Инфо") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = false, onClick = { page = "map" }, label = { Text("Карта") })
                if (!isMaster) {
                    FilterChip(selected = page == "game", onClick = { page = "game" }, label = { Text("Персонаж") })
                    FilterChip(selected = page == "abilities", onClick = { page = "abilities" }, label = { Text("Способности") })
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
                        key(masterEditRevision, activeCharacter.id) {
                            GameScreen(
                                character = activeCharacter,
                                onBack = { page = "map" },
                                onAbilities = { page = "abilities" },
                                onMaps = { page = "map" },
                                showNavigation = false,
                                onEvent = { session.sendEvent(it) }
                            )
                        }
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
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedMapId != null,
                    onClick = {
                        val map = maps.firstOrNull { it.id == selectedMapId } ?: return@Button
                        val roomName = "Мастер"
                        started = true
                        scope.launch {
                            try {
                                val bytes = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(Uri.parse(map.imageUri))?.use { stream ->
                                        val output = java.io.ByteArrayOutputStream()
                                        val buffer = ByteArray(8192)
                                        while (output.size() <= LAN_MAX_IMAGE) {
                                            val count = stream.read(buffer)
                                            if (count < 0) break
                                            output.write(buffer, 0, count)
                                        }
                                        output.toByteArray()
                                    } ?: error("Не удалось открыть изображение")
                                }
                                if (bytes.size > LAN_MAX_IMAGE) error("Карта больше 4 МБ. Выбери изображение поменьше.")
                                session.host(roomName, map.name, bytes)
                            } catch (e: Exception) {
                                state = state.copy(status = "Ошибка карты: ${e.message}")
                                started = false
                            }
                        }
                    }
                ) { Text("Создать комнату") }
                if (maps.isEmpty()) Text("Сначала добавь изображение в библиотеку карт через обычный режим игры.")

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
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP телефона хоста") },
                    placeholder = { Text("192.168.1.100") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = ip.isNotBlank() && guestName.isNotBlank(),
                    onClick = {
                        started = true
                        session.join(ip.trim(), guestName.trim())
                    }
                ) { Text("Подключиться") }
            }
        }
        if (!state.connected && started) {
            Text(state.status, color = MaterialTheme.colorScheme.error)
        }
        if (state.connected && bitmap != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = page == "map", onClick = { page = "map" }, label = { Text("Общая карта") })
                FilterChip(selected = page == "game", onClick = { page = "game" }, label = { Text("Персонаж") })
                FilterChip(selected = page == "abilities", onClick = { page = "abilities" }, label = { Text("Способности") })
            }
            if (isMaster) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPermissions = true }) { Text("⚙ Права") }
                OutlinedButton(onClick = { showLogs = true }) { Text("☷ Журнал") }
            }
            Text("Карта: ${state.mapName}", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { fullscreen = true }) { Text("⛶ На весь экран") }
                if (canCreateMarker) Button(onClick = { addingMarker = !addingMarker }) {
                    Text(if (addingMarker) "Отмена" else "＋ Метка")
                }
            }
            if (addingMarker) Text("Выбери место на карте для новой метки.")
            mapContent()
            moveControl()
            Text("Игроков: ${state.players.size} · Меток: ${state.markers.size}")
            state.players.forEach { player ->
                Text("${if (player.id == state.myId) "●" else "○"} ${player.name}")
            }
            if (state.markers.isNotEmpty()) {
                Text("Отметки", fontWeight = FontWeight.Bold)
                state.markers.forEach { marker ->
                    Text("${markerIcon(marker.type)} ${marker.name}", Modifier.clickable { selectedMarker = marker })
                }
            }
        } else if (started && state.connected) {
            CircularProgressIndicator()
            Text("Ожидаем карту...")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("← В главное меню") }
        if (started) {
            Text("Для выхода из комнаты вернись в главное меню. Соединение будет закрыто.")
        }
    }
}

private fun markerIcon(type: String): String = when (type) {
    "Сундук" -> "📦"
    "Враг" -> "⚔"
    "NPC" -> "👤"
    "Задание" -> "📜"
    "Босс" -> "💀"
    "Опасность" -> "⚠"
    "Другое" -> "◆"
    else -> "📍"
}
