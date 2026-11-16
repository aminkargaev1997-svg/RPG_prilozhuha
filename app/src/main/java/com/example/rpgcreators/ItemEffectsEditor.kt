package com.example.rpgcreators

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Collapsed, context-only controls. Editing a single item never alters the catalog. */
@Composable
fun ItemEffectsEditor(effects: List<ItemEffect>, onChange: (List<ItemEffect>) -> Unit) {
    var kind by remember { mutableStateOf("attribute") }
    var target by remember { mutableStateOf("Сила") }
    var amount by remember { mutableIntStateOf(1) }
    var kindOpen by remember { mutableStateOf(false) }
    var targetOpen by remember { mutableStateOf(false) }
    val kinds = listOf("attribute" to "Характеристика", "ac" to "Бонус КД", "armor" to "Базовый КД брони", "skill" to "Навык", "save" to "Спасбросок", "maxhp" to "Максимум HP")
    val targets = when (kind) {
        "attribute", "save" -> listOf("Сила", "Ловкость", "Телосложение", "Интеллект", "Мудрость", "Харизма")
        "skill" -> listOf("Акробатика", "Анализ", "Атлетика", "Внимательность", "Выживание", "Выступление", "Запугивание", "История", "Ловкость рук", "Магия", "Медицина", "Обман", "Природа", "Проницательность", "Религия", "Скрытность", "Убеждение", "Уход за животными")
        "armor" -> listOf("heavy", "medium", "light")
        else -> listOf("")
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (effects.isEmpty()) Text("Нет эффектов. Предмет пока не меняет характеристики.", style = MaterialTheme.typography.bodySmall)
        effects.forEachIndexed { i, e ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(e.readable(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onChange(effects.filterIndexed { j, _ -> i != j }) }) { Text("Убрать") }
            }
        }
        Box {
            OutlinedButton(onClick = { kindOpen = true }) { Text(kinds.first { it.first == kind }.second) }
            DropdownMenu(kindOpen, onDismissRequest = { kindOpen = false }) {
                kinds.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = {
                    kind = value; target = when(value) { "armor" -> "heavy"; "ac", "maxhp" -> ""; else -> "Сила" }
                    kindOpen = false
                }) }
            }
        }
        if (kind in setOf("attribute", "save", "skill", "armor")) Box {
            OutlinedButton(onClick = { targetOpen = true }) { Text(if (target == "heavy") "Тяжёлая броня" else if (target == "medium") "Средняя броня" else if (target == "light") "Лёгкая броня" else target) }
            DropdownMenu(targetOpen, onDismissRequest = { targetOpen = false }) {
                targets.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { target = t; targetOpen = false }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { amount = (amount - 1).coerceAtLeast(-50) }) { Text("−") }
            Text("$amount", modifier = Modifier.padding(12.dp))
            OutlinedButton(onClick = { amount = (amount + 1).coerceAtMost(99) }) { Text("+") }
        }
        Button(enabled = effects.size < 24 && (kind !in setOf("attribute", "skill", "save", "armor") || target in targets), onClick = {
            onChange(effects + ItemEffect(kind, target, amount))
        }) { Text("Добавить эффект") }
        Text("Эффект работает, только когда предмет надет. Значения сохраняются автоматически.", style = MaterialTheme.typography.bodySmall)
    }
}
