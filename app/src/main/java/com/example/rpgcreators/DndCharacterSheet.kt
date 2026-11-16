package com.example.rpgcreators

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject

/** Optional D&D 5e-style details; stored independently to preserve existing characters. */
data class DndSheet(
    val armorClass: Int = 10,
    val initiativeBonus: Int = 0,
    val speedFeet: Int = 30,
    val proficiencyOverride: Int? = null,
    val temporaryHp: Int = 0,
    val hitDie: Int = 8,
    val hitDiceRemaining: Int = 1,
    val deathSuccesses: Int = 0,
    val deathFailures: Int = 0,
    val spellAbility: String = "Интеллект",
    val spellSlotsMax: List<Int> = List(9) { 0 },
    val spellSlotsRemaining: List<Int> = List(9) { 0 },
    val saveProficiencies: Set<String> = emptySet(),
    val skillProficiencies: Set<String> = emptySet(),
    val conditions: String = "",
    val concentration: String = ""
)

object DndSheetStorage {
    private const val PREFS = "rpg_creators_dnd_sheets_v1"
    fun load(context: Context, id: Long): DndSheet {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(id.toString(), null)
            ?: return DndSheet()
        return runCatching {
            val j = JSONObject(raw)
            fun ints(key: String) = List(9) { index -> j.optJSONArray(key)?.optInt(index, 0)?.coerceIn(0, 99) ?: 0 }
            fun strings(key: String) = buildSet {
                val a = j.optJSONArray(key) ?: return@buildSet
                for (i in 0 until a.length()) add(a.optString(i))
            }
            DndSheet(
                armorClass = j.optInt("ac", 10).coerceIn(0, 99),
                initiativeBonus = j.optInt("initiative", 0).coerceIn(-99, 99),
                speedFeet = j.optInt("speed", 30).coerceIn(0, 999),
                proficiencyOverride = if (j.isNull("proficiency") || !j.has("proficiency")) null else j.optInt("proficiency").coerceIn(0, 30),
                temporaryHp = j.optInt("tempHp", 0).coerceIn(0, 99999),
                hitDie = j.optInt("hitDie", 8).coerceIn(1, 100),
                hitDiceRemaining = j.optInt("hitDice", 1).coerceIn(0, 99),
                deathSuccesses = j.optInt("deathSuccess", 0).coerceIn(0, 3),
                deathFailures = j.optInt("deathFailure", 0).coerceIn(0, 3),
                spellAbility = j.optString("spellAbility", "Интеллект"),
                spellSlotsMax = ints("slotsMax"),
                spellSlotsRemaining = ints("slotsLeft"),
                saveProficiencies = strings("saves"), skillProficiencies = strings("skills"),
                conditions = j.optString("conditions", ""), concentration = j.optString("concentration", "")
            )
        }.getOrDefault(DndSheet())
    }

    fun save(context: Context, id: Long, s: DndSheet): Boolean {
        val json = JSONObject().apply {
            put("ac", s.armorClass); put("initiative", s.initiativeBonus); put("speed", s.speedFeet)
            put("proficiency", s.proficiencyOverride ?: JSONObject.NULL)
            put("tempHp", s.temporaryHp); put("hitDie", s.hitDie); put("hitDice", s.hitDiceRemaining)
            put("deathSuccess", s.deathSuccesses); put("deathFailure", s.deathFailures)
            put("spellAbility", s.spellAbility)
            put("slotsMax", JSONArray(s.spellSlotsMax)); put("slotsLeft", JSONArray(s.spellSlotsRemaining))
            put("saves", JSONArray(s.saveProficiencies.toList())); put("skills", JSONArray(s.skillProficiencies.toList()))
            put("conditions", s.conditions); put("concentration", s.concentration)
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(id.toString(), json.toString()).commit()
    }
}

private val sheetAttributes = listOf("Сила", "Ловкость", "Телосложение", "Интеллект", "Мудрость", "Харизма")
private val sheetSkills = listOf(
    "Акробатика" to "Ловкость", "Анализ" to "Интеллект", "Атлетика" to "Сила",
    "Внимательность" to "Мудрость", "Выживание" to "Мудрость", "Выступление" to "Харизма",
    "Запугивание" to "Харизма", "История" to "Интеллект", "Ловкость рук" to "Ловкость",
    "Магия" to "Интеллект", "Медицина" to "Мудрость", "Обман" to "Харизма",
    "Природа" to "Интеллект", "Проницательность" to "Мудрость", "Религия" to "Интеллект",
    "Скрытность" to "Ловкость", "Убеждение" to "Харизма", "Уход за животными" to "Мудрость"
)
private fun signed(n: Int): String = if (n >= 0) "+$n" else "$n"
private fun modifier(score: Int): Int = Math.floorDiv(score - 10, 2)

@Composable
private fun SheetNumber(label: String, value: Int, range: IntRange, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var draft by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (draft.toIntOrNull() != value && draft.isNotBlank()) draft = value.toString()
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { text ->
            if (text.length <= 7 && (text.isBlank() || text == "-" || text.toIntOrNull() != null)) {
                draft = text
                text.toIntOrNull()?.let { onChange(it.coerceIn(range)) }
            }
        },
        label = { Text(label) }, modifier = modifier, singleLine = true
    )
}

