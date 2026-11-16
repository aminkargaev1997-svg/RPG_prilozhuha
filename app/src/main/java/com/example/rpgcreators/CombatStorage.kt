package com.example.rpgcreators

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small, versioned JSON protocol; host is authoritative and persists its own battle. */
internal object CombatStorage {
    private const val PREFS = "rpg_combat_v26"

    fun encode(s: CombatState, includePrivateIdentity: Boolean = true): JSONObject = JSONObject().apply {
        put("version", 1); put("phase", s.phase); put("round", s.round)
        put("current", s.currentId); put("revision", s.revision)
        put("fighters", JSONArray().apply {
            s.fighters.forEach { f -> put(JSONObject().apply {
                put("id", f.id); put("name", f.name); put("kind", f.kind)
                put("sourceId", f.sourceId); put("identity", if (includePrivateIdentity) f.stableIdentity else "")
                put("roll", f.initiativeRoll ?: JSONObject.NULL); put("bonus", f.initiativeBonus)
                put("actions", JSONArray(f.actions))
            }) }
        })
        put("events", JSONArray().apply { s.events.takeLast(45).forEach { e ->
            put(JSONObject().put("round", e.round).put("text", e.text))
        } })
    }

    fun decode(json: JSONObject): CombatState = runCatching {
        if (json.optInt("version") != 1) return@runCatching CombatState()
        val fighters = json.optJSONArray("fighters") ?: JSONArray()
        val people = (0 until minOf(fighters.length(), CombatEngine.MAX_FIGHTERS)).mapNotNull { i ->
            val f = fighters.optJSONObject(i) ?: return@mapNotNull null
            val id = f.optString("id").take(100)
            val name = f.optString("name").take(70)
            val kind = f.optString("kind")
            if (id.isBlank() || name.isBlank() || kind !in setOf("player", "marker", "custom")) return@mapNotNull null
            CombatFighter(id, name, kind, f.optString("sourceId").take(100),
                f.optString("identity").take(100),
                if (f.isNull("roll") || !f.has("roll")) null else f.optInt("roll").takeIf { it in 1..20 },
                f.optInt("bonus").coerceIn(-99, 99),
                (f.optJSONArray("actions") ?: JSONArray()).let { a ->
                    (0 until minOf(a.length(), 2)).map { a.optString(it) }
                        .filter { it in CombatEngine.ACTIONS }
                })
        }.distinctBy { it.id }
        val logs = json.optJSONArray("events") ?: JSONArray()
        val events = (maxOf(0, logs.length() - 45) until logs.length()).mapNotNull { i -> logs.optJSONObject(i)?.let {
            e -> CombatEvent(e.optInt("round").coerceAtLeast(0), e.optString("text").take(180))
        } }
        val phase = json.optString("phase").takeIf { it in setOf("idle", "setup", "active") } ?: "idle"
        val current = json.optString("current").takeIf { id -> people.any { it.id == id } }.orEmpty()
        CombatState(phase, json.optInt("round").coerceAtLeast(0), current, people, events,
            json.optLong("revision").coerceAtLeast(0))
    }.getOrDefault(CombatState())

    fun save(context: Context, roomId: String, state: CombatState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(roomId, encode(state).toString()).apply()
    }

    fun load(context: Context, roomId: String): CombatState = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(roomId, null)
        if (raw == null) CombatState() else decode(JSONObject(raw))
    }.getOrDefault(CombatState())
}
