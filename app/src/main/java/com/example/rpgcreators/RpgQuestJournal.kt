package com.example.rpgcreators

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Quest data is separate from the special journal inventory item. */
internal data class RpgQuestObjective(val id: String = UUID.randomUUID().toString(), val text: String, val completed: Boolean = false)
internal data class RpgQuest(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val objectives: List<RpgQuestObjective> = emptyList(),
    val reward: String = "",
    val status: String = "new",
    val published: Boolean = true,
    /** Blank = all players; otherwise a participant's displayed LAN name. */
    val recipient: String = ""
)

internal object RpgQuestStorage {
    private fun prefs(context: Context) = context.getSharedPreferences("rpg_quests_v1", Context.MODE_PRIVATE)
    fun personalKey(characterId: Long) = "personal_$characterId"
    fun campaignKey(roomId: String) = "master_${roomId.take(100)}"
    fun clientKey(roomId: String, characterId: Long) = "client_${roomId.take(100)}_$characterId"
    fun encode(quests: List<RpgQuest>): String = JSONArray().apply {
        quests.forEach { q -> put(JSONObject().apply {
            put("id",q.id); put("title",q.title); put("description",q.description)
            put("reward",q.reward); put("status",q.status); put("published",q.published)
            put("recipient",q.recipient)
            put("objectives", JSONArray().apply { q.objectives.forEach { o -> put(JSONObject().apply {
                put("id",o.id); put("text",o.text); put("completed",o.completed)
            }) } })
        }) }
    }.toString()
    fun decode(raw: String): List<RpgQuest> = try {
        val a = JSONArray(raw)
        (0 until a.length().coerceAtMost(400)).map { i ->
            val j=a.getJSONObject(i); val os=j.optJSONArray("objectives") ?: JSONArray()
            RpgQuest(id=j.optString("id"),title=j.optString("title"),description=j.optString("description"),
                reward=j.optString("reward"),status=j.optString("status","new"),
                published=j.optBoolean("published",true),recipient=j.optString("recipient"),
                objectives=(0 until os.length().coerceAtMost(40)).map { k -> val o=os.getJSONObject(k)
                    RpgQuestObjective(o.optString("id"),o.optString("text"),o.optBoolean("completed")) })
        }
    } catch (_:Exception) { emptyList() }
    fun load(context:Context,key:String):List<RpgQuest> = decode(prefs(context).getString(key,"[]") ?: "[]")
    fun cachedCampaignQuests(context:Context, characterId:Long): List<RpgQuest> =
        prefs(context).all.keys.filter { it.startsWith("client_") && it.endsWith("_$characterId") }
            .flatMap { load(context,it) }.distinctBy { it.id }
    fun deleteCharacter(context:Context, characterId:Long) {
        val preferences=prefs(context)
        val editor=preferences.edit().remove(personalKey(characterId))
        preferences.all.keys.filter { it.startsWith("client_") && it.endsWith("_$characterId") }
            .forEach { editor.remove(it) }
        editor.apply()
    }


    fun save(context:Context,key:String,quests:List<RpgQuest>) {
        prefs(context).edit().putString(key,encode(quests)).apply()
    }
}

internal fun questStatusName(status: String) = when(status) {
    "new" -> "Не начато"; "progress" -> "В процессе"
    "done" -> "Завершено"; "failed" -> "Провалено"; else -> "Не начато"
}

