package com.example.rpgcreators

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Private, device-local notes. Not included in any LAN packet or host synchronization. */
internal data class RpgPersonalNote(
    val id: String = UUID.randomUUID().toString(),
    val heading: String = "",
    val body: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

internal object RpgPersonalNotesStorage {
    private const val PREFS = "rpg_private_notes_v1"
    private fun key(characterId: Long) = "character_$characterId"

    fun load(context: Context, characterId: Long): List<RpgPersonalNote> = try {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(characterId), "[]") ?: "[]")
        (0 until arr.length().coerceAtMost(2000)).map { index ->
            val obj = arr.getJSONObject(index)
            RpgPersonalNote(
                id = obj.optString("id"),
                heading = obj.optString("heading"),
                body = obj.optString("body"),
                updatedAt = obj.optLong("updatedAt", 0L)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun save(context: Context, characterId: Long, notes: List<RpgPersonalNote>) {
        val arr = JSONArray()
        notes.forEach { note ->
            arr.put(JSONObject().apply {
                put("id", note.id)
                put("heading", note.heading)
                put("body", note.body)
                put("updatedAt", note.updatedAt)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key(characterId), arr.toString()).apply()
    }
}

@Composable
internal fun RpgPersonalNotesScreen(characterId: Long, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notes = remember(characterId) {
        mutableStateListOf<RpgPersonalNote>().apply {
            addAll(RpgPersonalNotesStorage.load(context, characterId))
        }
    }
    var filter by remember(characterId) { mutableStateOf("") }
    var editingId by remember(characterId) { mutableStateOf<String?>(null) }
    var heading by remember(characterId) { mutableStateOf("") }
    var body by remember(characterId) { mutableStateOf("") }
    var confirmDeleteId by remember(characterId) { mutableStateOf<String?>(null) }
    var confirmDiscard by remember(characterId) { mutableStateOf(false) }

    fun store() = RpgPersonalNotesStorage.save(context, characterId, notes.toList())
    fun openNote(note: RpgPersonalNote) {
        editingId = note.id; heading = note.heading; body = note.body
    }
    fun leaveEditor() {
        val existing = notes.firstOrNull { it.id == editingId }
        if (existing != null && (existing.heading != heading || existing.body != body) ||
            existing == null && (heading.isNotBlank() || body.isNotBlank())) {
            confirmDiscard = true
        } else {
            if (existing != null && existing.heading.isBlank() && existing.body.isBlank()) notes.removeAll { it.id == editingId }
            editingId = null
        }
    }

    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Отменить изменения?") },
        text = { Text("Несохранённый текст будет потерян.") },
        confirmButton = {
            TextButton(onClick = { confirmDiscard = false; if (notes.firstOrNull { it.id == editingId }?.let { it.heading.isEmpty() && it.body.isEmpty() } == true) notes.removeAll { it.id == editingId }; editingId = null }) { Text("Не сохранять") }
        },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Продолжить") } }
    )
    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("Удалить заметку?") },
            text = { Text("Восстановить её после удаления будет нельзя.") },
            confirmButton = {
                TextButton(onClick = {
                    notes.removeAll { it.id == id }
                    store(); confirmDeleteId = null; editingId = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text("Отмена") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { if (editingId != null) leaveEditor() else onBack() }) {
                Text("← Назад")
            }
            Text(if (editingId == null) "Личные заметки" else "Редактор заметки",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(72.dp))
        }
        if (editingId == null) {
            Text("Только на этом устройстве · Для выбранного персонажа",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it },
                    label = { Text("Поиск по заметкам") },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    val note = RpgPersonalNote()
                    notes.add(0, note)
                    editingId = note.id; heading = ""; body = ""
                }, modifier = Modifier.padding(top = 7.dp)) { Text("+ Запись") }
            }
            val visible = notes.filter {
                it.heading.contains(filter, ignoreCase = true) ||
                    it.body.contains(filter, ignoreCase = true)
            }.sortedByDescending { it.updatedAt }
            if (visible.isEmpty()) {
                Text(if (notes.isEmpty()) "Пока нет записей. Добавь первую заметку."
                    else "По запросу ничего не найдено.")
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible, key = { it.id }) { note ->
                    Card(Modifier.fillMaxWidth().clickable { openNote(note) },
                        shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(note.heading.ifBlank { "Без названия" },
                                fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(note.body.ifBlank { "Пустая заметка" }, maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = heading, onValueChange = { heading = it.take(160) },
                label = { Text("Название (необязательно)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body, onValueChange = { body = it },
                label = { Text("Текст заметки") },
                placeholder = { Text("Имена, подсказки, слова NPC, важные детали...") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                minLines = 6
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmDeleteId = editingId }) { Text("Удалить") }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    val index = notes.indexOfFirst { it.id == editingId }
                    if (index >= 0) {
                        notes[index] = notes[index].copy(heading = heading.trim(), body = body,
                            updatedAt = System.currentTimeMillis())
                        store()
                    }
                    editingId = null
                }, enabled = heading.isNotBlank() || body.isNotBlank()) { Text("Сохранить") }
            }
        }
    }
}
