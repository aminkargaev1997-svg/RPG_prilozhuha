package com.example.rpgcreators

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** The only entry point is tapping a player's marker on the LAN map. */
@Composable
internal fun MasterObservationDialog(
    playerId: String,
    playerName: String,
    snapshot: JSONObject?,
    initialTab: String,
    onClose: () -> Unit,
    onEdit: (operation: String, data: JSONObject, description: String) -> Boolean
) {
    var tab by remember(playerId) { mutableStateOf(initialTab) }
    var editing by remember(playerId) { mutableStateOf("") }
    var identifier by remember(playerId) { mutableLongStateOf(0L) }
    var fieldName by remember(playerId) { mutableStateOf("") }
    var fieldValue by remember(playerId) { mutableStateOf("") }
    var extra by remember(playerId) { mutableStateOf("") }
    var extra2 by remember(playerId) { mutableStateOf("") }
    var extra3 by remember(playerId) { mutableStateOf("") }
    var checked by remember(playerId) { mutableStateOf(false) }
    var feedback by remember(playerId) { mutableStateOf("") }
    var identityCatalog by remember(playerId) { mutableStateOf("") }

    fun send(operation: String, data: JSONObject, description: String) {
        feedback = if (onEdit(operation, data, description)) "Изменение отправлено игроку" else
            "Не удалось отправить. Игрок должен быть подключён и передать персонажа."
        editing = ""
    }

    val inventory = snapshot?.optJSONArray("inventory") ?: JSONArray()
    val abilities = snapshot?.optJSONArray("abilities") ?: JSONArray()
    val attributes = snapshot?.optJSONObject("attributes")
    val equipment = snapshot?.optJSONObject("equipment")

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(if (editing.isEmpty()) "$playerName · Управление персонажем" else "Редактирование · $playerName")
        },
        text = {
            Column(
                Modifier.heightIn(max = 530.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (editing.isEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = tab == "character", onClick = { tab = "character" }, label = { Text("Персонаж") })
                        FilterChip(selected = tab == "abilities", onClick = { tab = "abilities" }, label = { Text("Способности") })
                        FilterChip(selected = tab == "tree", onClick = { tab = "tree" }, label = { Text("Древо") })
                    }
                    if (snapshot == null) {
                        Text("Ожидаем сведения от игрока. Он должен выбрать персонажа в комнате.")
                    } else if (tab == "character") {
                        Text(snapshot.optString("name"), fontWeight = FontWeight.Bold)
                        Text("Раса: ${snapshot.optString("race")} · Класс: ${snapshot.optString("profession")}")
                        TextButton(onClick = {
                            editing = "identity"
                            fieldName = snapshot.optString("name")
                            fieldValue = snapshot.optString("race")
                            extra = snapshot.optString("profession")
                        }) { Text("✎ Изменить данные персонажа") }
                        HorizontalDivider()
                        Text("Уровень ${snapshot.optInt("level")}/20  ·  Опыт ${snapshot.optInt("experience")}/${snapshot.optInt("experienceRequired")}", fontWeight = FontWeight.Bold)
                        Text(if (snapshot.optBoolean("autoExperience", false))
                            "Автоматический рост: 650 + 750 × (уровень − 1)" else "Ручной максимум опыта", fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                editing = "level"; fieldValue = snapshot.optInt("level").toString()
                            }) { Text("Уровень") }
                            TextButton(onClick = {
                                editing = "grant_xp"; fieldValue = ""
                            }) { Text("+ Опыт") }
                            TextButton(onClick = {
                                editing = "set_xp"; fieldValue = snapshot.optInt("experience").toString()
                            }) { Text("Опыт") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                editing = "max_xp"; fieldValue = snapshot.optInt("experienceRequired").toString()
                            }) { Text("Макс. XP") }
                            if (!snapshot.optBoolean("autoExperience", false)) TextButton(onClick = {
                                send("auto_xp", JSONObject(), "Включил автоматический рост опыта")
                            }) { Text("Авто XP") }
                        }
                        HorizontalDivider()
                        Text("Характеристики", fontWeight = FontWeight.Bold)
                        if (attributes != null) {
                            attributes.keys().asSequence().toList().sorted().forEach { name ->
                                TextButton(onClick = {
                                    editing = "attribute"; fieldName = name
                                    fieldValue = attributes.optInt(name).toString()
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text("$name: ${attributes.optInt(name)}   ✎", modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        TextButton(onClick = {
                            editing = "attribute"; fieldName = ""; fieldValue = "10"
                        }) { Text("＋ Добавить характеристику") }
                        HorizontalDivider()
                        Text("Инвентарь", fontWeight = FontWeight.Bold)
                        if (inventory.length() == 0) Text("Нет предметов")
                        for (i in 0 until inventory.length()) {
                            val item = inventory.optJSONObject(i) ?: continue
                            TextButton(onClick = {
                                editing = "item"; identifier = item.optLong("id", 0)
                                fieldName = item.optString("name"); fieldValue = item.optString("quantity")
                                extra = item.optString("description")
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("${item.optString("name")} ${item.optString("quantity")}  ✎", modifier = Modifier.fillMaxWidth())
                            }
                        }
                        TextButton(onClick = {
                            editing = "item"; identifier = 0L; fieldName = ""; fieldValue = ""; extra = ""
                        }) { Text("＋ Добавить предмет") }
                        HorizontalDivider()
                        Text("Экипировка", fontWeight = FontWeight.Bold)
                        listOf("Голова", "Тело", "Руки", "Ноги", "Голени").forEach { slot ->
                            val equippedId = equipment?.optLong(slot, 0L) ?: 0L
                            var equippedName = "Пусто"
                            for (i in 0 until inventory.length()) {
                                val item = inventory.optJSONObject(i) ?: continue
                                if (item.optLong("id", 0L) == equippedId && equippedId != 0L) {
                                    equippedName = item.optString("name")
                                    break
                                }
                            }
                            TextButton(onClick = {
                                editing = "equipment"; fieldName = slot; identifier = equippedId
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("$slot: $equippedName  ✎", modifier = Modifier.fillMaxWidth())
                            }
                        }
                    } else if (tab == "tree") {
                        val currentCharacter = Character(
                            id = snapshot.optLong("id"),
                            name = snapshot.optString("name"), race = snapshot.optString("race"),
                            profession = snapshot.optString("profession"),
                            level = snapshot.optInt("level", 1).coerceIn(1, MAX_CHARACTER_LEVEL),
                            prestigePoints = snapshot.optInt("prestigePoints", 0))
                        val talentTree = snapshot.optJSONObject("tree") ?: JSONObject()
                        val learnedArray = talentTree.optJSONArray("nodes") ?: JSONArray()
                        val pointsGranted = talentTree.optInt("granted", 0)
                        val learned = (0 until learnedArray.length()).map { learnedArray.optString(it) }.toSet()
                        val points = TalentState(learned, pointsGranted)
                        val allNodes = SkillTreeEngine.nodes(currentCharacter)
                        Text("Уровень ${currentCharacter.level}/20 · Потрачено ${SkillTreeEngine.spent(allNodes, points)}", fontWeight = FontWeight.Bold)
                        Text("Доступно очков: ${SkillTreeEngine.totalPoints(currentCharacter, points) - SkillTreeEngine.spent(allNodes, points)}")
                        Text("После 20 уровня: ${currentCharacter.prestigePoints} дополнительных очков")
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            TextButton(onClick = { send("skill_points", JSONObject().put("value", 1), "Выдал 1 очко развития") }) { Text("+1 очко") }
                            TextButton(onClick = { send("skill_points", JSONObject().put("value", 5), "Выдал 5 очков развития") }) { Text("+5 очков") }
                            TextButton(onClick = { send("skill_reset", JSONObject(), "Сбросил древо игрока") }) { Text("Сброс") }
                        }
                        Text("Изученные навыки", fontWeight = FontWeight.Bold)
                        allNodes.filter { it.id in learned }.forEach { node ->
                            Text("• ${node.title}", fontSize = 12.sp)
                        }
                        HorizontalDivider()
                        Text("Доступные для изучения", fontWeight = FontWeight.Bold)
                        allNodes.filter { SkillTreeEngine.canLearn(currentCharacter, points, it, allNodes) }.take(20).forEach { node ->
                            TextButton(onClick = { send("skill_learn", JSONObject().put("id", node.id), "Изучил узел «${node.title}»") },
                                modifier = Modifier.fillMaxWidth()) { Text("+ ${node.title} (${node.tier.cost})") }
                        }
                    } else {
                        Text("Способности", fontWeight = FontWeight.Bold)
                        if (abilities.length() == 0) Text("Способностей нет")
                        for (i in 0 until abilities.length()) {
                            val ability = abilities.optJSONObject(i) ?: continue
                            Column(Modifier.fillMaxWidth().clickable {
                                editing = "ability"; identifier = ability.optLong("id", 0)
                                fieldName = ability.optString("name")
                                fieldValue = ability.optString("description")
                                extra = ability.optString("activator")
                                extra2 = ability.optString("passive")
                                checked = ability.optBoolean("used")
                            }.padding(vertical = 6.dp)) {
                                Text("${ability.optString("name")}   ✎", fontWeight = FontWeight.Bold)
                                val detail = ability.optString("description")
                                if (detail.isNotBlank()) Text(detail, fontSize = 12.sp)
                                if (ability.optBoolean("used")) Text("Использована", fontSize = 12.sp)
                            }
                        }
                        TextButton(onClick = {
                            editing = "ability"; identifier = 0L; fieldName = ""
                            fieldValue = ""; extra = ""; extra2 = ""; checked = false
                        }) { Text("＋ Добавить способность") }
                    }
                } else {
                    Text("Изменение будет применено на телефоне игрока и появится у него в персонаже.", fontSize = 12.sp)
                    when (editing) {
                        "identity" -> {
                            MasterEditTextField(fieldName, { fieldName = it.take(80) }, "Имя")
                            MasterEditTextField(fieldValue, { fieldValue = it.take(80) }, "Раса")
                            TextButton(onClick = { identityCatalog = "race" }) { Text("📚 Расы из базы") }
                            MasterEditTextField(extra, { extra = it.take(80) }, "Класс")
                            TextButton(onClick = { identityCatalog = "class" }) { Text("📚 Классы и профессии из базы") }
                        }
                        "level", "grant_xp", "set_xp", "max_xp" -> {
                            val title = when (editing) {
                                "level" -> "Уровень (число)"
                                "grant_xp" -> "Сколько XP добавить"
                                "set_xp" -> "Текущий опыт"
                                else -> "Максимальный опыт (ручной)"
                            }
                            MasterEditTextField(fieldValue, { fieldValue = it.filter(Char::isDigit).take(9) }, title)
                        }
                        "attribute" -> {
                            MasterEditTextField(fieldName, { fieldName = it.take(60) }, "Характеристика")
                            MasterEditTextField(fieldValue, { fieldValue = it.filter { c -> c.isDigit() || c == '-' }.take(7) }, "Значение")
                        }
                        "item" -> {
                            TextButton(onClick = { identityCatalog = "item" }) { Text("📚 Предметы из базы") }
                            MasterEditTextField(fieldName, { fieldName = it.take(100) }, "Название предмета")
                            MasterEditTextField(fieldValue, { fieldValue = it.take(60) }, "Количество")
                            MasterEditTextField(extra, { extra = it.take(500) }, "Описание")
                        }
                        "ability" -> {
                            TextButton(onClick = { identityCatalog = "ability" }) { Text("📚 Способности из базы") }
                            MasterEditTextField(fieldName, { fieldName = it.take(100) }, "Название способности")
                            MasterEditTextField(fieldValue, { fieldValue = it.take(500) }, "Описание")
                            MasterEditTextField(extra, { extra = it.take(500) }, "Активный эффект")
                            MasterEditTextField(extra2, { extra2 = it.take(500) }, "Пассивный эффект")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = { checked = it })
                                Text("Использована")
                            }
                        }
                        "equipment" -> {
                            Text("Слот: $fieldName", fontWeight = FontWeight.Bold)
                            RadioButtonRow("Снять предмет", identifier == 0L) { identifier = 0L }
                            for (i in 0 until inventory.length()) {
                                val item = inventory.optJSONObject(i) ?: continue
                                val id = item.optLong("id", 0L)
                                RadioButtonRow(item.optString("name"), identifier == id) { identifier = id }
                            }
                        }
                    }
                    if ((editing == "item" || editing == "ability") && identifier != 0L) {
                        HorizontalDivider()
                        TextButton(onClick = {
                            if (editing == "item") send("item_delete", JSONObject().put("id", identifier),
                                "Удалил предмет «$fieldName»")
                            else send("ability_delete", JSONObject().put("id", identifier),
                                "Удалил способность «$fieldName»")
                        }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                    }
                }
                if (feedback.isNotBlank()) Text(feedback, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            if (editing.isEmpty()) {
                TextButton(onClick = onClose) { Text("На карту") }
            } else {
                val numeric = fieldValue.toIntOrNull()
                val canSave = when (editing) {
                    "identity" -> fieldName.isNotBlank() && fieldValue.isNotBlank() && extra.isNotBlank()
                    "attribute" -> fieldName.isNotBlank() && numeric != null && numeric in -100_000..100_000
                    "level" -> numeric != null && numeric in 1..MAX_CHARACTER_LEVEL
                    "grant_xp", "max_xp" -> numeric != null && numeric > 0
                    "set_xp" -> numeric != null && numeric >= 0
                    "item", "ability" -> fieldName.isNotBlank()
                    "equipment" -> true
                    else -> false
                }
                TextButton(enabled = canSave, onClick = {
                    when (editing) {
                        "identity" -> send("identity", JSONObject()
                            .put("name", fieldName).put("race", fieldValue).put("profession", extra),
                            "Изменил имя/расу/класс персонажа")
                        "level", "grant_xp", "set_xp", "max_xp" -> {
                            val labels = mapOf("level" to "Установил уровень", "grant_xp" to "Добавил опыт",
                                "set_xp" to "Установил текущий опыт", "max_xp" to "Изменил максимум опыта")
                            send(editing, JSONObject().put("value", numeric), "${labels[editing]}: $numeric")
                        }
                        "attribute" -> send("attribute", JSONObject().put("name", fieldName).put("value", numeric),
                            "Изменил характеристику «$fieldName»: $numeric")
                        "item" -> send("item_save", JSONObject().put("id", identifier)
                            .put("name", fieldName).put("quantity", fieldValue).put("description", extra),
                            if (identifier == 0L) "Добавил предмет «$fieldName»" else "Изменил предмет «$fieldName»")
                        "ability" -> send("ability_save", JSONObject().put("id", identifier)
                            .put("name", fieldName).put("description", fieldValue)
                            .put("activator", extra).put("passive", extra2).put("used", checked),
                            if (identifier == 0L) "Добавил способность «$fieldName»" else "Изменил способность «$fieldName»")
                        "equipment" -> send("equipment", JSONObject().put("slot", fieldName).put("itemId", identifier),
                            "Изменил экипировку: $fieldName")
                    }
                }) { Text("Сохранить") }
            }
        },
        dismissButton = {
            if (editing.isNotEmpty()) TextButton(onClick = { editing = "" }) { Text("Назад") }
        }
    )
    if (identityCatalog.isNotBlank()) RpgCatalogPickerDialog(
        title = when(identityCatalog) {
            "race" -> "База рас"
            "class" -> "База классов"
            "item" -> "База предметов"
            else -> "База способностей"
        },
        entries = when(identityCatalog) {
            "race" -> RpgCatalog.races
            "class" -> RpgCatalog.professions
            "item" -> RpgEquipmentAbilitiesCatalog.items
            else -> RpgEquipmentAbilitiesCatalog.abilities
        },
        onSelect = { entry ->
            when(identityCatalog) {
                "race" -> fieldValue = entry.name
                "class" -> extra = entry.name
                "item" -> { fieldName = entry.name; extra = entry.description }
                "ability" -> { fieldName = entry.name; fieldValue = entry.description }
            }
            identityCatalog = ""
        },
        onDismiss = { identityCatalog = "" }
    )
}

@Composable
private fun MasterEditTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = onValueChange,
        label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = false,
        maxLines = 4)
}

@Composable
private fun RadioButtonRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}