/** Portrait-friendly fullscreen sheet; landscape is also scrollable rather than clipped. */
@Composable
fun DndCharacterSheetDialog(character: Character, attributes: Map<String, Int>, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var sheet by remember(character.id) { mutableStateOf(DndSheetStorage.load(context, character.id)) }
    var tab by remember { mutableIntStateOf(0) }
    val proficiency = sheet.proficiencyOverride ?: (2 + (character.level.coerceIn(1, 20) - 1) / 4)
    val mods = sheetAttributes.associateWith { modifier(attributes[it] ?: 10) }
    val spellMod = mods[sheet.spellAbility] ?: 0
    val spellDc = 8 + proficiency + spellMod

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(8.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Лист D&D · ${character.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Бой", "Навыки", "Магия").forEachIndexed { index, label ->
                        if (tab == index) Button(onClick = { tab = index }, modifier = Modifier.weight(1f)) { Text(label) }
                        else OutlinedButton(onClick = { tab = index }, modifier = Modifier.weight(1f)) { Text(label) }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (tab) {
                        0 -> {
                            Text("Боевые показатели", fontWeight = FontWeight.Bold)
                            Text("КД задаётся вручную: броня и щиты пока не пересчитываются автоматически.", style = MaterialTheme.typography.bodySmall)
                            SheetNumber("Класс доспеха (КД)", sheet.armorClass, 0..99, Modifier.fillMaxWidth()) { sheet = sheet.copy(armorClass = it) }
                            SheetNumber("Дополнительный бонус инициативы", sheet.initiativeBonus, -99..99, Modifier.fillMaxWidth()) { sheet = sheet.copy(initiativeBonus = it) }
                            Text("Инициатива: ${signed((mods["Ловкость"] ?: 0) + sheet.initiativeBonus)} (Ловкость + бонус)")
                            SheetNumber("Скорость, футы", sheet.speedFeet, 0..999, Modifier.fillMaxWidth()) { sheet = sheet.copy(speedFeet = it) }
                            SheetNumber("Временные HP", sheet.temporaryHp, 0..99999, Modifier.fillMaxWidth()) { sheet = sheet.copy(temporaryHp = it) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SheetNumber("Кость здоровья (d)", sheet.hitDie, 1..100, Modifier.weight(1f)) { sheet = sheet.copy(hitDie = it) }
                                SheetNumber("Осталось костей", sheet.hitDiceRemaining, 0..99, Modifier.weight(1f)) { sheet = sheet.copy(hitDiceRemaining = it) }
                            }
                            Text("Спасброски от смерти", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SheetNumber("Успехи (0–3)", sheet.deathSuccesses, 0..3, Modifier.weight(1f)) { sheet = sheet.copy(deathSuccesses = it) }
                                SheetNumber("Провалы (0–3)", sheet.deathFailures, 0..3, Modifier.weight(1f)) { sheet = sheet.copy(deathFailures = it) }
                            }
                            OutlinedTextField(value = sheet.conditions, onValueChange = { sheet = sheet.copy(conditions = it.take(500)) },
                                label = { Text("Состояния (через запятую)") }, modifier = Modifier.fillMaxWidth())
                        }
                        1 -> {
                            Text("Бонус мастерства: +$proficiency", fontWeight = FontWeight.Bold)
                            var manual by remember { mutableStateOf(sheet.proficiencyOverride != null) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = manual, onCheckedChange = { manual = it; sheet = sheet.copy(proficiencyOverride = if (it) proficiency else null) })
                                Text("Задать бонус вручную")
                            }
                            if (manual) SheetNumber("Бонус мастерства", proficiency, 0..30, Modifier.fillMaxWidth()) { sheet = sheet.copy(proficiencyOverride = it) }
                            Text("Спасброски · отметь владение", fontWeight = FontWeight.Bold)
                            sheetAttributes.forEach { stat ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = stat in sheet.saveProficiencies, onCheckedChange = { yes -> sheet = sheet.copy(saveProficiencies = sheet.saveProficiencies.let { if (yes) it + stat else it - stat }) })
                                    Text("$stat: ${signed((mods[stat] ?: 0) + if (stat in sheet.saveProficiencies) proficiency else 0)}")
                                }
                            }
                            HorizontalDivider()
                            Text("Навыки · отметь владение", fontWeight = FontWeight.Bold)
                            sheetSkills.forEach { (skill, stat) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = skill in sheet.skillProficiencies, onCheckedChange = { yes -> sheet = sheet.copy(skillProficiencies = sheet.skillProficiencies.let { if (yes) it + skill else it - skill }) })
                                    Text("$skill (${stat.take(3)}): ${signed((mods[stat] ?: 0) + if (skill in sheet.skillProficiencies) proficiency else 0)}")
                                }
                            }
                            Text("Пассивная внимательность: ${10 + (mods["Мудрость"] ?: 0) + if ("Внимательность" in sheet.skillProficiencies) proficiency else 0}")
                        }
                        else -> {
                            Text("Заклинания", fontWeight = FontWeight.Bold)
                            Text("Характеристика заклинаний:")
                            sheetAttributes.drop(3).forEach { stat ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = sheet.spellAbility == stat, onClick = { sheet = sheet.copy(spellAbility = stat) })
                                    Text(stat)
                                }
                            }
                            Text("Сложность спасброска заклинания: $spellDc")
                            Text("Бонус атаки заклинанием: ${signed(proficiency + spellMod)}")
                            OutlinedTextField(value = sheet.concentration, onValueChange = { sheet = sheet.copy(concentration = it.take(160)) },
                                label = { Text("Концентрация (заклинание)") }, modifier = Modifier.fillMaxWidth())
                            Text("Ячейки заклинаний · максимум / осталось", fontWeight = FontWeight.Bold)
                            (0 until 9).forEach { index ->
                                val max = sheet.spellSlotsMax[index]
                                val left = sheet.spellSlotsRemaining[index]
                                Text("${index + 1}-й круг: $left / $max", fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(onClick = { sheet = sheet.copy(spellSlotsRemaining = sheet.spellSlotsRemaining.toMutableList().apply { this[index] = (left - 1).coerceAtLeast(0) }) }) { Text("−1") }
                                    OutlinedButton(onClick = { sheet = sheet.copy(spellSlotsRemaining = sheet.spellSlotsRemaining.toMutableList().apply { this[index] = (left + 1).coerceAtMost(max) }) }) { Text("+1") }
                                    SheetNumber("Макс.", max, 0..99, Modifier.weight(1f)) { updated ->
                                        sheet = sheet.copy(spellSlotsMax = sheet.spellSlotsMax.toMutableList().apply { this[index] = updated },
                                            spellSlotsRemaining = sheet.spellSlotsRemaining.toMutableList().apply { this[index] = this[index].coerceAtMost(updated) })
                                    }
                                }
                            }
                            OutlinedButton(onClick = { sheet = sheet.copy(spellSlotsRemaining = sheet.spellSlotsMax.toList()) }, modifier = Modifier.fillMaxWidth()) { Text("Восстановить все ячейки") }
                        }
                    }
                }
                Button(onClick = { if (DndSheetStorage.save(context, character.id, sheet)) onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить лист персонажа") }
            }
        }
    }
}
