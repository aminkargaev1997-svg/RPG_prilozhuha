package com.example.rpgcreators

/** Опыт для перехода с текущего уровня на следующий: 650, 1400, 2150... */
fun experienceForLevel(level: Int): Int =
    (650L + (level.coerceAtLeast(1).toLong() - 1L) * 750L)
        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

data class Character(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val race: String,
    val profession: String,
    val level: Int = 1,
    val experience: Int = 0,
    val experienceRequired: Int = 650,
    val autoExperience: Boolean = true
) {
    fun withLevel(newLevel: Int): Character {
        val lvl = newLevel.coerceAtLeast(1)
        val max = if (autoExperience) experienceForLevel(lvl) else experienceRequired.coerceAtLeast(1)
        return copy(level = lvl, experienceRequired = max, experience = experience.coerceIn(0, max - 1))
    }

    fun addExperience(amount: Int): Character {
        if (amount <= 0) return this
        if (!autoExperience) {
            val threshold = experienceRequired.coerceAtLeast(1)
            val total = experience.toLong() + amount.toLong()
            val gainedLevels = (total / threshold).coerceAtMost((Int.MAX_VALUE - level).toLong()).toInt()
            return copy(level = level + gainedLevels, experience = (total % threshold).toInt())
        }
        var nextLevel = level.coerceAtLeast(1)
        var xp = experience.toLong().coerceAtLeast(0L) + amount.toLong()
        var required = experienceForLevel(nextLevel).toLong()
        while (xp >= required && nextLevel < Int.MAX_VALUE) {
            xp -= required
            nextLevel++
            required = experienceForLevel(nextLevel).toLong()
        }
        return copy(
            level = nextLevel,
            experience = xp.coerceAtMost(required - 1L).toInt(),
            experienceRequired = required.toInt()
        )
    }
}
