
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rpgcreators.ui.theme.RPGCreatorsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

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

    when (currentScreen) {

        "home" -> RoleSelectionScreen(
            onPlayer = { appRole = "player"; currentScreen = "playerMenu" },
            onMaster = { appRole = "master"; currentScreen = "masterMenu" }
        )
        "playerMenu" -> MainScreen(
            modifier,
            { currentScreen = "create" },
            { currentScreen = "list" },
            { currentScreen = "lan" },
            onBack = { currentScreen = "home" }
        )
        "masterMenu" -> MasterMenuScreen(
            onNetwork = { currentScreen = "lan" },
            onBack = { currentScreen = "home" }
        )

        "lan" -> LanGameScreen(
            modifier = modifier,
            characters = characters.toList(),
            onBack = { currentScreen = if (appRole == "master") "masterMenu" else "playerMenu" },
            isMaster = appRole == "master",
            onCharacterUpdate = { updated ->
                val index = characters.indexOfFirst { it.id == updated.id }
                if (index >= 0) {
                    characters[index] = updated
                    CharacterStorage.saveCharacters(context, characters.toList())
                }
            }
        )

        "create" -> CreateCharacterScreen(
            onBack = { currentScreen = "home" },
            onCreate = {
                characters.add(it)
                CharacterStorage.saveCharacters(context, characters.toList())
                currentScreen = "list"
            }
        )

        "list" -> CharacterListScreen(
            characters,
            onBack = { currentScreen = "home" },
            onSelectCharacter = {
                selectedCharacterId = it.id
                currentScreen = "details"
            }
        )

        "details" -> selectedCharacter?.let { character ->
            CharacterDetailsScreen(
                character,
                onBack = { currentScreen = "list" },
                onPlay = { currentScreen = "game" },
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
                onMaps = { currentScreen = "maps" }
            )
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
                        currentScreen = "list"
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
fun MainScreen(
    modifier: Modifier = Modifier,
    onCreateCharacter: () -> Unit,
    onShowCharacters: () -> Unit,
    onNetwork: () -> Unit,
    onBack: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "RPG Creators",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onBack) { Text("← Выбор роли") }

        Text("Создай своего персонажа")

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onCreateCharacter,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Создать персонажа")
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onShowCharacters,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Мои персонажи")
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onNetwork,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сетевая игра (LAN)")
        }

    }
}

