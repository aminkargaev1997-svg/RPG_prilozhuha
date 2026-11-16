package com.example.rpgcreators

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * v27: Thin, optional HUD over the EXISTING LAN map. It neither renders a new map,
 * nor installs pointer handlers on the map. Camera, fog, markers and joystick stay intact.
 * All combat mutations are sent through the host's existing LAN command callbacks.
 */
@Composable
internal fun BoxScope.MapCombatOverlay(
    lan: LanState,
    isMaster: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onManage: () -> Unit,
    onAction: (String, String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRefund: () -> Unit
) {
    val combat = lan.combat
    val actor = combat.current
    val own = combat.fighters.firstOrNull { it.kind == "player" && it.sourceId == lan.myId }
    val canAct = combat.phase == "active" && actor != null &&
        (isMaster || combat.currentId == own?.id)
    var showQueue by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var actionNote by remember(pendingAction) { mutableStateOf("") }

    // In exploration mode, with the HUD closed, the old map is pixel-for-pixel intact.
    if (combat.phase == "idle" && !expanded) return

    if (showQueue) {
        AlertDialog(
            onDismissRequest = { showQueue = false },
            title = { Text("Очередь инициативы") },
            text = {
                Column(Modifier.heightIn(max = 310.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (combat.fighters.isEmpty()) Text("Участники ещё не добавлены")
                    combat.fighters.forEachIndexed { index, fighter ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(if (fighter.id == combat.currentId) "▶" else "${index + 1}.",
                                color = MaterialTheme.colorScheme.primary)
                            Text(fighter.name, Modifier.weight(1f), maxLines = 1)
                            Text(fighter.initiative?.toString() ?: "—", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQueue = false }) { Text("Закрыть") } },
            dismissButton = { TextButton(onClick = { showQueue = false; onManage() }) { Text("Настройки боя") } }
        )
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(action) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (action == "Перемещение")
                            "После подтверждения можно двигаться существующим джойстиком на карте. Дистанцию контролирует мастер."
                        else "Выполни действие за столом с настоящими кубиками. Результат можно записать ниже.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = actionNote,
                        onValueChange = { actionNote = it.take(90) },
                        label = { Text("Цель, результат или заметка (необязательно)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAction(action, actionNote)
                    pendingAction = null
                }) { Text("Подтвердить · −1 действие") }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Отмена") } }
        )
    }

    // A small status chip is always available; no combat panel is forced onto the map.
    Surface(
        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 5.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = .95f)
    ) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                when (combat.phase) {
                    "active" -> "⚔ Р${combat.round} · ${actor?.name.orEmpty()} · ${2 - (actor?.actionsSpent ?: 0)}/2"
                    "setup" -> "⚔ Сбор инициативы"
                    else -> "⚔ Бой"
                },
                fontSize = 11.sp, maxLines = 1,
                fontWeight = if (combat.phase == "active") FontWeight.SemiBold else FontWeight.Normal
            )
            TextButton(onClick = onToggle, contentPadding = PaddingValues(horizontal = 5.dp)) {
                Text(if (expanded) "Скрыть" else "Действия", fontSize = 11.sp)
            }
            TextButton(onClick = { showQueue = true }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                Text("Очередь", fontSize = 11.sp)
            }
        }
    }

    if (expanded) {
        Surface(
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = 45.dp, end = 6.dp)
                .width(194.dp)
                .heightIn(max = 280.dp),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = .96f)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Бой", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    TextButton(onClick = onToggle, contentPadding = PaddingValues(2.dp)) {
                        Text("✕", fontSize = 15.sp)
                    }
                }
                when (combat.phase) {
                    "idle" -> {
                        Text("Бой пока не начат", fontSize = 11.sp)
                        if (isMaster) Button(onClick = onManage, Modifier.fillMaxWidth()) { Text("Подготовить бой", fontSize = 11.sp) }
                        else Text("Мастер начнёт подготовку инициативы.", fontSize = 11.sp)
                    }
                    "setup" -> {
                        Text("Бросайте настоящие кубики и вводите результаты.", fontSize = 11.sp)
                        Button(onClick = onManage, Modifier.fillMaxWidth()) {
                            Text(if (isMaster) "Управлять инициативой" else "Ввести инициативу", fontSize = 11.sp)
                        }
                    }
                    "active" -> {
                        Text("Ход: ${actor?.name.orEmpty()}", fontSize = 12.sp, maxLines = 1)
                        Text("Действий: ${2 - (actor?.actionsSpent ?: 0)} из 2", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary)
                        if (canAct) {
                            CombatEngine.ACTIONS.forEach { action ->
                                OutlinedButton(
                                    onClick = { pendingAction = action },
                                    enabled = (actor?.actionsSpent ?: 2) < 2,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) { Text(action, fontSize = 11.sp, maxLines = 1) }
                            }
                            if (actor?.movementAllowed == true) {
                                Text("✓ Перемещение разрешено джойстиком", fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        } else Text("Ожидание своего хода", fontSize = 11.sp)
                        Button(
                            onClick = onNext,
                            enabled = isMaster || canAct,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 5.dp)
                        ) { Text(if (isMaster) "Следующий ход" else "Завершить ход", fontSize = 11.sp) }
                        if (isMaster) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(onClick = onPrevious, contentPadding = PaddingValues(3.dp)) {
                                    Text("← Ход", fontSize = 11.sp)
                                }
                                TextButton(onClick = onRefund,
                                    enabled = (actor?.actionsSpent ?: 0) > 0,
                                    contentPadding = PaddingValues(3.dp)) {
                                    Text("↶ Действие", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                    Text("⚙ Пульт и журнал", fontSize = 11.sp)
                }
            }
        }
    }
}
