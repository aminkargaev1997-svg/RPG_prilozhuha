package com.example.rpgcreators

/** Порог XP по уровням; на 20 уровне полная шкала даёт очко развития. */
const val MAX_CHARACTER_LEVEL = 20
const val POST_CAP_TREE_POINTS = 2 // Старое древо v1: правила не меняем.
const val POST_CAP_TREE_POINTS_V2 = 1
fun experienceForLevel(level: Int): Int =
    (650L + (level.coerceIn(1, MAX_CHARACTER_LEVEL).toLong() - 1L) * 750L)
        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

/** Новое древо: 3 на старте и +1/уровень. Старое: 3 и +2/уровень. */
fun levelTreePoints(level: Int, buildVersion: Int = 1): Int =
    3 + (if (buildVersion >= 2) 1 else 2) * (level.coerceIn(1, MAX_CHARACTER_LEVEL) - 1)

data class Character(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val race: String,
    val profession: String,
    val level: Int = 1,
    val experience: Int = 0,
    val experienceRequired: Int = 650,
    val autoExperience: Boolean = true,
    val prestigePoints: Int = 0,
    val buildVersion: Int = 1
) {
    fun withLevel(newLevel: Int): Character {
        val lvl = newLevel.coerceIn(1, MAX_CHARACTER_LEVEL)
        val threshold = if (autoExperience) experienceForLevel(lvl) else experienceRequired.coerceAtLeast(1)
        return copy(level = lvl, experienceRequired = threshold, experience = experience.coerceIn(0, threshold - 1))
    }

    /** XP сохраняется. На 20 уровне за полную шкалу даются очки по правилам версии древа.
     *  grantedTreePoints нужен для учёта наград мастера в пределе нового древа.
     */
    fun addExperience(amount: Int, grantedTreePoints: Int = 0): Character {
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
            val perCompletion = if (buildVersion >= 2) POST_CAP_TREE_POINTS_V2 else POST_CAP_TREE_POINTS
            val earned = (points.toLong() + completions * perCompletion)
                .coerceAtMost(Int.MAX_VALUE.toLong())
            points = if (buildVersion >= 2) {
                // Не создаём неиспользуемые очки сверх стоимости всего древа.
                val maxPrestige = (SkillTreeEngine.maxPoints(this) -
                    levelTreePoints(lvl, buildVersion).toLong() - grantedTreePoints.toLong())
                    .coerceIn(0L, Int.MAX_VALUE.toLong())
                earned.coerceAtMost(maxPrestige).toInt()
            } else earned.toInt()
            xp %= required
        }
        return copy(level = lvl, experience = xp.toInt(),
            experienceRequired = threshold(lvl).toInt(), prestigePoints = points)
    }
}
