
package com.example.rpgcreators

import android.os.Bundle
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rpgcreators.ui.theme.RPGCreatorsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RPGCreatorsTheme {
                Scaffold(Modifier.fillMaxSize()) { padding ->
                    AppScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun AppScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Состояние навигации переживает пересоздание Activity при повороте экрана.
    var currentScreen by rememberSaveable { mutableStateOf("home") }
    var appRole by rememberSaveable { mutableStateOf("player") }
    var selectedCharacterId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    // The player's hero is selected before choosing offline/LAN play.
    var showPlayModeSheet by rememberSaveable { mutableStateOf(false) }
    var playChoiceReturnScreen by rememberSaveable { mutableStateOf("heroes") }
    var lanReturnScreen by rememberSaveable { mutableStateOf("heroes") }

    // Для выбранной карты сохраняем простые поля, а не сам объект.
    var selectedMapId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedMapName by rememberSaveable { mutableStateOf("") }
    var selectedMapImageUri by rememberSaveable { mutableStateOf("") }

    val characters = remember {
        mutableStateListOf<Character>().apply {
            addAll(CharacterStorage.loadCharacters(context))
        }
    }
    val selectedCharacter = characters.firstOrNull { it.id == selectedCharacterId }
    val selectedMap = selectedMapId?.let { id ->
        GameMap(id = id, name = selectedMapName, imageUri = selectedMapImageUri)
    }

    // Hardware/system back follows the same route as the visible player controls.
    // The host workflow is deliberately unchanged.
    BackHandler(enabled = appRole == "player" && currentScreen != "home" && currentScreen != "lan") {
        when {
            showDeleteDialog -> showDeleteDialog = false
            showPlayModeSheet -> showPlayModeSheet = false
            else -> currentScreen = when (currentScreen) {
                "heroes", "playerMenu", "list" -> "home"
                "create", "details" -> "heroes"
                "edit", "game" -> "details"
                "mapDetails" -> "maps"
                "abilities", "notes", "talents", "journal", "maps" -> "game"
                else -> "heroes"
            }
        }
    }

    when (currentScreen) {

        "home" -> RoleSelectionScreen(
            onPlayer = { appRole = "player"; currentScreen = "heroes" },
            onMaster = { appRole = "master"; currentScreen = "masterMenu" }
        )
        // Legacy route names are intentionally retained for old restored navigation state.
        "heroes", "playerMenu", "list" -> PlayerHeroesScreen(
            modifier = modifier,
            characters = characters,
            onBack = { currentScreen = "home" },
            onCreate = { currentScreen = "create" },
            onOpen = { character ->
                selectedCharacterId = character.id
                currentScreen = "details"
            },
            onPlay = { character ->
                selectedCharacterId = character.id
                playChoiceReturnScreen = "heroes"
                showPlayModeSheet = true
            }
        )
        "masterMenu" -> MasterMenuScreen(
            onNetwork = { currentScreen = "lan" },
            onBack = { currentScreen = "home" }
        )

        "lan" -> LanGameScreen(
            modifier = modifier,
            characters = characters.toList(),
            onBack = { currentScreen = if (appRole == "master") "masterMenu" else lanReturnScreen },
            isMaster = appRole == "master",
            preselectedCharacterId = if (appRole == "master") null else selectedCharacterId,
            onCharacterUpdate = { updated ->
                val index = characters.indexOfFirst { it.id == updated.id }
                if (index >= 0) {
                    characters[index] = updated
                    CharacterStorage.saveCharacters(context, characters.toList())
                }
            }
        )

        "create" -> CreateCharacterScreen(
            onBack = { currentScreen = "heroes" },
            onCreate = { created ->
                characters.add(created)
                CharacterStorage.saveCharacters(context, characters.toList())
                selectedCharacterId = created.id
                currentScreen = "heroes"
            }
        )

        "details" -> selectedCharacter?.let { character ->
            CharacterDetailsScreen(
                character,
                onBack = { currentScreen = "heroes" },
                onPlay = {
                    playChoiceReturnScreen = "details"
                    showPlayModeSheet = true
                },
                onEdit = { currentScreen = "edit" },
                onDelete = { showDeleteDialog = true },
                onUpdateCharacter = { updated ->
                    val index = characters.indexOfFirst { it.id == updated.id }
                    if (index >= 0) {
                        characters[index] = updated
                        CharacterStorage.saveCharacters(context, characters.toList())
                    }
                }
            )
        }

        "game" -> selectedCharacter?.let { character ->
            GameScreen(
                character = character,
                onBack = { currentScreen = "details" },
                onAbilities = { currentScreen = "abilities" },
                onTalents = { currentScreen = "talents" },
                onJournal = { currentScreen = "journal" },
                onNotes = { currentScreen = "notes" },
                onMaps = { currentScreen = "maps" }
            )
        }

        "journal" -> selectedCharacter?.let { character ->
            val key = RpgQuestStorage.personalKey(character.id)
            var own by remember(character.id) { mutableStateOf(RpgQuestStorage.load(context, key)) }
            var journalTab by remember(character.id) { mutableStateOf("personal") }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=journalTab=="personal",onClick={journalTab="personal"},label={Text("Личные задания")})
                    FilterChip(selected=journalTab=="network",onClick={journalTab="network"},label={Text("Сетевые (офлайн)")})
                }
                Box(Modifier.weight(1f)) {
                    if (journalTab=="personal") RpgQuestJournalScreen(quests=own,
                        onBack={currentScreen="game"},onSave={list->
                            own=list; RpgQuestStorage.save(context,key,list)
                        },title="Личный журнал заданий")
                    else RpgQuestJournalScreen(
                        quests=RpgQuestStorage.cachedCampaignQuests(context,character.id),
                        onBack={currentScreen="game"},title="Сохранённые задания кампаний")
                }
            }
        }

        "notes" -> selectedCharacter?.let { character ->
            RpgPersonalNotesScreen(character.id, onBack = { currentScreen = "game" })
        }

        "talents" -> selectedCharacter?.let { character ->
            SkillTreeScreen(character = character, onBack = { currentScreen = "game" })
        }

        "abilities" -> selectedCharacter?.let { character ->
            AbilitiesScreen(
                character = character,
                onBack = { currentScreen = "game" }
            )
        }

        "maps" -> MapLibraryScreen(
            onBack = { currentScreen = "game" },
            onOpenMap = { map ->
                selectedMapId = map.id
                selectedMapName = map.name
                selectedMapImageUri = map.imageUri
                currentScreen = "mapDetails"
            }
        )

        "mapDetails" -> selectedMap?.let { map ->
            selectedCharacter?.let { character ->
                MapDetailsScreen(
                    map = map,
                    character = character,
                    onBack = { currentScreen = "maps" }
                )
            }
        }

        "edit" -> selectedCharacter?.let { character ->
            CreateCharacterScreen(
                character = character,
                onBack = { currentScreen = "details" },
                onCreate = { updated ->
                    val index = characters.indexOfFirst {
                        it.id == updated.id
                    }

                    if (index != -1) {
                        characters[index] = updated
                    }

                    CharacterStorage.saveCharacters(
                        context,
                        characters.toList()
                    )

                    selectedCharacterId = updated.id
                    currentScreen = "details"
                }
            )
        }
    }

    if (showPlayModeSheet && selectedCharacter != null) {
        PlayerPlayModeSheet(
            character = selectedCharacter,
            onDismiss = { showPlayModeSheet = false },
            onOffline = {
                showPlayModeSheet = false
                currentScreen = "game"
            },
            onLan = {
                showPlayModeSheet = false
                lanReturnScreen = playChoiceReturnScreen
                currentScreen = "lan"
            }
        )
    }

    if (showDeleteDialog && selectedCharacter != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
            },
            title = {
                Text("Удалить персонажа?")
            },
            text = {
                Text(
                    "Персонаж «${selectedCharacter!!.name}» будет удалён без возможности восстановления."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = selectedCharacter!!.id

                        characters.removeAll { it.id == id }

                        CharacterStorage.saveCharacters(
                            context,
                            characters.toList()
                        )

                        CharacterStorage.deleteCharacterData(context, id)

                        selectedCharacterId = null
                        showDeleteDialog = false
                        currentScreen = "heroes"
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}


@Composable
fun RoleSelectionScreen(onPlayer: () -> Unit, onMaster: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("RPG Creators", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onPlayer, modifier = Modifier.fillMaxWidth()) { Text("⚔ Игрок") }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onMaster, modifier = Modifier.fillMaxWidth()) { Text("♛ Мастер") }
    }
}

@Composable
fun MasterMenuScreen(onNetwork: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Режим мастера", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNetwork, modifier = Modifier.fillMaxWidth()) { Text("Создать сетевую комнату") }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text("← Выбор роли") }
    }
}

