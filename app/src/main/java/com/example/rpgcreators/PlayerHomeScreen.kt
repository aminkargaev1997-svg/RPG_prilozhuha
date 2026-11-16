package com.example.rpgcreators

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The player starts with their heroes, not a menu of unrelated actions. */
@Composable
fun PlayerHeroesScreen(
    modifier: Modifier = Modifier,
    characters: List<Character>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (Character) -> Unit,
    onPlay: (Character) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Recompute on every observable-list update (a remembered list reference never changes).
    val filtered = characters.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) ||
            it.race.contains(query.trim(), ignoreCase = true) ||
            it.profession.contains(query.trim(), ignoreCase = true)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Column(Modifier.weight(1f)) {
                Text("Мои герои", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text("Выбери, кем отправишься в приключение",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onCreate, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Text("＋", fontSize = 24.sp)
            }
        }

        if (characters.size >= 6 || query.isNotBlank()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Поиск героя") },
                placeholder = { Text("Имя, раса или класс") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
        }

        if (characters.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(
                        Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("⚔", fontSize = 44.sp)
                        Text("Твоя история начинается здесь", style = MaterialTheme.typography.titleLarge)
                        Text("Создай первого героя. Инвентарь, способности, заметки и сетевые приключения будут доступны из него.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                            Text("＋ Создать героя")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Героев: ${characters.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(filtered, key = { it.id }) { character ->
                    PlayerHeroCard(
                        character = character,
                        onOpen = { onOpen(character) },
                        onPlay = { onPlay(character) }
                    )
                }
                if (filtered.isEmpty()) item {
                    Text("Таких героев пока нет.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PlayerHeroCard(character: Character, onOpen: () -> Unit, onPlay: () -> Unit) {
    val context = LocalContext.current
    val maxHp = AdventureVitalsStorage.maxHealth(context, character.id).coerceAtLeast(1)
    val hp = AdventureVitalsStorage.currentHealth(context, character.id).coerceIn(0, maxHp)
    val hpRatio = hp.toFloat() / maxHp.toFloat()
    Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))) {
        Column(Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(heroSymbol(character.profession), fontSize = 27.sp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(character.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text("${character.race} · ${character.profession}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Уровень ${character.level}",
                        style = MaterialTheme.typography.labelMedium)
                }
                Text("›", fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("♥ $hp/$maxHp", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                LinearProgressIndicator(
                    progress = { hpRatio },
                    modifier = Modifier.weight(1f).height(6.dp)
                )
            }
            Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                Text("▶ Играть")
            }
        }
    }
}

private fun heroSymbol(profession: String): String = when {
    profession.contains("маг", ignoreCase = true) ||
        profession.contains("колдун", ignoreCase = true) ||
        profession.contains("волшеб", ignoreCase = true) -> "✦"
    profession.contains("лучник", ignoreCase = true) ||
        profession.contains("следопыт", ignoreCase = true) -> "🏹"
    profession.contains("жрец", ignoreCase = true) ||
        profession.contains("паладин", ignoreCase = true) -> "✚"
    else -> "⚔"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPlayModeSheet(
    character: Character,
    onDismiss: () -> Unit,
    onOffline: () -> Unit,
    onLan: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Начать приключение", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Выбран герой: ${character.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(onClick = onOffline, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("📖", fontSize = 28.sp)
                    Column(Modifier.weight(1f)) {
                        Text("Обычная игра", fontWeight = FontWeight.SemiBold)
                        Text("Инвентарь, способности, задания и заметки",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", fontSize = 24.sp)
                }
            }
            Card(onClick = onLan, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("⚔", fontSize = 28.sp)
                    Column(Modifier.weight(1f)) {
                        Text("С друзьями · LAN", fontWeight = FontWeight.SemiBold)
                        Text("Найти комнату мастера или подключиться по IP",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", fontSize = 24.sp)
                }
            }
        }
    }
}
