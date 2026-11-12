package com.example.rpgcreators

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class TalentTier(val title: String, val cost: Int, val minLevel: Int) {
    SMALL("Малый", 1, 1), NOTABLE("Значимый", 2, 4), KEYSTONE("Ключевой", 3, 8), LEGENDARY("Легендарный", 5, 15)
}

data class TalentNode(
    val id: String, val title: String, val description: String,
    val tier: TalentTier, val attribute: String? = null, val amount: Int = 0,
    val parentId: String? = null, val x: Float = 0f, val y: Float = 0f,
    val branch: String = "Универсальное"
)

data class TalentState(val unlocked: Set<String> = emptySet(), val grantedPoints: Int = 0)

/** Versioned, stable node IDs. A known class/race maps to reproducible paths. */
object SkillTreeEngine {
    val attributes = listOf("Сила", "Ловкость", "Телосложение", "Интеллект", "Мудрость", "Харизма")
    private val common = listOf("Выносливость", "Восприятие", "Воля")

    private fun classTags(name: String): List<String> {
        val n = name.lowercase()
        return when {
            listOf("маг", "чарод", "колдун", "ведьм", "некром", "псион", "заклин", "ритуал").any(n::contains) -> listOf("Чары", "Контроль", "Поток")
            listOf("воин", "рыцар", "палад", "берсер", "самура", "страж", "гладиат", "мечник").any(n::contains) -> listOf("Клинок", "Оборона", "Натиск")
            listOf("стрел", "снайп", "лучник", "охотник", "развед", "следоп", "ассас").any(n::contains) -> listOf("Точность", "Скрытность", "Манёвр")
            listOf("инжен", "хакер", "нетран", "механ", "техник", "пилот", "кибер").any(n::contains) -> listOf("Технологии", "Тактика", "Расчёт")
            listOf("жрец", "друид", "шаман", "лекар", "целител", "монах", "бард").any(n::contains) -> listOf("Поддержка", "Дух", "Гармония")
            else -> listOf("Мастерство", "Стратегия", "Решимость")
        }
    }
    private fun raceTags(name: String): List<String> {
        val n = name.lowercase()
        return when {
            listOf("эльф", "фе", "кицун", "тэнгу", "дух", "дриад").any(n::contains) -> listOf("Наследие", "Чутьё")
            listOf("орк", "тролл", "огр", "великан", "гоблин", "дворф", "гном").any(n::contains) -> listOf("Стойкость", "Кровь")
            listOf("андроид", "киборг", "робот", "синтет", "автомат").any(n::contains) -> listOf("Системы", "Адаптация")
            listOf("вамп", "демон", "нежит", "оборот", "прокля").any(n::contains) -> listOf("Тьма", "Преображение")
            else -> listOf("Происхождение", "Адаптация")
        }
    }

    fun nodes(character: Character): List<TalentNode> {
        val list = mutableListOf(TalentNode("root", "Начало пути", "Центральная точка дерева", TalentTier.SMALL, x = 0f, y = 0f))
        val sections = listOf(
            "Основы" to common,
            "Класс" to classTags(character.profession),
            "Раса" to raceTags(character.race)
        )
        // 8 thematic branches, 20 connected steps each = 161 nodes total.
        val branches = sections.flatMap { (category, tags) -> tags.map { category to it } }
        branches.forEachIndexed { branchIndex, (category, branchName) ->
            val angle = -PI / 2.0 + branchIndex * 2.0 * PI / branches.size
            var parent = "root"
            for (step in 1..20) {
                val id = "v1_${category}_${branchIndex}_$step"
                val tier = when (step) {
                    10, 20 -> TalentTier.LEGENDARY
                    6, 9, 16, 19 -> TalentTier.KEYSTONE
                    3, 5, 8, 12, 15, 18 -> TalentTier.NOTABLE
                    else -> TalentTier.SMALL
                }
                val stat = attributes[(branchIndex + step / 3) % attributes.size]
                val isPassive = tier == TalentTier.KEYSTONE || tier == TalentTier.LEGENDARY
                val bonus = if (isPassive) 0 else if (tier == TalentTier.NOTABLE) 2 else 1
                val detail = if (isPassive) "Особая черта направления «$branchName». Её эффект применяется по решению мастера." else "+$bonus к характеристике «$stat» (считается автоматически)."
                val direction = angle + if (step % 2 == 0) 0.07 else -0.07
                val distance = (80 + step * 51).toFloat()
                val node = TalentNode(
                    id = id,
                    title = if (step == 10 || step == 20) "Легенда: $branchName · $step" else "$branchName · ${when(tier) { TalentTier.SMALL -> "основа"; TalentTier.NOTABLE -> "усиление"; TalentTier.KEYSTONE -> "веха"; TalentTier.LEGENDARY -> "легенда" }} $step",
                    description = detail, tier = tier,
                    attribute = if (isPassive) null else stat,
                    amount = bonus, parentId = parent,
                    x = (cos(direction) * distance).toFloat(), y = (sin(direction) * distance).toFloat(),
                    branch = "$category · $branchName"
                )
                list += node
                parent = id
            }
        }
        return list
    }
    fun totalPoints(character: Character, state: TalentState): Int =
        (levelTreePoints(character.level).toLong() + character.prestigePoints + state.grantedPoints)
            .coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    fun spent(nodes: List<TalentNode>, state: TalentState): Int =
        nodes.filter { it.id in state.unlocked }.sumOf { it.tier.cost }

    fun canLearn(character: Character, state: TalentState, node: TalentNode, nodes: List<TalentNode>): Boolean =
        node.id != "root" && node.id !in state.unlocked && character.level >= node.tier.minLevel &&
            (node.parentId == "root" || node.parentId in state.unlocked) &&
            totalPoints(character, state) - spent(nodes, state) >= node.tier.cost

    fun learn(character: Character, state: TalentState, node: TalentNode, nodes: List<TalentNode>): TalentState =
        if (canLearn(character, state, node, nodes)) state.copy(unlocked = state.unlocked + node.id) else state

    fun reset(state: TalentState) = state.copy(unlocked = emptySet())
    fun bonus(character: Character, state: TalentState): Map<String, Int> =
        nodes(character).filter { it.id in state.unlocked && it.attribute != null }
            .groupBy { it.attribute!! }.mapValues { (_, list) -> list.sumOf { it.amount } }
}

object SkillTreeStorage {
    private const val PREFS = "rpg_talent_tree_v1"
    fun load(context: Context, id: Long): TalentState {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val data = JSONObject(prefs.getString(id.toString(), "{}") ?: "{}")
            val nodes = data.optJSONArray("nodes") ?: JSONArray()
            TalentState((0 until nodes.length()).map { nodes.getString(it) }.toSet(), data.optInt("granted", 0))
        } catch (_: Exception) { TalentState() }
    }
    fun save(context: Context, id: Long, state: TalentState) {
        val data = JSONObject().put("nodes", JSONArray(state.unlocked.toList().sorted())).put("granted", state.grantedPoints)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(id.toString(), data.toString()).apply()
    }
    fun clear(context: Context, id: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(id.toString()).apply()
    }
}