@Composable
fun CharacterDetailsScreen(
    character: Character,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpdateCharacter: (Character) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showExperienceDialog by remember(character.id) { mutableStateOf(false) }
    var showMaximumDialog by remember(character.id) { mutableStateOf(false) }
    var experienceInput by remember { mutableStateOf("") }
    var maximumInput by remember { mutableStateOf("") }

    if (showExperienceDialog) {
        AlertDialog(
            onDismissRequest = { showExperienceDialog = false },
            title = { Text("Добавить опыт") },
            text = {
                OutlinedTextField(
                    value = experienceInput,
                    onValueChange = { experienceInput = it.filter(Char::isDigit).take(9) },
                    label = { Text("Количество опыта") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = (experienceInput.toIntOrNull() ?: 0) > 0,
                    onClick = {
                        val amount = experienceInput.toIntOrNull() ?: 0
                        onUpdateCharacter(character.addExperience(amount,
                            SkillTreeStorage.load(context, character.id).grantedPoints))
                        experienceInput = ""
                        showExperienceDialog = false
                    }
                ) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { showExperienceDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showMaximumDialog) {
        AlertDialog(
            onDismissRequest = { showMaximumDialog = false },
            title = { Text("Максимум опыта до уровня") },
            text = {
                OutlinedTextField(
                    value = maximumInput,
                    onValueChange = { maximumInput = it.filter(Char::isDigit).take(9) },
                    label = { Text("Новый максимум") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = (maximumInput.toIntOrNull() ?: 0) > 0,
                    onClick = {
                        val max = maximumInput.toIntOrNull() ?: character.experienceRequired
                        val total = character.experience
                        val updated = character.copy(
                            experience = 0,
                            experienceRequired = max,
                            autoExperience = false
                        ).addExperience(total,
                            SkillTreeStorage.load(context, character.id).grantedPoints)
                        onUpdateCharacter(updated)
                        showMaximumDialog = false
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showMaximumDialog = false }) { Text("Отмена") }
            }
        )
    }

    var experienceExpanded by remember(character.id) { mutableStateOf(false) }
    var moreMenuExpanded by remember(character.id) { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Герои") }
            Spacer(Modifier.weight(1f))
            Box {
                OutlinedButton(onClick = { moreMenuExpanded = true }) { Text("⋯") }
                DropdownMenu(expanded = moreMenuExpanded, onDismissRequest = { moreMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Редактировать героя") }, onClick = {
                        moreMenuExpanded = false
                        onEdit()
                    })
                    DropdownMenuItem(text = { Text("Удалить персонажа", color = MaterialTheme.colorScheme.error) }, onClick = {
                        moreMenuExpanded = false
                        onDelete()
                    })
                }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(character.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("${character.race} · ${character.profession}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Уровень ${character.level} / $MAX_CHARACTER_LEVEL", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Опыт", style = MaterialTheme.typography.labelLarge)
                    Text("${character.experience} / ${character.experienceRequired}",
                        style = MaterialTheme.typography.labelLarge)
                }
                LinearProgressIndicator(
                    progress = { (character.experience.toFloat() /
                        character.experienceRequired.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (character.level == MAX_CHARACTER_LEVEL) {
                    Text("Очки древа после 20 уровня: ${character.prestigePoints}" +
                        if (character.buildVersion >= 2) " (по +1, общий предел ${SkillTreeEngine.maxPoints(character)})" else " (по +2)",
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { experienceExpanded = !experienceExpanded }) {
                    Text(if (experienceExpanded) "Скрыть настройки опыта ⌃" else "Управление опытом ⌄")
                }
                if (experienceExpanded) {
                    OutlinedButton(onClick = { experienceInput = ""; showExperienceDialog = true },
                        modifier = Modifier.fillMaxWidth()) { Text("＋ Добавить опыт") }
                    OutlinedButton(onClick = {
                        maximumInput = character.experienceRequired.toString()
                        showMaximumDialog = true
                    }, modifier = Modifier.fillMaxWidth()) { Text("Изменить порог уровня") }
                    Text(if (character.autoExperience)
                        "Порог рассчитывается автоматически по уровню."
                        else "Установлен ручной порог уровня.",
                        style = MaterialTheme.typography.bodySmall)
                    if (!character.autoExperience) {
                        TextButton(onClick = {
                            val max = experienceForLevel(character.level)
                            onUpdateCharacter(character.copy(
                                experienceRequired = max,
                                experience = character.experience.coerceAtMost(max - 1),
                                autoExperience = true
                            ))
                        }) { Text("Вернуть автоматический расчёт") }
                    }
                }
            }
        }

        HeroStoryCard(character.id)

        Button(onClick = onPlay, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("▶ Играть")
        }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text("Изменить персонажа")
        }
        Text("Игра, способности, инвентарь и заметки откроются после выбора режима.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class InventoryItem(
    val id: Long = System.nanoTime(),
    val name: String,
    val quantity: String = "",
    val description: String = "",
    val position: Offset = Offset.Zero,
    val category: String = "Прочее",
    val effects: List<ItemEffect> = emptyList()
)

data class Ability(
    val id: Long = System.nanoTime(),
    val name: String,
    val description: String = "",
    val activatorDescription: String = "",
    val passiveDescription: String = "",
    val isUsed: Boolean = false,
    val origin: String = "", // "hero_story" means only the master may edit/delete this trait.
    val spellLevel: Int = 0 // 0 = ordinary, -1 = cantrip (free), 1..9 = spell slot circle
) {
    val isStoryTrait: Boolean
        get() = origin == "hero_story" || description == "Положительная черта истории героя" ||
            description == "Отрицательная черта истории героя"
    val isPassive: Boolean
        get() = passiveDescription.isNotBlank()
}

// КАРТА ИЗ ОБЩЕЙ БИБЛИОТЕКИ
// Само изображение общее, а MapMarker хранится отдельно для каждого персонажа.
data class GameMap(
    val id: Long = System.nanoTime(),
    val name: String,
    val imageUri: String,
    val tags: List<String> = emptyList()
)

data class MapMarker(
    val id: Long = System.nanoTime(),
    val name: String,
    val description: String = "",
    val type: String = "Обычная",
    // Координаты в диапазоне 0..1 относительно самого изображения.
    val x: Float,
    val y: Float
)

private val equipmentSlots = listOf(
    "Голова", "Тело", "Руки", "Ноги", "Голени", "Аксессуар"
)

private val defaultAttributes = listOf(
    "Сила",
    "Ловкость",
    "Телосложение",
    "Интеллект",
    "Мудрость",
    "Харизма"
)

@Composable
fun GameScreen(
    character: Character,
    onBack: () -> Unit,
    onAbilities: () -> Unit,
    onTalents: () -> Unit = {},
    onMaps: () -> Unit,
    onJournal: () -> Unit = {},
    onNotes: () -> Unit = {},
    showNavigation: Boolean = true,
    onEvent: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val inventoryItems = remember(character.id) {
        mutableStateListOf<InventoryItem>().apply {
            addAll(
                CharacterStorage.loadInventory(
                    context,
                    character.id
                )
            )
        }
    }

    val positions = remember(character.id) {
        mutableStateMapOf<Long, Offset>()
    }

    val equipment = remember(character.id) {
        mutableStateMapOf<String, Long>().apply {
            putAll(
                CharacterStorage.loadEquipment(
                    context,
                    character.id
                )
            )
        }
    }

    val attributes = remember(character.id) {
        mutableStateMapOf<String, Int>().apply {
            putAll(
                CharacterStorage.loadAttributes(
                    context,
                    character.id
                )
            )

            defaultAttributes.forEach {
                if (!containsKey(it)) put(it, 10)
            }
        }
    }

    var showVitals by remember(character.id) { mutableStateOf(false) }
    var maximumHealth by remember(character.id) {
        mutableIntStateOf(AdventureVitalsStorage.maxHealth(context, character.id))
    }
    var currentHealth by remember(character.id) {
        mutableIntStateOf(AdventureVitalsStorage.currentHealth(context, character.id))
    }
    val talentBonuses = SkillTreeEngine.bonus(character, SkillTreeStorage.load(context, character.id))
    val equipmentBonuses = ItemEffectEngine.total(inventoryItems.toList(), equipment.toMap())

    var showInventoryCatalog by remember { mutableStateOf(false) }
    var showRingSlots by remember(character.id) { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteMode by remember { mutableStateOf(false) }

    var itemName by remember { mutableStateOf("") }
    var itemDescription by remember { mutableStateOf("") }
    var itemPresetEffects by remember { mutableStateOf(emptyList<ItemEffect>()) }
    var selectedInventoryItem by remember { mutableStateOf<InventoryItem?>(null) }
    var showInventoryDetails by remember { mutableStateOf(false) }
    var hasQuantity by remember { mutableStateOf(false) }
    var itemQuantity by remember { mutableStateOf("") }
    var itemCategory by remember { mutableStateOf("Прочее") }
    var inventoryQuery by rememberSaveable(character.id) { mutableStateOf("") }
    var inventoryCategory by rememberSaveable(character.id) { mutableStateOf("Все") }
    var inventorySort by rememberSaveable(character.id) { mutableStateOf("Новые") }
    var inventoryListMode by rememberSaveable(character.id) { mutableStateOf(false) }
    var localWalletCopper by remember(character.id) { mutableLongStateOf(CharacterStorage.walletCopper(context, character.id)) }
    var walletExpanded by remember(character.id) { mutableStateOf(false) }
    var walletAdjustment by remember { mutableStateOf("") }
    var walletDenomination by remember { mutableStateOf(100) }


    var selectedSlot by remember { mutableStateOf<String?>(null) }
    var editingAttribute by remember { mutableStateOf<String?>(null) }
    var attributeValue by remember { mutableStateOf("") }

    fun saveInventory() {
        CharacterStorage.saveInventory(
            context,
            character.id,
            inventoryItems.toList()
        )
    }

    fun saveEquipment() {
        CharacterStorage.saveEquipment(
            context,
            character.id,
            equipment.toMap()
        )
    }

    fun saveAttributes() {
        CharacterStorage.saveAttributes(
            context,
            character.id,
            attributes.toMap()
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            character.name,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "${character.race} • ${character.profession}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val effectiveMaxHp = (maximumHealth + equipmentBonuses.maxHpBonus).coerceIn(1, 99999)
        TextButton(onClick = { showVitals = !showVitals }) {
            Text("❤ Здоровье: ${currentHealth.coerceAtMost(effectiveMaxHp)} / $effectiveMaxHp  ${if (showVitals) "▴" else "▾"}")
        }
        if (showVitals) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Текущее здоровье", style = MaterialTheme.typography.labelMedium)
                Slider(value = currentHealth.toFloat(), onValueChange = { value ->
                    currentHealth = value.toInt().coerceIn(0, effectiveMaxHp)
                }, onValueChangeFinished = {
                    AdventureVitalsStorage.save(context, character.id, currentHealth, maximumHealth)
                }, valueRange = 0f..effectiveMaxHp.toFloat())
                // Fast HP changes during tabletop play. Values are clamped and saved immediately.
                listOf(-1, 1).forEach { direction ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(2, 5, 10, 15).forEach { amount ->
                            OutlinedButton(
                                onClick = {
                                    currentHealth = (currentHealth + direction * amount).coerceIn(0, effectiveMaxHp)
                                    AdventureVitalsStorage.save(context, character.id, currentHealth, maximumHealth)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) { Text(if (direction < 0) "−$amount" else "+$amount") }
                        }
                    }
                }
                if (equipmentBonuses.maxHpBonus != 0) Text(
                    "Бонус экипировки к максимуму: ${equipmentBonuses.maxHpBonus}",
                    style = MaterialTheme.typography.bodySmall
                )
                var maxText by remember(character.id) { mutableStateOf(maximumHealth.toString()) }
                OutlinedTextField(value = maxText, onValueChange = { maxText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Максимум здоровья") }, singleLine = true)
                TextButton(onClick = {
                    maximumHealth = (maxText.toIntOrNull() ?: maximumHealth).coerceIn(1, 99999)
                    currentHealth = currentHealth.coerceAtMost((maximumHealth + equipmentBonuses.maxHpBonus).coerceAtLeast(1))
                    AdventureVitalsStorage.save(context, character.id, currentHealth, maximumHealth)
                }) { Text("Сохранить максимум") }
            }
        }

        if (showNavigation) Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onAbilities,
                modifier = Modifier.weight(1f)
            ) {
                Text("✦ Способности")
            }

            OutlinedButton(
                onClick = onMaps,
                modifier = Modifier.weight(1f)
            ) {
                Text("🗺 Карты")
            }
        }

        if (showNavigation) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onJournal, modifier = Modifier.weight(1f)) { Text("📖 Задания") }
            OutlinedButton(onClick = onNotes, modifier = Modifier.weight(1f)) { Text("✎ Заметки") }
        }

        if (showNavigation) OutlinedButton(
            onClick = onTalents,
            modifier = Modifier.fillMaxWidth()
        ) { Text("✧ Древо навыков") }

        // Compact D&D essentials live on the existing character page, not in a second form.
        var showArmorDetails by remember(character.id) { mutableStateOf(false) }
        var armorClass by remember(character.id) { mutableIntStateOf(DndSheetStorage.load(context, character.id).armorClass) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { showArmorDetails = !showArmorDetails }) {
                    Text("🛡 Защита (КД): ${equipmentBonuses.armorClass(armorClass, (attributes["Ловкость"] ?: 10) + (talentBonuses["Ловкость"] ?: 0) + (equipmentBonuses.attributes["Ловкость"] ?: 0))}  ${if (showArmorDetails) "▴" else "▾"}")
                }
                if (showArmorDetails) {
                    Text("Класс доспеха — число, которое атакующий должен достичь броском с бонусом, чтобы попасть по персонажу.",
                        fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            armorClass = (armorClass - 1).coerceAtLeast(0)
                            val old = DndSheetStorage.load(context, character.id)
                            DndSheetStorage.save(context, character.id, old.copy(armorClass = armorClass))
                        }) { Text("−") }
                        Text("  $armorClass  ", fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = {
                            armorClass = (armorClass + 1).coerceAtMost(99)
                            val old = DndSheetStorage.load(context, character.id)
                            DndSheetStorage.save(context, character.id, old.copy(armorClass = armorClass))
                        }) { Text("+") }
                    }
                    Text("Базовый КД: $armorClass · экипировка: ${equipmentBonuses.acDescription()}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Броня задаёт защиту, щит и магические предметы добавляют бонус. Снять предмет можно в разделе экипировки.", fontSize = 11.sp)
                }
            }
        }

        // ЭКИПИРОВКА

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Экипировка",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Нажми на слот, чтобы надеть предмет из инвентаря.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("В руках", fontWeight = FontWeight.SemiBold)
                Text(
                    "Меч, щит или другой предмет. Кисти с кольцами — ниже, они не занимают руки.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EquipmentHands.handSlots.forEach { hand ->
                        val held = inventoryItems.firstOrNull { it.id == equipment[hand] }
                        val opposite = if (hand == EquipmentHands.LEFT) EquipmentHands.RIGHT else EquipmentHands.LEFT
                        val occupyingBoth = inventoryItems.firstOrNull { it.id == equipment[opposite] }
                            ?.takeIf { EquipmentHands.isTwoHanded(it) }
                        Column(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedSlot = hand }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(if (hand == EquipmentHands.LEFT) "🫲" else "🫱", fontSize = 25.sp)
                            Text(hand, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(held?.name ?: occupyingBoth?.let { "Занята: ${it.name}" } ?: "Свободна",
                                fontSize = 12.sp,
                                color = if (held != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center)
                        }
                    }
                }

                equipmentSlots.forEach { slot ->
                    val item = inventoryItems.firstOrNull { it.id == equipment[slot] }
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selectedSlot = slot }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (slot == "Руки") "Перчатки" else slot,
                            modifier = Modifier.width(88.dp), fontWeight = FontWeight.SemiBold)
                        Text(
                            (item?.name ?: "Пусто") + (item?.effects?.takeIf { it.isNotEmpty() }?.let {
                                "  ✦ " + it.joinToString { effect -> effect.readable() }
                            } ?: ""),
                            color = if (item == null) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                val wornRings = ItemCatalogCategories.ringSlots.count { equipment[it] != null }
                TextButton(onClick = { showRingSlots = !showRingSlots }) {
                    Text("💍 Кисти и кольца ($wornRings/10) ${if (showRingSlots) "▴" else "▾"}")
                }
                if (showRingSlots) {
                    RingHandsPicker(
                        equipment = equipment.toMap(),
                        inventory = inventoryItems.toList(),
                        onChooseSlot = { selectedSlot = it }
                    )
                }
            }
        }

        // ХАРАКТЕРИСТИКИ

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Характеристики",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                if (talentBonuses.isNotEmpty()) Text(
                    "С учётом древа: " + talentBonuses.entries.joinToString { "${it.key} +${it.value}" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                if (equipmentBonuses.attributes.isNotEmpty()) Text(
                    "От снаряжения: " + equipmentBonuses.attributes.entries.joinToString { "${it.key} ${if (it.value >= 0) "+" else ""}${it.value}" },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.primary
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    defaultAttributes.take(3).forEach { attribute ->
                        AttributeDiamond(
                            attribute,
                            (attributes[attribute] ?: 10) + (talentBonuses[attribute] ?: 0) + (equipmentBonuses.attributes[attribute] ?: 0),
                            modifier = Modifier.weight(1f)
                        ) {
                            editingAttribute = attribute
                            attributeValue =
                                (attributes[attribute] ?: 10).toString()
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    defaultAttributes.drop(3).forEach { attribute ->
                        AttributeDiamond(
                            attribute,
                            (attributes[attribute] ?: 10) + (talentBonuses[attribute] ?: 0) + (equipmentBonuses.attributes[attribute] ?: 0),
                            modifier = Modifier.weight(1f)
                        ) {
                            editingAttribute = attribute
                            attributeValue =
                                (attributes[attribute] ?: 10).toString()
                        }
                    }
                }

                Text(
                    "Нажми на характеристику, чтобы изменить значение.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // All checks are grouped under six attributes, not shown as 24 permanent checkboxes.
        var showChecks by remember(character.id) { mutableStateOf(false) }
        var selectedCheckAttribute by remember(character.id) { mutableStateOf("Ловкость") }
        var checkSheet by remember(character.id) { mutableStateOf(DndSheetStorage.load(context, character.id)) }
        val checksByAttribute = mapOf(
            "Сила" to listOf("Атлетика"),
            "Ловкость" to listOf("Акробатика", "Ловкость рук", "Скрытность"),
            "Телосложение" to emptyList(),
            "Интеллект" to listOf("Анализ", "История", "Магия", "Природа", "Религия"),
            "Мудрость" to listOf("Внимательность", "Выживание", "Медицина", "Проницательность", "Уход за животными"),
            "Харизма" to listOf("Выступление", "Запугивание", "Обман", "Убеждение")
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showChecks = !showChecks }) {
                    Text("🎯 Проверки и владения ${if (showChecks) "▴" else "▾"}")
                }
                if (showChecks) {
                    Text("Выбери характеристику. Показываем только связанные с ней навыки.", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        defaultAttributes.take(3).forEach { attr ->
                            OutlinedButton(onClick = { selectedCheckAttribute = attr }, modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(2.dp)) { Text(attr.take(3), fontSize = 11.sp) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        defaultAttributes.drop(3).forEach { attr ->
                            OutlinedButton(onClick = { selectedCheckAttribute = attr }, modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(2.dp)) { Text(attr.take(3), fontSize = 11.sp) }
                        }
                    }
                    val statScore = (attributes[selectedCheckAttribute] ?: 10) + (talentBonuses[selectedCheckAttribute] ?: 0) + (equipmentBonuses.attributes[selectedCheckAttribute] ?: 0)
                    val statBonus = Math.floorDiv(statScore - 10, 2)
                    val mastery = checkSheet.proficiencyOverride ?: (2 + (character.level.coerceIn(1, 20) - 1) / 4)
                    Text("$selectedCheckAttribute · бонус характеристики ${if (statBonus >= 0) "+" else ""}$statBonus · мастерство +$mastery",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val hasSave = selectedCheckAttribute in checkSheet.saveProficiencies
                    val saveBonus = equipmentBonuses.saves[selectedCheckAttribute] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hasSave, onCheckedChange = { checked ->
                            checkSheet = checkSheet.copy(saveProficiencies =
                                if (checked) checkSheet.saveProficiencies + selectedCheckAttribute
                                else checkSheet.saveProficiencies - selectedCheckAttribute)
                            DndSheetStorage.save(context, character.id, checkSheet)
                        })
                        Text("Спасбросок: ${if (statBonus + (if (hasSave) mastery else 0) + saveBonus >= 0) "+" else ""}${statBonus + (if (hasSave) mastery else 0) + saveBonus}", fontSize = 13.sp)
                    }
                    (checksByAttribute[selectedCheckAttribute] ?: emptyList()).forEach { skill ->
                        val proficient = skill in checkSheet.skillProficiencies
                        val total = statBonus + (if (proficient) mastery else 0) + (equipmentBonuses.skills[skill] ?: 0)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = proficient, onCheckedChange = { checked ->
                                checkSheet = checkSheet.copy(skillProficiencies =
                                    if (checked) checkSheet.skillProficiencies + skill else checkSheet.skillProficiencies - skill)
                                DndSheetStorage.save(context, character.id, checkSheet)
                            })
                            Text("$skill: ${if (total >= 0) "+" else ""}$total", fontSize = 13.sp)
                        }
                    }
                    Text("Отмечай владения, а не вводи бонусы вручную. Итог рассчитывается автоматически. Бросок кубика остаётся настоящим.",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // КОШЕЛЁК: shown in the existing character screen, not a separate page.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("🪙 Кошелёк", fontWeight = FontWeight.Bold)
                        Text(RpgCurrency.display(localWalletCopper),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { walletExpanded = !walletExpanded }) {
                        Text(if (walletExpanded) "Свернуть" else "Изменить")
                    }
                }
                if (walletExpanded) {
                    Text("Монеты синхронизируются с балансом магазина. Цены магазинов указаны в золотых.",
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(100 to "Золото", 10 to "Серебро", 1 to "Медь").forEach { (value, label) ->
                            FilterChip(selected = walletDenomination == value,
                                onClick = { walletDenomination = value }, label = { Text(label) })
                        }
                    }
                    OutlinedTextField(value = walletAdjustment,
                        onValueChange = { walletAdjustment = it.filter(Char::isDigit).take(9) },
                        label = { Text("Количество монет") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val value = (walletAdjustment.toLongOrNull() ?: 0L) * walletDenomination
                        OutlinedButton(enabled = value > 0 && value <= localWalletCopper,
                            onClick = {
                                localWalletCopper = CharacterStorage.changeWallet(context, character.id, -value)
                                walletAdjustment = ""
                            }) { Text("− Списать") }
                        OutlinedButton(enabled = value > 0,
                            onClick = {
                                localWalletCopper = CharacterStorage.changeWallet(context, character.id, value)
                                walletAdjustment = ""
                            }) { Text("+ Добавить") }
                    }
                }
            }
        }

        // ИНВЕНТАРЬ

        Text(
            "Инвентарь",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        // Inventory view keeps the old draggable field, but large inventories
        // switch automatically to a searchable scrollable list.
        OutlinedTextField(value=inventoryQuery,onValueChange={inventoryQuery=it},
            label={Text("Поиск предмета")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            var catOpen by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick={catOpen=true}){Text(inventoryCategory)}
                DropdownMenu(expanded=catOpen,onDismissRequest={catOpen=false}) {
                    (listOf("Все") + inventoryItems.map { it.category }.distinct().sorted()).forEach { cat ->
                        DropdownMenuItem(text={Text(cat)},onClick={inventoryCategory=cat;catOpen=false})
                    }
                }
            }
            var sortOpen by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick={sortOpen=true}){Text("↕ $inventorySort")}
                DropdownMenu(expanded=sortOpen,onDismissRequest={sortOpen=false}) {
                    listOf("Новые","А–Я","По категории","Количество").forEach { option ->
                        DropdownMenuItem(text={Text(option)},onClick={inventorySort=option;sortOpen=false})
                    }
                }
            }
            TextButton(onClick={inventoryListMode=!inventoryListMode}){Text(if(inventoryListMode) "Поле" else "Список")}
        }
        val filteredInventory = inventoryItems.filter { item ->
            item.name.contains(inventoryQuery,ignoreCase=true) &&
            (inventoryCategory=="Все" || item.category==inventoryCategory)
        }.let { list -> when(inventorySort) {
            "А–Я" -> list.sortedBy { it.name.lowercase() }
            "По категории" -> list.sortedWith(compareBy<InventoryItem>{it.category}.thenBy{it.name})
            "Количество" -> list.sortedByDescending { it.quantity.toIntOrNull() ?: 1 }
            else -> list.sortedByDescending { it.id }
        } }
        val showInventoryList = inventoryListMode || inventoryItems.size > 8 ||
            inventoryQuery.isNotBlank() || inventoryCategory != "Все"
        if(showInventoryList) {
            Text("Найдено: ${filteredInventory.size} из ${inventoryItems.size}",
                style=MaterialTheme.typography.bodySmall)
            Column(Modifier.fillMaxWidth().heightIn(max=300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(5.dp)) {
                filteredInventory.forEach { item ->
                    Card(Modifier.fillMaxWidth().clickable {
                        if(deleteMode) {
                            if(CharacterStorage.isBoundItem(item)) return@clickable
                            inventoryItems.removeAll { it.id == item.id }
                            equipment.entries.removeAll { it.value == item.id }
                            positions.remove(item.id)
                            saveInventory(); saveEquipment()
                        } else if(CharacterStorage.isJournalItem(item)) onJournal()
                        else { selectedInventoryItem=item;showInventoryDetails=false }
                    }) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight=FontWeight.Bold)
                                Text(item.category,style=MaterialTheme.typography.bodySmall)
                            }
                            Text(if(CharacterStorage.isBoundItem(item)) "🔒" else if(deleteMode) "Удалить" else if(item.quantity.isNotBlank()) "×${item.quantity}" else "Открыть")
                        }
                    }
                }
            }
        } else {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                // Two columns accommodate up to eight items (including bound journal/scroll).
                .height(if (inventoryItems.size > 6) 280.dp else 220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    1.dp,
                    if (deleteMode) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    RoundedCornerShape(20.dp)
                )
        ) {
            val itemWidth = 124.dp
            val itemHeight = 56.dp

            val maxX = with(density) {
                (maxWidth - itemWidth).toPx().coerceAtLeast(0f)
            }

            val maxY = with(density) {
                (maxHeight - itemHeight).toPx().coerceAtLeast(0f)
            }

            val itemWidthPx = with(density) { itemWidth.toPx() }
            val itemHeightPx = with(density) { itemHeight.toPx() }
            val gapPx = with(density) { 8.dp.toPx() }
            val columns = if (maxX >= itemWidthPx + gapPx) 2 else 1

            LaunchedEffect(inventoryItems.map { it.id }, maxX, maxY) {
                // Use predictable, non-overlapping starting positions instead of random
                // offsets. Previously placed or dragged items are not reset here.
                inventoryItems.forEachIndexed { index, item ->
                    if (positions[item.id] == null) {
                        positions[item.id] = Offset(
                            ((index % columns) * (itemWidthPx + gapPx)).coerceAtMost(maxX),
                            ((index / columns) * (itemHeightPx + gapPx)).coerceAtMost(maxY)
                        )
                    }
                }

                positions.keys.toList()
                    .filter { id ->
                        inventoryItems.none { it.id == id }
                    }
                    .forEach {
                        positions.remove(it)
                    }
            }

            inventoryItems.forEach { item ->
                val pos = positions[item.id] ?: Offset.Zero

                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                pos.x.roundToInt(),
                                pos.y.roundToInt()
                            )
                        }
                        .size(itemWidth, itemHeight)
                        .clip(CircleShape)
                        .background(
                            if (deleteMode) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        )
                        .border(
                            1.dp,
                            if (deleteMode) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            CircleShape
                        )
                        .pointerInput(item.id, deleteMode, maxX, maxY) {
                            if (deleteMode) {
                                detectTapGestures {
                                    if (CharacterStorage.isBoundItem(item)) return@detectTapGestures
                                    inventoryItems.removeAll {
                                        it.id == item.id
                                    }

                                    equipment.entries.removeAll {
                                        it.value == item.id
                                    }

                                    positions.remove(item.id)

                                    saveInventory()
                                    saveEquipment()
                                }
                            } else {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()

                                    val old = positions[item.id] ?: Offset.Zero

                                    positions[item.id] = Offset(
                                        (old.x + dragAmount.x).coerceIn(0f, maxX),
                                        (old.y + dragAmount.y).coerceIn(0f, maxY)
                                    )
                                }
                            }
                        }
                        .pointerInput(item.id, deleteMode) {
                            if (!deleteMode) detectTapGestures {
                                if (CharacterStorage.isJournalItem(item)) onJournal()
                                else { selectedInventoryItem = item; showInventoryDetails = false }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            item.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )

                        if (item.quantity.isNotBlank()) {
                            Text(
                                "${item.quantity} шт.",
                                fontSize = 10.sp
                            )
                        }

                        if (equipment.values.contains(item.id)) {
                            Text("Надето", fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        } // End draggable inventory field

        Text(
            if (deleteMode) {
                "Нажми на предмет, чтобы удалить его."
            } else {
                "Перетаскивай предметы пальцем или мышью."
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    deleteMode = false
                    itemName = ""
                    itemDescription = ""
                    itemQuantity = ""
                    itemCategory = "Прочее"
                    hasQuantity = false
                    showAddDialog = true
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("+ Добавить")
            }

            OutlinedButton(
                onClick = {
                    deleteMode = !deleteMode
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (deleteMode) "Отмена" else "− Удалить")
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("← Назад")
        }
    }

    selectedInventoryItem?.let { item ->
        var editingItem by remember(item.id) { mutableStateOf(false) }
        var draftName by remember(item.id) { mutableStateOf(item.name) }
        var draftDescription by remember(item.id) { mutableStateOf(item.description) }
        var draftQuantity by remember(item.id) { mutableStateOf(item.quantity.ifBlank { "1" }) }
        var draftCategory by remember(item.id) { mutableStateOf(item.category) }
        var draftEffects by remember(item.id) { mutableStateOf(item.effects) }
        var showItemEffects by remember(item.id) { mutableStateOf(false) }
        val bound = CharacterStorage.isBoundItem(item)
        fun saveEdited(updated: InventoryItem) {
            val index = inventoryItems.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                inventoryItems[index] = updated
                selectedInventoryItem = updated
                saveInventory()
            }
        }
        AlertDialog(
            onDismissRequest = { selectedInventoryItem = null },
            title = { Text(if (editingItem) "Редактирование предмета" else item.name) },
            text = {
                Column(Modifier.heightIn(max = 410.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!bound) {
                        Text("Количество", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            listOf(-5,-1,1,5).forEach { delta ->
                                OutlinedButton(onClick = {
                                    val before = item.quantity.toIntOrNull() ?: 1
                                    val newer = item.copy(quantity = (before + delta).coerceIn(0, 1_000_000).toString())
                                    saveEdited(newer)
                                }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                                    Text(if (delta > 0) "+$delta" else "$delta")
                                }
                            }
                        }
                        Text("Сейчас: ${item.quantity.ifBlank { "1" }} · 0 оставляет предмет в сумке",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (!bound) {
                        TextButton(onClick = { showItemEffects = !showItemEffects }) {
                            Text("✦ Свойства и эффекты ${if (showItemEffects) "▴" else "▾"}")
                        }
                        if (showItemEffects) {
                            ItemEffectsEditor(draftEffects, onChange = { newEffects ->
                                draftEffects = newEffects
                                saveEdited(item.copy(effects = newEffects))
                            })
                        }
                    }
                    if (editingItem && !bound) {
                        OutlinedTextField(draftName, { draftName = it },
                            label = { Text("Название") }, singleLine = true)
                        OutlinedTextField(draftDescription, { draftDescription = it },
                            label = { Text("Описание") }, minLines = 2)
                        OutlinedTextField(draftQuantity, { draftQuantity = it.filter(Char::isDigit).take(7) },
                            label = { Text("Точное количество") }, singleLine = true)
                        var changeCategoryMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { changeCategoryMenu = true }) { Text("Категория: $draftCategory") }
                            DropdownMenu(expanded = changeCategoryMenu, onDismissRequest = { changeCategoryMenu = false }) {
                                ItemCatalogCategories.all.forEach { category ->
                                    DropdownMenuItem(text = { Text(category) }, onClick = {
                                        draftCategory = category; changeCategoryMenu = false
                                    })
                                }
                            }
                        }
                    } else {
                        Text("Категория: ${item.category}", style = MaterialTheme.typography.bodySmall)
                        Text(item.description.ifBlank { "Описание отсутствует" })
                        if (item.effects.isNotEmpty()) Text(
                            item.effects.joinToString(" · ") { it.readable() },
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.primary
                        )
                        if (bound) Text("Системный предмет нельзя изменить или удалить.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (editingItem && !bound) {
                    TextButton(enabled = draftName.isNotBlank() && draftQuantity.toIntOrNull() != null,
                        onClick = {
                            saveEdited(item.copy(name = draftName.trim(), description = draftDescription.trim(),
                                quantity = draftQuantity, category = draftCategory, effects = draftEffects))
                            editingItem = false
                        }) { Text("Сохранить") }
                } else if (!bound) {
                    TextButton(onClick = {
                        draftName = item.name; draftDescription = item.description
                        draftQuantity = item.quantity.ifBlank { "1" }; draftCategory = item.category
                        editingItem = true
                    }) { Text("Изменить") }
                }
            },
            dismissButton = {
                TextButton(onClick = { if (editingItem) editingItem = false else selectedInventoryItem = null }) {
                    Text(if (editingItem) "Отмена" else "Закрыть")
                }
            }
        )
    }

    // ДИАЛОГ ДОБАВЛЕНИЯ ПРЕДМЕТА

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
            },
            title = {
                Text("Новый предмет")
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { showInventoryCatalog = true }) { Text("📚 Выбрать предмет из библиотеки") }
                    OutlinedTextField(
                        itemName,
                        { itemName = it },
                        label = {
                            Text("Название предмета")
                        },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = itemDescription,
                        onValueChange = { itemDescription = it },
                        label = { Text("Описание предмета") },
                        minLines = 2
                    )

                    Text("Категория")
                    var categoryMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick={categoryMenuOpen=true}){Text(itemCategory)}
                        DropdownMenu(expanded=categoryMenuOpen,onDismissRequest={categoryMenuOpen=false}) {
                            ItemCatalogCategories.all.forEach { c ->
                                DropdownMenuItem(text={Text(c)},onClick={itemCategory=c;categoryMenuOpen=false})
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            hasQuantity,
                            { hasQuantity = it }
                        )

                        Text("Указать количество")
                    }

                    if (hasQuantity) {
                        OutlinedTextField(
                            itemQuantity,
                            { itemQuantity = it },
                            label = {
                                Text("Количество")
                            },
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = itemName.isNotBlank() &&
                            (!hasQuantity || (itemQuantity.toLongOrNull() ?: -1L) in 0..1_000_000),
                    onClick = {
                        val denomination = CharacterStorage.currencyCopperValue(itemName.trim())
                        val amount = if (hasQuantity) itemQuantity.trim().toLongOrNull() else 1L
                        if (denomination > 0 && amount != null && amount in 1..1_000_000) {
                            localWalletCopper = CharacterStorage.changeWallet(context, character.id,
                                denomination.toLong() * amount)
                        } else {
                            inventoryItems.add(
                                InventoryItem(name = itemName.trim(), description = itemDescription.trim(),
                                    category = itemCategory, quantity = if (hasQuantity) itemQuantity.trim() else "",
                                    effects = if (itemPresetEffects.isNotEmpty()) itemPresetEffects else ItemEffectEngine.template(itemName.trim()))
                            )
                            saveInventory()
                        }
                        onEvent("Добавил предмет «${itemName.trim()}»")
                        showAddDialog = false

                        itemName = ""
                        itemDescription = ""
                        itemPresetEffects = emptyList()
                        itemQuantity = ""
                        itemCategory = "Прочее"
                        hasQuantity = false
                    }
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showInventoryCatalog) RpgCatalogPickerDialog(
        title = "База предметов",
        entries = RpgEquipmentAbilitiesCatalog.items,
        onSelect = { chosen ->
            itemName = chosen.name
            itemDescription = ItemEffectEngine.templateDescription(chosen.name, chosen.description)
            itemPresetEffects = ItemEffectEngine.template(chosen.name)
            itemCategory = chosen.group
            showInventoryCatalog = false
        },
        onDismiss = { showInventoryCatalog = false }
    )

    // ДИАЛОГ ЭКИПИРОВКИ

    selectedSlot?.let { slot ->
        val currentlyEquipped = inventoryItems.firstOrNull {
            it.id == equipment[slot]
        }
        var ringDetailsId by remember(slot) { mutableStateOf<Long?>(null) }
        val isFingerSlot = slot in ItemCatalogCategories.ringSlots
        val availableForSlot = inventoryItems.filter { item ->
            !CharacterStorage.isBoundItem(item) &&
                (item.quantity.toIntOrNull() ?: 1) > 0 &&
                equipment.entries.none { it.key != slot && it.value == item.id } &&
                EquipmentHands.canEquip(slot, item, equipment.toMap(), inventoryItems.toList())
        }

        AlertDialog(
            onDismissRequest = {
                selectedSlot = null
            },
            title = {
                Text(if (isFingerSlot) ItemCatalogCategories.fingerLabel(slot) else "Слот: $slot")
            },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text(if (isFingerSlot) "Выбери кольцо для этого пальца:"
                        else if (slot in EquipmentHands.handSlots) "Выбери, что держать в руке:"
                        else "Выбери предмет из инвентаря:")
                    if (slot in EquipmentHands.handSlots) {
                        Text("Двуручное оружие требует обе свободные руки; два щита одновременно нельзя.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (isFingerSlot) {
                        Text("Одно кольцо на палец. Для одинаковых колец нужны отдельные экземпляры.",
                            style = MaterialTheme.typography.bodySmall)
                        currentlyEquipped?.let { ring ->
                            Text("Надето: ${ring.name}", fontWeight = FontWeight.SemiBold)
                            if (ring.description.isNotBlank()) Text(ring.description, fontSize = 12.sp)
                            ring.effects.forEach { Text("✦ ${it.readable()}", fontSize = 12.sp) }
                            HorizontalDivider()
                        }
                    }

                    if (inventoryItems.isEmpty()) {
                        Text("Инвентарь пока пуст.")
                    }

                    if (availableForSlot.isEmpty()) {
                        Text(if (isFingerSlot)
                            "В инвентаре нет свободных колец. Добавь новое кольцо в инвентарь."
                        else "Подходящих предметов нет.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    availableForSlot.forEach { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    equipment[slot] = item.id
                                    saveEquipment()
                                    selectedSlot = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(item.name + if (item.quantity.isNotBlank()) " (×${item.quantity})" else "",
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (isFingerSlot) TextButton(onClick = {
                                ringDetailsId = if (ringDetailsId == item.id) null else item.id
                            }) { Text("ⓘ") }
                        }
                        if (isFingerSlot && ringDetailsId == item.id) {
                            if (item.description.isNotBlank()) Text(item.description, fontSize = 12.sp)
                            if (item.effects.isEmpty()) Text("Нет игровых бонусов", fontSize = 12.sp)
                            else item.effects.forEach { Text("✦ ${it.readable()}", fontSize = 12.sp) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        equipment.remove(slot)
                        saveEquipment()
                        selectedSlot = null
                    },
                    enabled = currentlyEquipped != null
                ) {
                    Text("Снять предмет")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedSlot = null
                    }
                ) {
                    Text("Закрыть")
                }
            }
        )
    }

    // РЕДАКТИРОВАНИЕ ХАРАКТЕРИСТИКИ

    editingAttribute?.let { attribute ->
        AlertDialog(
            onDismissRequest = {
                editingAttribute = null
            },
            title = {
                Text(attribute)
            },
            text = {
                OutlinedTextField(
                    value = attributeValue,
                    onValueChange = { value ->
                        attributeValue = value
                            .filter { it.isDigit() }
                            .take(4)
                    },
                    label = {
                        Text("Значение")
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        attributes[attribute] =
                            attributeValue.toIntOrNull() ?: 10

                        saveAttributes()
                        onEvent("Изменил характеристику «$attribute» на ${attributes[attribute]}")
                        editingAttribute = null
                    },
                    enabled = attributeValue.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingAttribute = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun AttributeDiamond(
    name: String,
    value: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(
                vertical = 10.dp,
                horizontal = 3.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value.toString(),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )

        Text(
            name,
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}

// ЭКРАН СПОСОБНОСТЕЙ

@Composable
fun AbilitiesScreen(
    character: Character,
    onBack: () -> Unit,
    onEvent: (String) -> Unit = {}
) {
    val context = LocalContext.current

    val abilities = remember(character.id) {
        mutableStateListOf<Ability>().apply {
            addAll(CharacterStorage.loadAbilities(context, character.id))
        }
    }

    // Shares storage with the previous D&D sheet, so existing slot counts are retained.
    var magic by remember(character.id) { mutableStateOf(DndSheetStorage.load(context, character.id)) }
    var showMagicSetup by remember(character.id) { mutableStateOf(false) }
    fun updateMagic(next: DndSheet) {
        magic = next
        DndSheetStorage.save(context, character.id, next)
    }

    var inCombat by remember(character.id) {
        mutableStateOf(CharacterStorage.loadCombatState(context, character.id))
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var deleteMode by remember { mutableStateOf(false) }
    var abilityForDelete by remember { mutableStateOf<Ability?>(null) }

    var showAbilityCatalog by remember { mutableStateOf(false) }
    var abilityName by remember { mutableStateOf("") }
    var abilityDescription by remember { mutableStateOf("") }
    var hasActivator by remember { mutableStateOf(false) }
    var activatorDescription by remember { mutableStateOf("") }
    var hasPassive by remember { mutableStateOf(false) }
    var spellLevel by remember { mutableIntStateOf(0) }
    var passiveDescription by remember { mutableStateOf("") }

    fun saveAbilities() {
        CharacterStorage.saveAbilities(context, character.id, abilities.toList())
    }

    fun saveCombat() {
        CharacterStorage.saveCombatState(context, character.id, inCombat)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Способности",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            character.name,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✦ Магические ячейки", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showMagicSetup = !showMagicSetup }) {
                        Text(if (showMagicSetup) "Скрыть" else "Настроить")
                    }
                }
                Text("Закрашенный круг — ячейка доступна; пустой — уже потрачена.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val configured = (0..8).filter { magic.spellSlotsMax.getOrElse(it) { 0 } > 0 }
                if (configured.isEmpty() && !showMagicSetup) {
                    Text("Ячейки ещё не настроены. Нажми «Настроить», если герой использует магию.", fontSize = 13.sp)
                }
                (if (showMagicSetup) (0..8).toList() else configured).forEach { levelIndex ->
                    val maximum = magic.spellSlotsMax.getOrElse(levelIndex) { 0 }
                    val left = magic.spellSlotsRemaining.getOrElse(levelIndex) { 0 }.coerceIn(0, maximum)
                    if (maximum > 0 || showMagicSetup) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${levelIndex + 1} круг", modifier = Modifier.weight(1f), fontSize = 13.sp)
                            Text("$left/$maximum", fontSize = 12.sp)
                            if (showMagicSetup) {
                                OutlinedButton(onClick = {
                                    val maxes = magic.spellSlotsMax.toMutableList()
                                    val remaining = magic.spellSlotsRemaining.toMutableList()
                                    maxes[levelIndex] = (maximum - 1).coerceAtLeast(0)
                                    remaining[levelIndex] = remaining[levelIndex].coerceAtMost(maxes[levelIndex])
                                    updateMagic(magic.copy(spellSlotsMax = maxes, spellSlotsRemaining = remaining))
                                }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("−") }
                                OutlinedButton(onClick = {
                                    val maxes = magic.spellSlotsMax.toMutableList()
                                    val remaining = magic.spellSlotsRemaining.toMutableList()
                                    maxes[levelIndex] = (maximum + 1).coerceAtMost(20)
                                    remaining[levelIndex] = (left + 1).coerceAtMost(maxes[levelIndex])
                                    updateMagic(magic.copy(spellSlotsMax = maxes, spellSlotsRemaining = remaining))
                                }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("+") }
                            }
                        }
                        if (maximum > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                repeat(maximum.coerceAtMost(20)) { idx ->
                                    Box(Modifier.size(15.dp).clip(CircleShape)
                                        .background(if (idx < left) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape))
                                }
                            }
                        }
                    }
                }
                if (showMagicSetup) Text("Количество ячеек задаётся вручную по правилам вашего персонажа. Потраченные восстанавливаются при начале нового дня после лагеря.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Button(
            onClick = {
                if (!inCombat) {
                    inCombat = true
                    saveCombat()
                    onEvent("Начал бой")
                } else {
                    abilities.indices.forEach { index ->
                        abilities[index] = abilities[index].copy(isUsed = false)
                    }
                    inCombat = false
                    saveAbilities()
                    saveCombat()
                    onEvent("Закончил бой")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (inCombat) "Закончить бой" else "Начать бой")
        }

        Text(
            if (inCombat) {
                "Бой начался. Используй способности и их индивидуальные активаторы."
            } else {
                "Начни бой, чтобы использовать способности."
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (deleteMode) {
            Text(
                "Режим удаления: нажми на карточку способности, чтобы удалить её.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        }

        if (abilities.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text("Способностей пока нет.", Modifier.padding(16.dp))
            }
        }

        abilities.forEach { ability ->
            val isMagic = ability.spellLevel != 0
            val spellIndex = ability.spellLevel - 1
            val hasSlot = ability.spellLevel == -1 ||
                (spellIndex in 0..8 && magic.spellSlotsRemaining.getOrElse(spellIndex) { 0 } > 0)
            val isAvailable = !ability.isPassive && if (isMagic) hasSlot else inCombat && !ability.isUsed
            val hasActivatorDescription = ability.activatorDescription.isNotBlank()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = deleteMode && !ability.isStoryTrait) {
                        abilityForDelete = ability
                    },
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        ability.isPassive -> MaterialTheme.colorScheme.tertiaryContainer
                        ability.isUsed -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        ability.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (ability.description.isNotBlank()) {
                        Text(ability.description, fontSize = 14.sp)
                    }

                    if (ability.isPassive) {
                        Divider()
                        Text(
                            "ПАССИВНЫЙ ЭФФЕКТ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(ability.passiveDescription, fontSize = 13.sp)
                    } else {
                        Text(
                            when {
                                isMagic && !hasSlot -> "Не хватает магических ячеек"
                                isMagic -> if (ability.spellLevel == -1) "Заговор · без ячеек" else "Магия · ${ability.spellLevel} круг"
                                !inCombat -> "Доступна во время боя"
                                ability.isUsed -> "Использована — перезарядка"
                                else -> "Готова к использованию"
                            },
                            fontSize = 12.sp,
                            color = if (ability.isUsed) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )

                        Button(
                            onClick = {
                                val index = abilities.indexOfFirst { it.id == ability.id }
                                if (index != -1 && isAvailable && !deleteMode) {
                                    if (isMagic) {
                                        if (spellIndex in 0..8) {
                                            val remaining = magic.spellSlotsRemaining.toMutableList()
                                            if (remaining[spellIndex] <= 0) return@Button
                                            remaining[spellIndex] -= 1
                                            updateMagic(magic.copy(spellSlotsRemaining = remaining))
                                        }
                                        // Magical abilities don't go on cooldown while slots remain.
                                    } else {
                                        abilities[index] = abilities[index].copy(isUsed = true)
                                        saveAbilities()
                                    }
                                    onEvent("Использовал способность «${ability.name}»")
                                }
                            },
                            enabled = isAvailable && !deleteMode,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(
                                when {
                                    isMagic && !hasSlot -> "Нет магических ячеек"
                                    isMagic -> "Применить магию"
                                    !inCombat -> "Доступна только в бою"
                                    ability.isUsed -> "Использована"
                                    else -> "Использовать"
                                }
                            )
                        }

                    }

                    if (hasActivatorDescription) {
                        Divider()
                        Text(
                            "АКТИВАТОР",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(ability.activatorDescription, fontSize = 13.sp)
                        if (!ability.isPassive && !isMagic) {
                            OutlinedButton(
                                onClick = {
                                    val index = abilities.indexOfFirst { it.id == ability.id }
                                    if (index != -1 && inCombat && abilities[index].isUsed) {
                                        // Перезаряжаем только эту способность.
                                        abilities[index] = abilities[index].copy(isUsed = false)
                                        saveAbilities()
                                        onEvent("Перезарядил способность «${ability.name}»")
                                    }
                                },
                                enabled = inCombat && ability.isUsed && !deleteMode,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    when {
                                        !inCombat -> "Активатор доступен только в бою"
                                        !ability.isUsed -> "Способность ещё не использована"
                                        else -> "Активировать — восстановить способность"
                                    }
                                )
                            }
                        }
                    }

                    if (ability.isStoryTrait) {
                        Text("🔒 Особенность истории героя. Изменять и удалять может только мастер.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    } else if (deleteMode) {
                        Text(
                            "Нажми на эту карточку для удаления",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    abilityName = ""
                    abilityDescription = ""
                    hasActivator = false
                    activatorDescription = ""
                    hasPassive = false
                    passiveDescription = ""
                    deleteMode = false
                    showAddDialog = true
                },
                modifier = Modifier.weight(1f),
                enabled = !deleteMode
            ) {
                Text("+ Добавить")
            }

            OutlinedButton(
                onClick = {
                    deleteMode = !deleteMode
                    abilityForDelete = null
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (deleteMode) "Готово" else "− Удалить")
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("← Назад к персонажу")
        }
    }

    // ДИАЛОГ ДОБАВЛЕНИЯ СПОСОБНОСТИ
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Новая способность") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { showAbilityCatalog = true }) { Text("📚 Выбрать способность из библиотеки") }
                    OutlinedTextField(
                        value = abilityName,
                        onValueChange = { abilityName = it },
                        label = { Text("Название способности") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = abilityDescription,
                        onValueChange = { abilityDescription = it },
                        label = { Text("Описание способности (необязательно)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Divider()

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = hasActivator,
                            onCheckedChange = {
                                hasActivator = it
                                if (!it) activatorDescription = ""
                            }
                        )
                        Text("У способности есть активатор")
                    }

                    if (hasActivator) {
                        OutlinedTextField(
                            value = activatorDescription,
                            onValueChange = { activatorDescription = it },
                            label = { Text("Что нужно сделать для перезарядки?") },
                            placeholder = { Text("Например: успешно уклониться от атаки") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }

                    Divider()

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = hasPassive,
                            onCheckedChange = {
                                hasPassive = it
                                if (!it) passiveDescription = ""
                            }
                        )
                        Text("Это пассивная способность")
                    }

                    if (hasPassive) {
                        OutlinedTextField(
                            value = passiveDescription,
                            onValueChange = { passiveDescription = it },
                            label = { Text("Описание пассивного эффекта") },
                            placeholder = { Text("Например: +2 к защите постоянно") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }

                    if (!hasPassive) {
                        Divider()
                        Text("Тип применения", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(0 to "Обычная", -1 to "Заговор", 1 to "Заклинание").forEach { (level, label) ->
                                OutlinedButton(onClick = { spellLevel = level }, modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if ((spellLevel > 0 && level == 1) || spellLevel == level)
                                            MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                                    Text(label, fontSize = 11.sp)
                                }
                            }
                        }
                        if (spellLevel > 0) {
                            Text("Круг заклинания · $spellLevel", fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { spellLevel = (spellLevel - 1).coerceAtLeast(1) },
                                    modifier = Modifier.weight(1f)) { Text("−") }
                                OutlinedButton(onClick = { spellLevel = (spellLevel + 1).coerceAtMost(9) },
                                    modifier = Modifier.weight(1f)) { Text("+") }
                            }
                        }
                    }

                    Text(
                        "Активатор и пассивный эффект можно указать независимо друг от друга. Пассивная способность не требует нажатия.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = abilityName.isNotBlank() &&
                            (!hasActivator || activatorDescription.isNotBlank()) &&
                            (!hasPassive || passiveDescription.isNotBlank()),
                    onClick = {
                        abilities.add(
                            Ability(
                                name = abilityName.trim(),
                                description = abilityDescription.trim(),
                                activatorDescription = if (hasActivator) activatorDescription.trim() else "",
                                passiveDescription = if (hasPassive) passiveDescription.trim() else "",
                                spellLevel = if (hasPassive) 0 else spellLevel
                            )
                        )
                        saveAbilities()
                        onEvent("Добавил способность «${abilityName.trim()}»")
                        showAddDialog = false
                        spellLevel = 0
                    }
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showAbilityCatalog) RpgCatalogPickerDialog(
        title = "База способностей",
        entries = RpgEquipmentAbilitiesCatalog.abilities,
        onSelect = { chosen ->
            abilityName = chosen.name
            abilityDescription = chosen.description
            showAbilityCatalog = false
        },
        onDismiss = { showAbilityCatalog = false }
    )

    // ПОДТВЕРЖДЕНИЕ УДАЛЕНИЯ
    abilityForDelete?.let { ability ->
        AlertDialog(
            onDismissRequest = { abilityForDelete = null },
            title = { Text("Удалить способность?") },
            text = { Text("Способность «${ability.name}» будет удалена без возможности восстановления.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (ability.isStoryTrait) { abilityForDelete = null; return@TextButton }
                        abilities.removeAll { it.id == ability.id }
                        saveAbilities()
                        onEvent("Удалил способность «${ability.name}»")
                        abilityForDelete = null
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { abilityForDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}



// БИБЛИОТЕКА КАРТ
@Composable
fun MapLibraryScreen(
    onBack: () -> Unit,
    onOpenMap: (GameMap) -> Unit
) {
    val context = LocalContext.current
    val maps = remember {
        mutableStateListOf<GameMap>().apply {
            addAll(CharacterStorage.loadMaps(context))
        }
    }

    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var mapName by remember { mutableStateOf("") }
    var mapForDelete by remember { mutableStateOf<GameMap?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Некоторые провайдеры не поддерживают постоянное разрешение.
            }
            pendingImageUri = uri
            mapName = ""
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Библиотека карт", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(
            "Общие карты — они доступны всем персонажам.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = { imagePicker.launch(arrayOf("image/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("＋ Добавить карту")
        }

        if (maps.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "Пока карт нет. Нажми «Добавить карту», чтобы выбрать изображение.",
                    Modifier.padding(18.dp)
                )
            }
        }

        maps.forEach { map ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapImage(
                        uri = map.imageUri,
                        modifier = Modifier.fillMaxWidth().height(190.dp)
                    )
                    Text(map.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    if ("Сетевая" in map.tags) {
                        Text("🌐 Сетевая · доступна без подключения",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpenMap(map) }, modifier = Modifier.weight(1f)) {
                            Text("Открыть")
                        }
                        OutlinedButton(onClick = { mapForDelete = map }) {
                            Text("Удалить")
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Назад")
        }
    }

    pendingImageUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImageUri = null },
            title = { Text("Новая карта") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MapImage(uri.toString(), Modifier.fillMaxWidth().height(150.dp))
                    OutlinedTextField(
                        value = mapName,
                        onValueChange = { mapName = it },
                        label = { Text("Название карты") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newMap = GameMap(
                            name = mapName.trim(),
                            imageUri = uri.toString()
                        )
                        maps.add(newMap)
                        CharacterStorage.saveMaps(context, maps.toList())
                        pendingImageUri = null
                    },
                    enabled = mapName.isNotBlank()
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImageUri = null }) { Text("Отмена") }
            }
        )
    }

    mapForDelete?.let { map ->
        AlertDialog(
            onDismissRequest = { mapForDelete = null },
            title = { Text("Удалить карту?") },
            text = { Text("Карта «${map.name}» будет удалена из общей библиотеки.") },
            confirmButton = {
                TextButton(onClick = {
                    maps.removeAll { it.id == map.id }
                    CharacterStorage.saveMaps(context, maps.toList())
                    mapForDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { mapForDelete = null }) { Text("Отмена") }
            }
        )
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext: Context = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

@Composable
fun MapDetailsScreen(
    map: GameMap,
    character: Character,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val density = LocalDensity.current

    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = map.imageUri
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(Uri.parse(map.imageUri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    val offlineFog = remember(map.id) { LanWorldStorage.loadOfflineFog(context, map.id) }
    val offlineRestricted = offlineFog.enabled

    val markers = remember(character.id, map.id) {
        mutableStateListOf<MapMarker>().apply {
            addAll(CharacterStorage.loadCharacterMapMarkers(context, character.id, map.id))
        }
    }

    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val originalOrientation = rememberSaveable {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val originalSystemUi = rememberSaveable {
        activity?.window?.decorView?.systemUiVisibility ?: View.SYSTEM_UI_FLAG_VISIBLE
    }
    var toolsExpanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf("Обычная") }
    var waitingForMapTap by remember { mutableStateOf(false) }
    // При перемещении метка двигается джойстиком, а исходная позиция нужна для отмены.
    var markerToMoveId by remember { mutableStateOf<Long?>(null) }
    var originalMarkerPosition by remember { mutableStateOf<MapMarker?>(null) }
    var pendingPosition by remember { mutableStateOf<Offset?>(null) }
    var markerName by remember { mutableStateOf("") }
    var markerDescription by remember { mutableStateOf("") }
    var selectedMarker by remember { mutableStateOf<MapMarker?>(null) }
    var showMarkerCatalog by remember { mutableStateOf(false) }

    // Сохраняем исходные настройки окна и возвращаем их при выходе с экрана карты.
    DisposableEffect(activity) {
        onDispose {
            if (activity != null) {
                activity.requestedOrientation = originalOrientation
                activity.window.decorView.systemUiVisibility = originalSystemUi
            }
        }
    }

    SideEffect {
        if (activity != null) {
            if (isFullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                activity.window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                or View.SYSTEM_UI_FLAG_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        )
            } else {
                activity.requestedOrientation = originalOrientation
                activity.window.decorView.systemUiVisibility = originalSystemUi
            }
        }
    }

    fun saveMarkers() {
        CharacterStorage.saveCharacterMapMarkers(context, character.id, map.id, markers.toList())
    }

    val markerTypes = remember {
        listOf("Обычная", "Сундук", "Враг", "NPC", "Босс", "Опасность",
            "Игрок", "Атака", "Защита", "Укрытие", "Задание", "Другое")
            .map { it to RpgCatalog.markerIcon(it) }
    }

    if (!isFullscreen) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(map.name, modifier = Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Button(onClick = { isFullscreen = true }) {
                    Text("⛶ На весь экран")
                }
            }
            Text(
                "Отметки персонажа: ${character.name}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(if (offlineRestricted) "Сетевая карта: скрытые участки доступны только после открытия мастером."
                else "Открой карту на весь экран, чтобы удобно расставлять отметки.", fontSize = 13.sp)

            if (bitmap != null) {
                val ratio = bitmap!!.width.toFloat() / bitmap!!.height.coerceAtLeast(1)
                Box(Modifier.fillMaxWidth().heightIn(max = 360.dp), contentAlignment = Alignment.Center) {
                    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(ratio)) {
                        Image(bitmap = bitmap!!, contentDescription = "Карта ${map.name}",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        if (offlineRestricted) Canvas(Modifier.fillMaxSize()) {
                            val tileW = size.width / 48f
                            val tileH = size.height / 32f
                            for (row in 0 until 32) for (col in 0 until 48) {
                                if (offlineFog.revealed.getOrNull(row * 48 + col) != '1')
                                    drawRect(Color(0xF700030A), Offset(col * tileW, row * tileH),
                                        androidx.compose.ui.geometry.Size(tileW + .35f, tileH + .35f))
                            }
                        }
                    }
                }
            } else {
                Text("Не удалось загрузить изображение карты")
            }

            Text("Отметки (${markers.size})", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (markers.isEmpty()) {
                Text("На этой карте у персонажа пока нет отметок.")
            } else {
                markers.filter { offlineFog.visible(it.x, it.y) }.forEachIndexed { index, marker ->
                    Card(Modifier.fillMaxWidth().clickable { selectedMarker = marker }) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${RpgCatalog.markerIcon(marker.type)}  ${index + 1}. ${marker.name}", fontWeight = FontWeight.SemiBold)
                            if (marker.description.isNotBlank()) Text(marker.description, fontSize = 13.sp)
                        }
                    }
                }
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("← К библиотеке карт")
            }
        }
    } else {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) {
            if (bitmap != null) {
                val imageRatio = bitmap!!.width.toFloat() / bitmap!!.height.toFloat()
                val availableWidth = maxWidth
                val availableHeight = maxHeight
                val fittedWidth = if (availableWidth / imageRatio <= availableHeight) {
                    availableWidth
                } else {
                    availableHeight * imageRatio
                }
                val fittedHeight = fittedWidth / imageRatio
                val imageLeft = (availableWidth - fittedWidth) / 2
                val imageTop = (availableHeight - fittedHeight) / 2
                val imagePixelWidth = with(density) { fittedWidth.toPx() }
                val imagePixelHeight = with(density) { fittedHeight.toPx() }

                Image(
                    bitmap = bitmap!!,
                    contentDescription = "Полноэкранная карта ${map.name}",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(fittedWidth)
                        .height(fittedHeight)
                        .pointerInput(map.id, character.id, imagePixelWidth, imagePixelHeight, waitingForMapTap) {
                            detectTapGestures { tap ->
                                if (waitingForMapTap) {
                                    val newX = (tap.x / imagePixelWidth).coerceIn(0f, 1f)
                                    val newY = (tap.y / imagePixelHeight).coerceIn(0f, 1f)
                                    markerName = ""
                                    markerDescription = ""
                                    pendingPosition = Offset(newX, newY)
                                    waitingForMapTap = false
                                }
                            }
                        },
                    contentScale = ContentScale.Fit
                )

                markers.filter { offlineFog.visible(it.x, it.y) }.forEachIndexed { index, marker ->
                    val markerEmoji = RpgCatalog.markerIcon(marker.type)
                    Box(
                        Modifier
                            .offset(
                                x = imageLeft + fittedWidth * marker.x - 18.dp,
                                y = imageTop + fittedHeight * marker.y - 18.dp
                            )
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
                            .clickable(enabled = markerToMoveId == null) { selectedMarker = marker },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            markerEmoji,
                            fontSize = 17.sp
                        )
                    }
                }
                if (offlineRestricted) {
                    Canvas(Modifier.align(Alignment.Center).width(fittedWidth).height(fittedHeight)) {
                        val tileW = size.width / 48f
                        val tileH = size.height / 32f
                        for (row in 0 until 32) for (col in 0 until 48) {
                            if (offlineFog.revealed.getOrNull(row * 48 + col) != '1') {
                                drawRect(Color(0xF700030A), topLeft = Offset(col * tileW, row * tileH),
                                    size = androidx.compose.ui.geometry.Size(tileW + .35f, tileH + .35f))
                            }
                        }
                    }
                }
            } else {
                Text("Не удалось загрузить изображение карты", Modifier.align(Alignment.Center))
            }

            // Верхняя компактная панель.
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 5.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(map.name, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${markers.size} меток", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }

            // Кнопка сворачивания полноэкранного режима.
            FilledTonalButton(
                onClick = {
                    isFullscreen = false
                    toolsExpanded = false
                    waitingForMapTap = false
                    markerToMoveId?.let { movingId ->
                        val original = originalMarkerPosition
                        val markerIndex = markers.indexOfFirst { it.id == movingId }
                        if (original != null && markerIndex != -1) markers[markerIndex] = original
                    }
                    markerToMoveId = null
                    originalMarkerPosition = null
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
            ) {
                Text("Свернуть ⤢")
            }

            if (waitingForMapTap && markerToMoveId == null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "Нажми на место карты для метки типа «$selectedType» · Отмена",
                        Modifier.clickable { waitingForMapTap = false }.padding(10.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Боковая панель управления перемещением: не перекрывает игровую карту.
            if (!offlineRestricted && markerToMoveId != null && bitmap != null) {
                val joystickRatio = bitmap!!.width.toFloat() / bitmap!!.height.toFloat()
                val joystickWidth = if (maxWidth / joystickRatio <= maxHeight) maxWidth else maxHeight * joystickRatio
                val joystickHeight = joystickWidth / joystickRatio
                val joystickPixelWidth = with(density) { joystickWidth.toPx() }
                val joystickPixelHeight = with(density) { joystickHeight.toPx() }

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .width(132.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        tonalElevation = 5.dp
                    ) {
                        Column(
                            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Перемещайте метку джойстиком",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(
                                onClick = {
                                    val movingId = markerToMoveId
                                    val original = originalMarkerPosition
                                    val markerIndex = markers.indexOfFirst { it.id == movingId }
                                    if (original != null && markerIndex != -1) markers[markerIndex] = original
                                    markerToMoveId = null
                                    originalMarkerPosition = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Отмена") }
                            Button(
                                onClick = {
                                    saveMarkers()
                                    markerToMoveId = null
                                    originalMarkerPosition = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Готово") }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .size(108.dp)
                            .pointerInput(markerToMoveId, joystickPixelWidth, joystickPixelHeight) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val markerIndex = markers.indexOfFirst { it.id == markerToMoveId }
                                    if (markerIndex != -1 && joystickPixelWidth > 0f && joystickPixelHeight > 0f) {
                                        // Ускорение 2.2x: небольшого движения пальца достаточно для точной коррекции.
                                        val current = markers[markerIndex]
                                        val nextX = (current.x + dragAmount.x * 2.2f / joystickPixelWidth).coerceIn(0f, 1f)
                                        val nextY = (current.y + dragAmount.y * 2.2f / joystickPixelHeight).coerceIn(0f, 1f)
                                        markers[markerIndex] = current.copy(x = nextX, y = nextY)
                                    }
                                }
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tonalElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✥", fontSize = 54.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Компактная панель вместо широкой сетки FilterChip, вылезавшей за экран.
            // В альбомной ориентации видны кнопка каталога и небольшой прокручиваемый список.
            if (!offlineRestricted) Column(
                Modifier.align(Alignment.BottomStart).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                if (toolsExpanded) {
                    Card(
                        modifier = Modifier.width(260.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            Modifier.padding(9.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("Добавить метку", fontWeight = FontWeight.Bold)
                            TextButton(
                                onClick = {
                                    toolsExpanded = false
                                    showMarkerCatalog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("📚 Выбрать из каталога") }
                            HorizontalDivider()
                            Text(
                                "Или выбрать только тип:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 120.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                markerTypes.forEach { (type, emoji) ->
                                    TextButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            selectedType = type
                                            waitingForMapTap = true
                                            toolsExpanded = false
                                        }
                                    ) {
                                        Text("$emoji  $type", modifier = Modifier.fillMaxWidth(), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                FloatingActionButton(onClick = { toolsExpanded = !toolsExpanded }) {
                    Text(if (toolsExpanded) "✕" else "📍")
                }
            }
        }
    }

    pendingPosition?.let { position ->
        AlertDialog(
            onDismissRequest = { pendingPosition = null },
            title = { Text("Новая метка · $selectedType") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = markerName,
                        onValueChange = { markerName = it },
                        label = { Text("Название") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = markerDescription,
                        onValueChange = { markerDescription = it },
                        label = { Text("Описание (необязательно)") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = markerName.isNotBlank(),
                    onClick = {
                        markers.add(
                            MapMarker(
                                name = markerName.trim(),
                                description = markerDescription.trim(),
                                type = selectedType,
                                x = position.x,
                                y = position.y
                            )
                        )
                        saveMarkers()
                        markerName = ""
                        markerDescription = ""
                        pendingPosition = null
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPosition = null }) { Text("Отмена") }
            }
        )
    }

    if (showMarkerCatalog) RpgCatalogPickerDialog(
        title = "Шаблоны меток и противников",
        entries = RpgCatalog.markerPresets,
        onSelect = { preset ->
            selectedType = preset.markerType
            markerName = preset.name
            markerDescription = preset.description
            waitingForMapTap = true
            toolsExpanded = false
            showMarkerCatalog = false
        },
        onDismiss = { showMarkerCatalog = false }
    )

    selectedMarker?.let { marker ->
        AlertDialog(
            onDismissRequest = { selectedMarker = null },
            title = { Text("${RpgCatalog.markerIcon(marker.type)} ${marker.name}") },
            text = {
                Text(marker.description.ifBlank { "Тип: ${marker.type}\nДля этой метки нет описания." })
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        markerToMoveId = marker.id
                        originalMarkerPosition = marker
                        waitingForMapTap = false
                        isFullscreen = true
                        toolsExpanded = false
                        selectedMarker = null
                    }) { Text("Переместить") }
                    TextButton(onClick = {
                        markers.removeAll { it.id == marker.id }
                        saveMarkers()
                        selectedMarker = null
                    }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedMarker = null }) { Text("Закрыть") }
            }
        )
    }
}

@Composable
private fun MapImage(
    uri: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = uri
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = "Изображение карты",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("Не удалось загрузить изображение")
        }
    }
}
