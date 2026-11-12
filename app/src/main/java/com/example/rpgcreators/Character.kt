package com.example.rpgcreators

/** Сложность перехода на следующий уровень. После 20-го тот же порог даёт очки навыков. */
const val MAX_CHARACTER_LEVEL = 20
const val POST_CAP_TREE_POINTS = 2
fun experienceForLevel(level: Int): Int =
    (650L + (level.coerceIn(1, MAX_CHARACTER_LEVEL).toLong() - 1L) * 750L)
        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

/** Всего обычных очков, заработанных за уровни, без очков после 20 уровня. */
fun levelTreePoints(level: Int) = 3 + 2 * (level.coerceIn(1, MAX_CHARACTER_LEVEL) - 1)

data class Character(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val race: String,
    val profession: String,
    val level: Int = 1,
    val experience: Int = 0,
    val experienceRequired: Int = 650,
    val autoExperience: Boolean = true,
    val prestigePoints: Int = 0
) {
    fun withLevel(newLevel: Int): Character {
        val lvl = newLevel.coerceIn(1, MAX_CHARACTER_LEVEL)
        val threshold = if (autoExperience) experienceForLevel(lvl) else experienceRequired.coerceAtLeast(1)
        return copy(level = lvl, experienceRequired = threshold, experience = experience.coerceIn(0, threshold - 1))
    }

    /** XP is never lost: at level 20 every extra full threshold grants 2 tree points. */
    fun addExperience(amount: Int): Character {
        if (amount <= 0) return this
        var lvl = level.coerceIn(1, MAX_CHARACTER_LEVEL)
        var xp = experience.toLong().coerceAtLeast(0) + amount.toLong()
        var points = prestigePoints.coerceAtLeast(0)
        fun threshold(level: Int): Long =
            (if (autoExperience) experienceForLevel(level) else experienceRequired.coerceAtLeast(1)).toLong()
        while (lvl < MAX_CHARACTER_LEVEL && xp >= threshold(lvl)) {
            xp -= threshold(lvl)
            lvl++
        }
        if (lvl == MAX_CHARACTER_LEVEL) {
            val required = threshold(lvl)
            val completions = xp / required
            points = (points.toLong() + completions * POST_CAP_TREE_POINTS)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            xp %= required
        }
        return copy(level = lvl, experience = xp.toInt(),
            experienceRequired = threshold(lvl).toInt(), prestigePoints = points)
    }
}
