package com.example.rpgcreators

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One shared battle dashboard; never simulates dice, never changes HP by itself. */
@Composable
internal fun CombatScreen(
    lan: LanState,
    isMaster: Boolean,
    ownInitiativeBonus: Int,
    onBack: () -> Unit,
    onPrepare: () -> Unit,
    onAddPlayer: (String) -> Unit,
    onAddMarker: (String) -> Unit,
    onAddCustom: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onMasterRoll: (String, Int, Int?) -> Unit,
    onPlayerRoll: (Int) -> Unit,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRefund: () -> Unit,
    onAction: (String, String) -> Unit
) {
    val combat = lan.combat
    val own = combat.fighters.firstOrNull { it.kind == "player" && it.sourceId == lan.myId }
    val ownTurn = combat.phase == "active" && combat.currentId == own?.id
    var newName by remember { mutableStateOf("") }
    var newBonus by remember { mutableStateOf("0") }
    var note by remember(combat.currentId) { mutableStateOf("") }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showAddPlayer by remember { mutableStateOf(false) }
    var showAddMarker by remember { mutableStateOf(false) }

    if (confirmFinish || confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false; confirmReplace = false },
            title = { Text(if (confirmFinish) "Завершить бой?" else "Новый бой?") },
            text = { Text(if (confirmFinish) "Очередь очистится, но журнал останется до следующего боя."
                          else "Текущая очередь будет заменена. Изученные способности и HP персонажей не меняются.") },
            confirmButton = { TextButton(onClick = {
                if (confirmFinish) onFinish() else onPrepare()
                confirmFinish = false; confirmReplace = false
            }) { Text("Подтвердить") } },
            dismissButton = { TextButton(onClick = { confirmFinish = false; confirmReplace = false }) {
                Text("Отмена")
            } }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("⚔ Бой", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("← Карта") }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (combat.phase) {
                "idle" -> {
                    Text("Нет активного боя", style = MaterialTheme.typography.titleMedium)
                    Text("Кубики бросаем на столе, сюда вводим только выпавшие значения. " +
                        "Приложение запомнит инициативу и порядок ходов.")
                    if (isMaster) Button(onClick = onPrepare, modifier = Modifier.fillMaxWidth()) {
                        Text("＋ Подготовить бой")
                    } else Text("Ожидаем, пока мастер начнёт подготовку.")
                }
                "setup" -> {
                    Text("Подготовка инициативы", style = MaterialTheme.typography.titleMedium)
                    Text("Бросьте настоящий d20. Результат и бонусы приложение сложит автоматически.",
                        style = MaterialTheme.typography.bodySmall)
                    if (own != null && !isMaster) {
                        CombatRollEntry(own, ownInitiativeBonus, true) { roll, _ -> onPlayerRoll(roll) }
                    } else if (!isMaster) {
                        Text("Мастер ещё не включил твоего персонажа в очередь.")
                    }
                    if (combat.fighters.isEmpty()) Text("Участников пока нет.")
                    combat.fighters.forEachIndexed { i, fighter ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${i + 1}. ${fighter.name}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    Text(fighter.initiative?.toString() ?: "—", fontWeight = FontWeight.Bold)
                                }
                                Text(when (fighter.kind) {
                                    "player" -> "Персонаж · бонус инициативы ${signedCombat(fighter.initiativeBonus)}"
                                    "marker" -> "Существо на карте"
                                    else -> "Участник мастера"
                                }, style = MaterialTheme.typography.labelSmall)
                                if (isMaster) {
                                    CombatRollEntry(fighter, fighter.initiativeBonus, false) { roll, bonus ->
                                        onMasterRoll(fighter.id, roll, if (fighter.kind == "player") null else bonus)
                                    }
                                    TextButton(onClick = { onRemove(fighter.id) }) { Text("Убрать из боя") }
                                } else if (fighter.id != own?.id) {
                                    Text(if (fighter.initiativeRoll == null) "Ожидаем бросок" else "Инициатива записана",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (isMaster) {
                        HorizontalDivider()
                        Text("Добавить участников", fontWeight = FontWeight.SemiBold)
                        val missingPlayers = lan.players.filter { p ->
                            combat.fighters.none { it.kind == "player" && it.sourceId == p.id }
                        }
                        if (missingPlayers.isNotEmpty()) {
                            OutlinedButton(onClick = { showAddPlayer = !showAddPlayer }, Modifier.fillMaxWidth()) {
                                Text("＋ Подключённый игрок")
                            }
                            if (showAddPlayer) missingPlayers.forEach { p ->
                                TextButton(onClick = { onAddPlayer(p.id); showAddPlayer = false }) { Text(p.name) }
                            }
                        }
                        val missingMarkers = lan.markers.filter { m ->
                            m.type in setOf("Враг", "Босс", "Союзник", "NPC", "Игрок") &&
                                combat.fighters.none { it.kind == "marker" && it.sourceId == m.id }
                        }
                        if (missingMarkers.isNotEmpty()) {
                            OutlinedButton(onClick = { showAddMarker = !showAddMarker }, Modifier.fillMaxWidth()) {
                                Text("＋ Существо с текущей карты")
                            }
                            if (showAddMarker) missingMarkers.forEach { m ->
                                TextButton(onClick = { onAddMarker(m.id); showAddMarker = false }) {
                                    Text("${m.name} (${m.type})")
                                }
                            }
                        }
                        OutlinedTextField(value = newName, onValueChange = { newName = it.take(60) },
                            label = { Text("Другой участник (например, Гоблин 2)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = newBonus,
                                onValueChange = { v -> if (v.length <= 4) newBonus = v },
                                label = { Text("Бонус") }, modifier = Modifier.width(100.dp), singleLine = true)
                            OutlinedButton(enabled = newName.isNotBlank() && newBonus.toIntOrNull() in -99..99,
                                onClick = { onAddCustom(newName, newBonus.toInt()); newName = "" }) {
                                Text("＋ Добавить")
                            }
                        }
                        Button(enabled = combat.ready, onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                            Text("Начать бой")
                        }
                        if (!combat.ready) Text("Для старта нужны результаты d20 у всех участников.",
                            style = MaterialTheme.typography.bodySmall)
                    } else Text("После всех бросков мастер запустит бой.",
                        style = MaterialTheme.typography.bodySmall)
                }
                "active" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Раунд ${combat.round}", style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f))
                        Text("2 действия / ход", style = MaterialTheme.typography.labelMedium)
                    }
                    combat.fighters.forEachIndexed { index, fighter ->
                        val current = fighter.id == combat.currentId
                        Surface(Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (current) MaterialTheme.colorScheme.primaryContainer
                                   else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (current) "▶" else "${index + 1}.", fontWeight = FontWeight.Bold)
                                Column(Modifier.weight(1f)) {
                                    Text(fighter.name, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                                    if (current) Text("Осталось ${2 - fighter.actionsSpent} из 2 действий",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${fighter.initiative ?: "—"}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    val actor = combat.current
                    if (actor != null) {
                        HorizontalDivider()
                        Text("Сейчас ходит ${actor.name}", style = MaterialTheme.typography.titleMedium)
                        if (isMaster || ownTurn) {
                            Text("Выбери совершённое действие. Кубики бросай физически.",
                                style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(value = note, onValueChange = { note = it.take(90) },
                                label = { Text("Необязательно: результат, цель, эффект") },
                                placeholder = { Text("Например: по гоблину, 8 урона") },
                                modifier = Modifier.fillMaxWidth(), maxLines = 2)
                            CombatEngine.ACTIONS.chunked(2).forEach { group ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    group.forEach { action ->
                                        OutlinedButton(modifier = Modifier.weight(1f),
                                            enabled = actor.actionsSpent < 2,
                                            onClick = { onAction(action, note); note = "" }) {
                                            Text(action, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    if (group.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            if (actor.movementAllowed && actor.kind == "player")
                                Text("✓ Перемещение разрешено до конца этого хода. Дистанцию контролирует мастер.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            Button(onClick = onNext, Modifier.fillMaxWidth()) {
                                Text(if (isMaster) "Завершить ход / следующий" else "Передать ход мастеру")
                            }
                            if (isMaster) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = onPrevious) { Text("← Предыдущий") }
                                TextButton(enabled = actor.actionsSpent > 0, onClick = onRefund) {
                                    Text("↶ Вернуть действие")
                                }
                            }
                        } else Text("Ожидаем ход участника. Карту и список способностей можно смотреть свободно.")
                    }
                    if (isMaster) Text("Мастер может переключить ход даже без потраченных действий.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (combat.events.isNotEmpty()) {
                HorizontalDivider()
                TextButton(onClick = { showHistory = !showHistory }) {
                    Text(if (showHistory) "▾ Скрыть журнал боя" else "▸ Журнал боя (${combat.events.size})")
                }
                if (showHistory) combat.events.asReversed().take(20).forEach { event ->
                    Text("${if (event.round > 0) "Р${event.round} · " else ""}${event.text}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (isMaster && combat.phase != "idle") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmFinish = true }, modifier = Modifier.weight(1f)) {
                    Text("Завершить бой")
                }
                OutlinedButton(onClick = { confirmReplace = true }, modifier = Modifier.weight(1f)) {
                    Text("Новый бой")
                }
            }
        }
    }
}

private fun signedCombat(value: Int): String = if (value >= 0) "+$value" else value.toString()

@Composable
private fun CombatRollEntry(
    fighter: CombatFighter,
    bonus: Int,
    own: Boolean,
    onSave: (Int, Int) -> Unit
) {
    var roll by remember(fighter.id, fighter.initiativeRoll) {
        mutableStateOf(fighter.initiativeRoll?.toString().orEmpty())
    }
    var bonusText by remember(fighter.id, fighter.initiativeBonus) {
        mutableStateOf(bonus.toString())
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (own) Text("Твоя инициатива · бонус ${signedCombat(bonus)}",
            fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = roll, onValueChange = { if (it.length <= 2) roll = it },
                label = { Text("d20") }, modifier = Modifier.weight(1f), singleLine = true)
            if (!own && fighter.kind != "player") {
                OutlinedTextField(value = bonusText,
                    onValueChange = { if (it.length <= 4) bonusText = it },
                    label = { Text("Бонус") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Button(enabled = (roll.toIntOrNull() in 1..20) &&
                (own || fighter.kind == "player" || bonusText.toIntOrNull() in -99..99),
                onClick = { onSave(roll.toInt(), if (own || fighter.kind == "player") bonus else bonusText.toInt()) }) {
                Text("Записать")
            }
        }
        if (roll.toIntOrNull() in 1..20) {
            Text("Итого: ${roll.toInt() + (if (own || fighter.kind == "player") bonus else bonusText.toIntOrNull() ?: 0)}",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
