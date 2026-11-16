package com.example.rpgcreators

/** RPG Creators combat uses physical dice. There is deliberately no random roll function. */
internal data class CombatFighter(
    val id: String,
    val name: String,
    val kind: String, // player, marker, custom
    val sourceId: String = "", // live LAN player id, map marker id, or empty
    val stableIdentity: String = "", // survives reconnects for player fighters
    val initiativeRoll: Int? = null,
    val initiativeBonus: Int = 0,
    val actions: List<String> = emptyList()
) {
    val initiative: Int? get() = initiativeRoll?.plus(initiativeBonus)
    val actionsSpent: Int get() = actions.size
    val movementAllowed: Boolean get() = "Перемещение" in actions
}

internal data class CombatEvent(val round: Int, val text: String)
internal data class CombatState(
    val phase: String = "idle", // idle, setup, active
    val round: Int = 0,
    val currentId: String = "",
    val fighters: List<CombatFighter> = emptyList(),
    val events: List<CombatEvent> = emptyList(),
    val revision: Long = 0
) {
    val current: CombatFighter? get() = fighters.firstOrNull { it.id == currentId }
    val ready: Boolean get() = fighters.isNotEmpty() && fighters.none { it.initiativeRoll == null }
}

internal object CombatEngine {
    const val MAX_FIGHTERS = 60
    val ACTIONS = listOf("Перемещение", "Атака", "Способность", "Предмет", "Защита / помощь")

    private fun log(s: CombatState, message: String): CombatState =
        s.copy(events = (s.events + CombatEvent(s.round, message.take(180))).takeLast(45))

    fun setup(): CombatState = CombatState(phase = "setup", events = listOf(CombatEvent(0, "Мастер объявил инициативу")))

    fun add(s: CombatState, fighter: CombatFighter): CombatState {
        if (s.phase != "setup" || s.fighters.size >= MAX_FIGHTERS || fighter.id.isBlank() ||
            fighter.name.isBlank() || s.fighters.any { it.id == fighter.id }) return s
        return log(s.copy(fighters = s.fighters + fighter.copy(actions = emptyList())),
            "Добавлен участник: ${fighter.name}")
    }

    fun remove(s: CombatState, id: String): CombatState {
        if (s.phase != "setup") return s
        val fighter = s.fighters.firstOrNull { it.id == id } ?: return s
        return log(s.copy(fighters = s.fighters.filterNot { it.id == id }), "Убран участник: ${fighter.name}")
    }

    fun initiative(s: CombatState, id: String, roll: Int, bonus: Int? = null): CombatState {
        if (s.phase != "setup" || roll !in 1..20) return s
        if (s.fighters.none { it.id == id }) return s
        val updated = s.fighters.map { fighter ->
            if (fighter.id == id) fighter.copy(initiativeRoll = roll,
                initiativeBonus = (bonus ?: fighter.initiativeBonus).coerceIn(-99, 99)) else fighter
        }
        val result = updated.first { it.id == id }
        return log(s.copy(fighters = updated), "${result.name}: d20=$roll, бонус ${result.initiativeBonus}, итог ${result.initiative}")
    }

    fun start(s: CombatState): CombatState {
        if (s.phase != "setup" || !s.ready) return s
        // Tie break: bonus descending, then join order. The master can revise initiative during setup.
        val order = s.fighters.withIndex().sortedWith(
            compareByDescending<IndexedValue<CombatFighter>> { it.value.initiative ?: Int.MIN_VALUE }
                .thenByDescending { it.value.initiativeBonus }.thenBy { it.index }
        ).map { it.value.copy(actions = emptyList()) }
        return log(s.copy(phase = "active", round = 1, fighters = order, currentId = order.first().id),
            "Бой начался. Первый ход: ${order.first().name}")
    }

    fun action(s: CombatState, actorId: String, label: String, details: String = ""): CombatState {
        if (s.phase != "active" || label !in ACTIONS || s.currentId != actorId) return s
        val actor = s.current ?: return s
        if (actor.actionsSpent >= 2) return s
        val updated = s.fighters.map { fighter ->
            if (fighter.id == actorId) fighter.copy(actions = fighter.actions + label) else fighter
        }
        val note = details.trim().take(90).takeIf { it.isNotEmpty() }?.let { " — $it" }.orEmpty()
        return log(s.copy(fighters = updated), "${actor.name}: $label$note (${2 - actor.actionsSpent - 1}/2 осталось)")
    }

    fun refund(s: CombatState): CombatState {
        if (s.phase != "active") return s
        val actor = s.current ?: return s
        if (actor.actionsSpent == 0) return s
        // Undo the latest action, keeping movement if another movement action remains.
        return log(s.copy(fighters = s.fighters.map { fighter ->
            if (fighter.id == actor.id) fighter.copy(actions = fighter.actions.dropLast(1))
            else fighter
        }), "Мастер вернул действие: ${actor.name}")
    }

    fun next(s: CombatState): CombatState {
        if (s.phase != "active" || s.fighters.isEmpty()) return s
        val now = s.fighters.indexOfFirst { it.id == s.currentId }
        val nextIndex = if (now < 0) 0 else (now + 1) % s.fighters.size
        val nextRound = if (now >= 0 && nextIndex == 0) s.round + 1 else s.round
        val fighters = s.fighters.map { fighter ->
            if (fighter.id == s.fighters[nextIndex].id) fighter.copy(actions = emptyList())
            else fighter
        }
        return log(s.copy(round = nextRound, fighters = fighters, currentId = fighters[nextIndex].id),
            "Ход: ${fighters[nextIndex].name}")
    }

    fun previous(s: CombatState): CombatState {
        if (s.phase != "active" || s.fighters.isEmpty()) return s
        val now = s.fighters.indexOfFirst { it.id == s.currentId }
        if (now < 0 || (s.round == 1 && now == 0)) return s
        val index = (now - 1 + s.fighters.size) % s.fighters.size
        val r = if (now == 0) s.round - 1 else s.round
        val fighters = s.fighters.map { if (it.id == s.fighters[index].id)
            it.copy(actions = emptyList()) else it }
        return log(s.copy(round = r, fighters = fighters, currentId = fighters[index].id),
            "Мастер вернул ход: ${fighters[index].name}")
    }

    fun finish(s: CombatState): CombatState = if (s.phase == "idle") s else
        log(s.copy(phase = "idle", round = 0, currentId = "", fighters = emptyList()), "Мастер завершил бой")
}