/** Read-only for LAN guests; edit allowed for own offline quests or for the master. */
@Composable
internal fun RpgQuestJournalScreen(
    quests: List<RpgQuest>, onBack: () -> Unit,
    onSave: ((List<RpgQuest>) -> Unit)? = null,
    recipients: List<String> = emptyList(), title: String = "Журнал заданий"
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("active") }
    var editing by remember { mutableStateOf<RpgQuest?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    if (editing != null) {
        RpgQuestEditor(quest=editing!!, recipients=recipients, onCancel={editing=null}, onSave={new ->
            onSave?.invoke(quests.filterNot { it.id==new.id } + new)
            editing=null
        })
        return
    }
    if (pendingDelete != null) AlertDialog(onDismissRequest={pendingDelete=null},
        title={Text("Удалить задание?")}, text={Text("Это удалит задание из журнала и с устройств участников после синхронизации.")},
        confirmButton={TextButton(onClick={
            if (onSave != null) onSave(quests.filterNot { it.id==pendingDelete })
            pendingDelete=null; selectedId=null
        }) { Text("Удалить") }},
        dismissButton={TextButton(onClick={pendingDelete=null}){Text("Отмена")}})
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            Text(title, style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=onBack){Text("← Назад")}
        }
        if(onSave != null) Button(onClick={editing=RpgQuest(title="")}){Text("＋ Создать задание")}
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf("active" to "Активные", "done" to "Завершены", "failed" to "Провалены").forEach { (id,label) ->
                FilterChip(selected=filter==id,onClick={filter=id;selectedId=null},label={Text(label)})
            }
        }
        val visible=quests.filter { when(filter) {
            "done" -> it.status=="done"; "failed" -> it.status=="failed"
            else -> it.status!="done" && it.status!="failed"
        } }
        if(visible.isEmpty()) Text("Заданий пока нет",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            visible.forEach { q ->
                Card(Modifier.fillMaxWidth().clickable { selectedId=if(selectedId==q.id) null else q.id }) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(q.title,style=MaterialTheme.typography.titleMedium)
                        Text("${questStatusName(q.status)} · ${q.objectives.count {it.completed}}/${q.objectives.size} целей",style=MaterialTheme.typography.bodySmall)
                        if(q.recipient.isNotBlank()) Text("Личное: ${q.recipient}",style=MaterialTheme.typography.bodySmall)
                        if(onSave!=null && !q.published) Text("Не опубликовано",color=MaterialTheme.colorScheme.error)
                        if(selectedId==q.id) {
                            Text(q.description.ifBlank {"Описание отсутствует"})
                            q.objectives.forEach { o -> Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text(if(o.completed) "☑" else "☐"); Text(o.text)
                            } }
                            if(q.reward.isNotBlank()) Text("Награда: ${q.reward}")
                            if(onSave!=null) Row {
                                TextButton(onClick={editing=q}){Text("Изменить")}
                                TextButton(onClick={pendingDelete=q.id}){Text("Удалить")}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RpgQuestEditor(quest:RpgQuest,recipients:List<String>,onCancel:()->Unit,onSave:(RpgQuest)->Unit) {
    var title by remember(quest.id) { mutableStateOf(quest.title) }
    var description by remember(quest.id) { mutableStateOf(quest.description) }
    var reward by remember(quest.id) { mutableStateOf(quest.reward) }
    var status by remember(quest.id) { mutableStateOf(quest.status) }
    var recipient by remember(quest.id) { mutableStateOf(quest.recipient) }
    var published by remember(quest.id) { mutableStateOf(quest.published) }
    val objectives = remember(quest.id) { mutableStateListOf<RpgQuestObjective>().apply { addAll(quest.objectives) } }
    var nextObjective by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Редактор задания",style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=onCancel){Text("Отмена")}
        }
        OutlinedTextField(title,{title=it.take(120)}, label={Text("Название")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(description,{description=it.take(4000)},label={Text("Описание")},modifier=Modifier.fillMaxWidth(),minLines=3)
        Text("Цели задания",style=MaterialTheme.typography.titleMedium)
        objectives.toList().forEach { o ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                Checkbox(checked=o.completed,onCheckedChange={done->val idx=objectives.indexOfFirst{it.id==o.id};if(idx>=0)objectives[idx]=o.copy(completed=done)})
                OutlinedTextField(o.text,{value->val idx=objectives.indexOfFirst{it.id==o.id};if(idx>=0)objectives[idx]=o.copy(text=value.take(400))},modifier=Modifier.weight(1f),singleLine=true)
                TextButton(onClick={objectives.removeAll{it.id==o.id}}){Text("×")}
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            OutlinedTextField(nextObjective,{nextObjective=it.take(400)},label={Text("Новая цель")},modifier=Modifier.weight(1f))
            TextButton(enabled=nextObjective.isNotBlank(),onClick={objectives.add(RpgQuestObjective(text=nextObjective.trim()));nextObjective=""}){Text("＋")}
        }
        OutlinedTextField(reward,{reward=it.take(1000)},label={Text("Награда (текст)")},modifier=Modifier.fillMaxWidth())
        Text("Статус")
        var statusOpen by remember { mutableStateOf(false) }
        Box { OutlinedButton(onClick={statusOpen=true}){Text(questStatusName(status))}
            DropdownMenu(expanded=statusOpen,onDismissRequest={statusOpen=false}) {
                listOf("new","progress","done","failed").forEach { value ->
                    DropdownMenuItem(text={Text(questStatusName(value))},onClick={status=value;statusOpen=false})
                }
            }
        }
        if(recipients.isNotEmpty()) {
            var recipientOpen by remember { mutableStateOf(false) }
            Text("Кому назначить")
            Box { OutlinedButton(onClick={recipientOpen=true}){Text(recipient.ifBlank {"Всем игрокам"})}
                DropdownMenu(expanded=recipientOpen,onDismissRequest={recipientOpen=false}) {
                    (listOf("") + recipients.distinct()).forEach { name ->
                        DropdownMenuItem(text={Text(name.ifBlank {"Всем игрокам"})},onClick={recipient=name;recipientOpen=false})
                    }
                }
            }
        }
        Row { Text("Опубликовать игрокам",modifier=Modifier.weight(1f)); Switch(checked=published,onCheckedChange={published=it}) }
        Button(enabled=title.isNotBlank() && objectives.all{it.text.isNotBlank()},onClick={onSave(quest.copy(
            title=title.trim(),description=description.trim(),reward=reward.trim(),
            objectives=objectives.toList(),status=status,recipient=recipient,published=published
        ))},modifier=Modifier.fillMaxWidth()){Text("Сохранить задание")}
    }
}
