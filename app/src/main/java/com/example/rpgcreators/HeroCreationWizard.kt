package com.example.rpgcreators

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Guided, offline character creation. Existing characters use the original identity editor. */
@Composable
fun HeroCreationWizard(onBack: () -> Unit, onCreate: (Character) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var race by rememberSaveable { mutableStateOf("Человек") }
    var profession by rememberSaveable { mutableStateOf("Воин") }
    var showRaces by remember { mutableStateOf(false) }
    var showClasses by remember { mutableStateOf(false) }
    var build by rememberSaveable { mutableStateOf("Под класс") }
    var racePick by rememberSaveable { mutableStateOf("") }
    var classPicks by remember { mutableStateOf(setOf<String>()) }
    var kit by rememberSaveable { mutableStateOf("Походный") }
    var editingPack by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(HeroCreationCatalog.packs.getValue("Походный")) }
    var tone by rememberSaveable { mutableStateOf(HeroCreationCatalog.tones.first()) }
    var weave by remember { mutableStateOf(HeroCreationCatalog.weave(tone)) }
    var narrative by remember { mutableStateOf(weave.narration(race, profession)) }
    var narrativeEdited by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf(false) }
    val steps = listOf("Основа", "Характер", "Дары", "Вещи", "Судьба", "Итог")
    val stats = HeroCreationCatalog.attributesFor(profession, build)
    val racial = HeroCreationCatalog.racialPowers(race)
    val professional = HeroCreationCatalog.classPowers(profession)
    val chosenRacial = racial.firstOrNull { it.name == racePick }
    val chosenClass = professional.filter { it.name in classPicks }
    val ready = name.trim().isNotEmpty() && chosenRacial != null && chosenClass.size == 2 && items.size <= HeroCreationCatalog.maxStartingItems

    fun refresh(part: String) {
        weave = HeroCreationCatalog.weave(tone, weave, part)
        narrative = weave.narration(race, profession)
        narrativeEdited = false
    }
    BackHandler { if (step > 0) step-- else onBack() }

    fun commit() {
        if (!ready || created) return
        created = true
        val id = System.currentTimeMillis()
        val hero = Character(id = id, name = name.trim(), race = race.trim(), profession = profession.trim(), buildVersion = 2)
        val powers = listOfNotNull(chosenRacial?.let { "Расовый дар" to it }) + chosenClass.map { "Классовый приём" to it }
        val abilities = powers.mapIndexed { index, (source, power) ->
            Ability(
                id = id * 100 + index,
                name = power.name,
                description = "$source · ${power.usage}. ${power.rules}",
                activatorDescription = if (power.passive) "" else "${power.usage}. ${power.rules}",
                passiveDescription = if (power.passive) power.rules else "",
                origin = if (source == "Расовый дар") "starter_race" else "starter_class"
            )
        }
        CharacterStorage.saveAttributes(context, id, stats)
        CharacterStorage.saveAbilities(context, id, abilities)
        CharacterStorage.saveInventory(context, id, items.mapIndexedNotNull { index, itemName ->
            HeroCreationCatalog.supplies.firstOrNull { it.name == itemName }?.let { item ->
                InventoryItem(
                    id = id * 100 + 10 + index,
                    name = item.name,
                    category = item.category,
                    description = ItemEffectEngine.templateDescription(item.name, item.description),
                    quantity = if (item.quantity > 1 || item.category == "Расходники" || item.category == "Боеприпасы")
                        item.quantity.toString() else "",
                    effects = ItemEffectEngine.template(item.name)
                )
            }
        })
        val hp = (12 + (stats.getValue("Телосложение") - 10) / 2).coerceIn(10, 20)
        AdventureVitalsStorage.save(context, id, hp, hp)
        HeroStoryStorage.save(context, id, HeroStory(narrative.trim(), weave.moment.hook, tone))
        onCreate(hero)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF152C45), Color(0xFF3F355C))))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (step > 0) step-- else onBack() }) { Text("←", color = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text("✦ КОНСТРУКТОР ГЕРОЯ", color = Color(0xFFFFD990), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(steps[step], color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${step + 1} / 6", color = Color.White)
                }
                LinearProgressIndicator(progress = { (step + 1) / 6f }, modifier = Modifier.fillMaxWidth())
                Text("${name.ifBlank { "Безымянный герой" }} · $race · $profession", color = Color(0xFFD8DEEF), style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                0 -> {
                    SectionHeading("Кто отправится в путь?", "Можно выбрать из большой базы или вписать собственную расу и класс.")
                    OutlinedTextField(name, { name = it.take(40) }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Имя героя") }, singleLine = true)
                    OutlinedTextField(race, { race = it.take(80); racePick = ""; refresh("all") },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Раса") }, singleLine = true)
                    OutlinedButton(onClick = { showRaces = true }, modifier = Modifier.fillMaxWidth()) { Text("✦ Выбрать расу из базы (${RpgCatalog.races.size})") }
                    OutlinedTextField(profession, { profession = it.take(80); classPicks = emptySet(); refresh("all") },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Класс / профессия") }, singleLine = true)
                    OutlinedButton(onClick = { showClasses = true }, modifier = Modifier.fillMaxWidth()) { Text("⚔ Выбрать класс из базы (${RpgCatalog.professions.size})") }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text("Начальный уровень — 1. Бонусы расы и класса не начисляются автоматически: ты выберешь свои дары сам.",
                            modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                1 -> {
                    SectionHeading("Основа характера", "Все варианты используют одни и те же небольшие числа: 15, 14, 13, 12, 10, 8. Раса не увеличивает их автоматически.")
                    HeroChoiceChips(HeroCreationCatalog.builds, build) { build = it }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HeroCreationCatalog.attributes.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { stat ->
                                    Card(Modifier.weight(1f)) {
                                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(stat, style = MaterialTheme.typography.bodySmall)
                                                Text("${if (stats.getValue(stat) >= 10) "+" else ""}${(stats.getValue(stat) - 10) / 2}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text("${stats.getValue(stat)}", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text("Подсказка: «Под класс» ставит высокие значения в характеристики, полезные для профессии. Можно изменить распределение в игре.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                2 -> {
                    SectionHeading("Твои первые дары", "Один расовый и два классовых. Только выбранные появятся в способностях персонажа.")
                    SelectionCounter("Наследие · $race", if (chosenRacial != null) 1 else 0, 1)
                    racial.forEach { power -> PowerChoiceCard(power, power.name == racePick) {
                        racePick = if (racePick == power.name) "" else power.name
                    } }
                    Spacer(Modifier.height(5.dp))
                    SelectionCounter("Приёмы · $profession", chosenClass.size, 2)
                    professional.forEach { power -> PowerChoiceCard(power, power.name in classPicks) {
                        classPicks = if (power.name in classPicks) classPicks - power.name
                                     else if (classPicks.size < 2) classPicks + power.name else classPicks
                    } }
                    Text("Баланс — авторские стартовые правила RPG Creators, а не официальные D&D-черты. Активные эффекты обычно доступны раз за игровой день; применение отмечает игрок или мастер.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                3 -> {
                    SectionHeading("Стартовое снаряжение", "Инвентарь уже доступен герою. Выбери до ${HeroCreationCatalog.maxStartingItems} видов обычных предметов; готовые наборы оставляют один свободный выбор.")
                    HeroChoiceChips(HeroCreationCatalog.packs.keys.toList(), kit) {
                        kit = it
                        items = HeroCreationCatalog.packs.getValue(it)
                        editingPack = it == "Свой набор"
                    }
                    SelectionCounter("Выбрано вещей", items.size, HeroCreationCatalog.maxStartingItems)
                    Card(Modifier.fillMaxWidth()) {
                        Text(if (items.isEmpty()) "Набор пуст — выбери вещи ниже."
                            else items.sorted().joinToString(" · ") { itemName ->
                                val count = HeroCreationCatalog.supplies.firstOrNull { it.name == itemName }?.quantity ?: 1
                                if (count > 1) "$itemName ×$count" else itemName
                            }, modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(onClick = { editingPack = !editingPack }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (editingPack) "Скрыть выбор предметов ↑" else "✎ Настроить стартовый набор ↓")
                    }
                    if (editingPack) HeroCreationCatalog.supplies.forEach { supply ->
                        val selected = supply.name in items
                        Card(Modifier.fillMaxWidth().clickable {
                            items = if (selected) items - supply.name
                                else if (items.size < HeroCreationCatalog.maxStartingItems) items + supply.name else items
                            kit = "Свой набор"
                        }, colors = CardDefaults.cardColors(containerColor = if (selected)
                            MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (selected) "☑" else "□", fontSize = 20.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(supply.name + if (supply.quantity > 1) " ×${supply.quantity}" else "", fontWeight = FontWeight.SemiBold)
                                    Text(supply.description, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(supply.category, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text("Использование расходников, проверка инструментов и свет от факелов отмечаются в игре вручную. Боевые свойства обычного оружия добавляются при создании героя.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                4 -> {
                    SectionHeading("Нити судьбы", "Полностью офлайн: история собирается из связанных событий. Можно изменить только один фрагмент, не теряя остальное.")
                    HeroChoiceChips(HeroCreationCatalog.tones, tone) { tone = it; refresh("all") }
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("✧ Происхождение", fontWeight = FontWeight.Bold)
                            Text(weave.origin)
                            TextButton(onClick = { refresh("origin") }) { Text("↻ Другое прошлое") }
                            HorizontalDivider()
                            Text("✧ Переломный момент", fontWeight = FontWeight.Bold)
                            Text(weave.moment.event)
                            TextButton(onClick = { refresh("moment") }) { Text("↻ Другое событие и цель") }
                            HorizontalDivider()
                            Text("✧ Секрет", fontWeight = FontWeight.Bold)
                            Text(weave.secret)
                            TextButton(onClick = { refresh("secret") }) { Text("↻ Другая тайна") }
                            HorizontalDivider()
                            Text("Зацепка для мастера: ${weave.moment.hook}",
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    OutlinedButton(onClick = { refresh("all") }, modifier = Modifier.fillMaxWidth()) { Text("✦ Переплести всю судьбу") }
                    Text("Полная история — редактируй как хочешь:", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(narrative, { narrative = it.take(5000); narrativeEdited = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp), minLines = 6,
                        label = { Text(if (narrativeEdited) "Твой вариант" else "Сгенерированная предыстория") })
                }
                5 -> {
                    SectionHeading("Герой готов к приключению", "Проверь детали. Нажав на этап внизу, можно вернуться и поменять выбор.")
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            Text("✦ ${name.ifBlank { "Безымянный" }}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("$race · $profession · 1 уровень", style = MaterialTheme.typography.bodyMedium)
                            HorizontalDivider()
                            Text("Характеристики", fontWeight = FontWeight.SemiBold)
                            Text(stats.entries.joinToString("  ·  ") { "${it.key.take(3)} ${it.value}" }, style = MaterialTheme.typography.bodySmall)
                            Text("Расовый дар", fontWeight = FontWeight.SemiBold)
                            Text(chosenRacial?.name ?: "Не выбран")
                            Text("Классовые приёмы", fontWeight = FontWeight.SemiBold)
                            Text(if (chosenClass.isEmpty()) "Не выбраны" else chosenClass.joinToString(" · ") { it.name })
                            Text("Снаряжение (${items.size} видов)", fontWeight = FontWeight.SemiBold)
                            Text(if (items.isEmpty()) "Без вещей" else items.sorted().joinToString(", ") { itemName ->
                                val count = HeroCreationCatalog.supplies.firstOrNull { it.name == itemName }?.quantity ?: 1
                                if (count > 1) "$itemName ×$count" else itemName
                            })
                            Text("Нить судьбы", fontWeight = FontWeight.SemiBold)
                            Text(weave.moment.hook, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!ready) Text("Чтобы создать героя, укажи имя, выбери 1 расовую и 2 классовые способности.",
                        color = MaterialTheme.colorScheme.error)
                    else Text("✓ Готово: способности, характеристики, предметы и история будут сохранены сразу.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { if (step > 0) step-- else onBack() }) { Text("Назад") }
            Button(onClick = { if (step == 5) commit() else step++ },
                enabled = if (step == 0) name.isNotBlank() && race.isNotBlank() && profession.isNotBlank()
                          else if (step == 2) chosenRacial != null && chosenClass.size == 2
                          else if (step == 5) ready && !created else true,
                modifier = Modifier.weight(1f)) { Text(if (step == 5) "Создать героя ✦" else "Продолжить →") }
        }
    }
    if (showRaces) RpgCatalogPickerDialog("Расы", RpgCatalog.races,
        onSelect = { race = it.name; racePick = ""; refresh("all"); showRaces = false },
        onDismiss = { showRaces = false })
    if (showClasses) RpgCatalogPickerDialog("Классы и профессии", RpgCatalog.professions,
        onSelect = { profession = it.name; classPicks = emptySet(); refresh("all"); showClasses = false },
        onDismiss = { showClasses = false })
}

@Composable
private fun SectionHeading(title: String, description: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable
private fun SelectionCounter(title: String, picked: Int, target: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("$picked/$target", color = if (picked == target) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
private fun PowerChoiceCard(power: StarterPower, selected: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
            Text(if (selected) "◉" else "○", fontSize = 20.sp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(power.name, fontWeight = FontWeight.Bold)
                Text(power.usage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(power.rules, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
@Composable
private fun HeroChoiceChips(choices: List<String>, selected: String, onSelect: (String) -> Unit) {
    // Wrap by pairs: all controls remain touch-friendly on narrow screens.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { option ->
                    FilterChip(selected = selected == option, onClick = { onSelect(option) },
                        label = { Text(option, maxLines = 2) }, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
