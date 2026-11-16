package com.example.rpgcreators

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.json.JSONObject
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp

data class HeroStory(val text: String = "", val hook: String = "", val tone: String = "Приключенческая")

object HeroStoryStorage {
    private const val STORE = "rpg_hero_creation_v23"
    fun save(context: Context, id: Long, story: HeroStory) {
        val obj = JSONObject().put("text", story.text).put("hook", story.hook).put("tone", story.tone)
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString("hero_$id", obj.toString()).apply()
    }
    fun load(context: Context, id: Long): HeroStory? {
        val raw = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString("hero_$id", null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            HeroStory(obj.optString("text"), obj.optString("hook"), obj.optString("tone", "Приключенческая"))
        }.getOrNull()
    }
    fun clear(context: Context, id: Long) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().remove("hero_$id").apply()
    }
}


/** Small expandable summary on the character card. Full text remains editable after creation. */
@androidx.compose.runtime.Composable
fun HeroStoryCard(characterId: Long) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var story by androidx.compose.runtime.remember(characterId) {
        androidx.compose.runtime.mutableStateOf(HeroStoryStorage.load(context, characterId))
    }
    if (story == null) return
    var expanded by androidx.compose.runtime.remember(characterId) { androidx.compose.runtime.mutableStateOf(false) }
    var editing by androidx.compose.runtime.remember(characterId) { androidx.compose.runtime.mutableStateOf(false) }
    var draft by androidx.compose.runtime.remember(characterId) { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.material3.Card(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Text("✧ Нити судьбы", modifier = androidx.compose.ui.Modifier.weight(1f),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                androidx.compose.material3.TextButton(onClick = { expanded = !expanded }) {
                    androidx.compose.material3.Text(if (expanded) "Свернуть ↑" else "Читать ↓")
                }
            }
            androidx.compose.material3.Text("Зацепка для мастера: ${story?.hook.orEmpty()}",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            if (expanded) {
                androidx.compose.material3.HorizontalDivider()
                androidx.compose.material3.Text(story?.text.orEmpty())
                androidx.compose.material3.OutlinedButton(onClick = {
                    draft = story?.text.orEmpty(); editing = true
                }) { androidx.compose.material3.Text("Редактировать историю") }
            }
        }
    }
    if (editing) androidx.compose.material3.AlertDialog(
        onDismissRequest = { editing = false }, title = { androidx.compose.material3.Text("Моя история") },
        text = { androidx.compose.material3.OutlinedTextField(draft, { draft = it.take(5000) },
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(), minLines = 6) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = {
            story = story?.copy(text = draft.trim())
            story?.let { HeroStoryStorage.save(context, characterId, it) }
            editing = false
        }) { androidx.compose.material3.Text("Сохранить") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { editing = false }) {
            androidx.compose.material3.Text("Отмена")
        } }
    )
}