@Composable
fun CharacterListScreen(
    characters: List<Character>,
    onBack: () -> Unit,
    onSelectCharacter: (Character) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Мои персонажи",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        if (characters.isEmpty()) {
            Text("Пока персонажей нет.")
        }

        characters.forEach { character ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectCharacter(character)
                    }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        character.name,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text("${character.race} • ${character.profession}")
                }
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("← Назад на главную")
        }
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
                        onUpdateCharacter(character.addExperience(amount))
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
                        val extraLevels = total / max
                        val updated = character.copy(
                            level = (character.level.toLong() + extraLevels).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            experience = total % max,
                            experienceRequired = max,
                            autoExperience = false
                        )
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            character.name,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Text("Раса: ${character.race}", fontSize = 18.sp)
        Text("Профессия: ${character.profession}", fontSize = 18.sp)

        Text("Уровень ${character.level}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("${character.experience}/${character.experienceRequired} опыта", fontSize = 18.sp)
        LinearProgressIndicator(
            progress = { character.experience.toFloat() / character.experienceRequired.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { experienceInput = ""; showExperienceDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Добавить опыт") }
        OutlinedButton(
            onClick = { maximumInput = character.experienceRequired.toString(); showMaximumDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Изменить максимум опыта") }
        Text(if (character.autoExperience) "Опыт рассчитывается автоматически: 650 + 750 × (уровень − 1)"
             else "Установлен ручной максимум опыта", fontSize = 12.sp)
        if (!character.autoExperience) {
            TextButton(onClick = {
                val max = experienceForLevel(character.level)
                onUpdateCharacter(character.copy(
                    experienceRequired = max,
                    experience = character.experience.coerceAtMost(max - 1),
                    autoExperience = true
                ))
            }) { Text("Включить автоматический максимум") }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Играть")
        }

        Button(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Редактировать")
        }

        Button(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Удалить персонажа")
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("← Назад к списку")
        }
    }
}

data class InventoryItem(
    val id: Long = System.nanoTime(),
    val name: String,
    val quantity: String = "",
    val description: String = "",
    val position: Offset = Offset.Zero
)

data class Ability(
    val id: Long = System.nanoTime(),
    val name: String,
    val description: String = "",
    val activatorDescription: String = "",
    val passiveDescription: String = "",
    val isUsed: Boolean = false
) {
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
    "Голова",
    "Тело",
    "Руки",
    "Ноги",
    "Голени"
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
    onMaps: () -> Unit,
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

    var showAddDialog by remember { mutableStateOf(false) }
    var deleteMode by remember { mutableStateOf(false) }

    var itemName by remember { mutableStateOf("") }
    var itemDescription by remember { mutableStateOf("") }
    var selectedInventoryItem by remember { mutableStateOf<InventoryItem?>(null) }
    var showInventoryDetails by remember { mutableStateOf(false) }
    var hasQuantity by remember { mutableStateOf(false) }
    var itemQuantity by remember { mutableStateOf("") }

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

                equipmentSlots.forEach { slot ->
                    val item = inventoryItems.firstOrNull {
                        it.id == equipment[slot]
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                selectedSlot = slot
                            }
                            .padding(
                                horizontal = 14.dp,
                                vertical = 11.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            slot,
                            modifier = Modifier.width(88.dp),
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            item?.name ?: "Пусто",
                            color = if (item == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
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

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    defaultAttributes.take(3).forEach { attribute ->
                        AttributeDiamond(
                            attribute,
                            attributes[attribute] ?: 10,
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
                            attributes[attribute] ?: 10,
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

        // ИНВЕНТАРЬ

        Text(
            "Инвентарь",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
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

            LaunchedEffect(inventoryItems.size, maxX, maxY) {
                inventoryItems.forEach { item ->
                    if (positions[item.id] == null) {
                        positions[item.id] = Offset(
                            Random.nextFloat() * maxX,
                            Random.nextFloat() * maxY
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
                                selectedInventoryItem = item
                                showInventoryDetails = false
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
        AlertDialog(
            onDismissRequest = { selectedInventoryItem = null },
            title = { Text(item.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (item.quantity.isNotBlank()) Text("Количество: ${item.quantity}")
                    if (showInventoryDetails) {
                        Text(item.description.ifBlank { "Описание не добавлено." })
                    } else {
                        Text("Информация о предмете скрыта.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInventoryDetails = !showInventoryDetails }) {
                    Text(if (showInventoryDetails) "Скрыть описание" else "О предмете")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedInventoryItem = null }) { Text("Закрыть") }
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                            (!hasQuantity || itemQuantity.isNotBlank()),
                    onClick = {
                        inventoryItems.add(
                            InventoryItem(
                                name = itemName.trim(),
                                description = itemDescription.trim(),
                                quantity = if (hasQuantity) {
                                    itemQuantity.trim()
                                } else {
                                    ""
                                }
                            )
                        )

                        saveInventory()
                        onEvent("Добавил предмет «${itemName.trim()}»")
                        showAddDialog = false

                        itemName = ""
                        itemDescription = ""
                        itemQuantity = ""
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

    // ДИАЛОГ ЭКИПИРОВКИ

    selectedSlot?.let { slot ->
        val currentlyEquipped = inventoryItems.firstOrNull {
            it.id == equipment[slot]
        }

        AlertDialog(
            onDismissRequest = {
                selectedSlot = null
            },
            title = {
                Text("Слот: $slot")
            },
            text = {
                Column {
                    Text("Выбери любой предмет из инвентаря:")

                    if (inventoryItems.isEmpty()) {
                        Text("Инвентарь пока пуст.")
                    }

                    inventoryItems.forEach { item ->
                        TextButton(
                            onClick = {
                                equipment[slot] = item.id
                                saveEquipment()
                                selectedSlot = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                item.name +
                                        if (item.quantity.isNotBlank()) {
                                            " (${item.quantity})"
                                        } else {
                                            ""
                                        }
                            )
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

    var inCombat by remember(character.id) {
        mutableStateOf(CharacterStorage.loadCombatState(context, character.id))
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var deleteMode by remember { mutableStateOf(false) }
    var abilityForDelete by remember { mutableStateOf<Ability?>(null) }

    var abilityName by remember { mutableStateOf("") }
    var abilityDescription by remember { mutableStateOf("") }
    var hasActivator by remember { mutableStateOf(false) }
    var activatorDescription by remember { mutableStateOf("") }
    var hasPassive by remember { mutableStateOf(false) }
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
            val isAvailable = inCombat && !ability.isPassive && !ability.isUsed
            val hasActivatorDescription = ability.activatorDescription.isNotBlank()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = deleteMode) {
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
                                if (index != -1 && inCombat && !abilities[index].isUsed) {
                                    abilities[index] = abilities[index].copy(isUsed = true)
                                    saveAbilities()
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
                        if (!ability.isPassive) {
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

                    if (deleteMode) {
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
                                passiveDescription = if (hasPassive) passiveDescription.trim() else ""
                            )
                        )
                        saveAbilities()
                        onEvent("Добавил способность «${abilityName.trim()}»")
                        showAddDialog = false
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

    // ПОДТВЕРЖДЕНИЕ УДАЛЕНИЯ
    abilityForDelete?.let { ability ->
        AlertDialog(
            onDismissRequest = { abilityForDelete = null },
            title = { Text("Удалить способность?") },
            text = { Text("Способность «${ability.name}» будет удалена без возможности восстановления.") },
            confirmButton = {
                TextButton(
                    onClick = {
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

    val markerTypes = listOf(
        "Обычная" to "📍",
        "Сундук" to "📦",
        "Враг" to "⚔️",
        "NPC" to "👤",
        "Задание" to "📜",
        "Босс" to "💀",
        "Опасность" to "⚠️",
        "Другое" to "◆"
    )

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
            Text("Открой карту на весь экран, чтобы удобно расставлять отметки.", fontSize = 13.sp)

            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = "Карта ${map.name}",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("Не удалось загрузить изображение карты")
            }

            Text("Отметки (${markers.size})", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (markers.isEmpty()) {
                Text("На этой карте у персонажа пока нет отметок.")
            } else {
                markers.forEachIndexed { index, marker ->
                    Card(Modifier.fillMaxWidth().clickable { selectedMarker = marker }) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${markerTypes.firstOrNull { it.first == marker.type }?.second ?: "📍"}  ${index + 1}. ${marker.name}", fontWeight = FontWeight.SemiBold)
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

                markers.forEachIndexed { index, marker ->
                    val markerEmoji = markerTypes.firstOrNull { it.first == marker.type }?.second ?: "📍"
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
            if (markerToMoveId != null && bitmap != null) {
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

            // Плавающая кнопка и раскрывающаяся мини-панель типов меток.
            Column(
                Modifier.align(Alignment.BottomStart).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                if (toolsExpanded) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Тип метки", fontWeight = FontWeight.Bold)
                            markerTypes.chunked(4).forEach { rowTypes ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    rowTypes.forEach { (type, emoji) ->
                                        FilterChip(
                                            selected = selectedType == type,
                                            onClick = {
                                                selectedType = type
                                                waitingForMapTap = true
                                                toolsExpanded = false
                                            },
                                            label = { Text("$emoji $type", fontSize = 11.sp) }
                                        )
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
                        pendingPosition = null
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPosition = null }) { Text("Отмена") }
            }
        )
    }

    selectedMarker?.let { marker ->
        AlertDialog(
            onDismissRequest = { selectedMarker = null },
            title = { Text("${markerTypes.firstOrNull { it.first == marker.type }?.second ?: "📍"} ${marker.name}") },
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
