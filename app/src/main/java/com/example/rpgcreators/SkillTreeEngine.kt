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
        if (character.buildVersion >= 2) return craftedNodes(character)
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

    /** Curated compact trees for new heroes; old v1 IDs remain untouched. */
    private fun craftedNodes(character: Character): List<TalentNode> {
        val classThemes = when (HeroCreationCatalog.classFamily(character.profession)) {
            "следопыт" -> listOf("Путь разведчика", "Меткий выстрел", "Знаток природы")
            "некромант" -> listOf("Могильные знания", "Защитный прах", "Диалог с тенью")
            "ловкач" -> listOf("Искусство тени", "Взлом и тайны", "Ложный манёвр")
            "хранитель" -> listOf("Оберег", "Клятва", "Свет исцеления")
            "друид" -> listOf("Зелёный зов", "Травничество", "Дикий путь")
            "маг" -> listOf("Рунное мастерство", "Защита чарами", "Поток магии")
            "бард" -> listOf("Голос легенды", "Вдохновение", "Маски и роли")
            "воин" -> listOf("Школа клинка", "Страж щита", "Выдержка")
            "технолог" -> listOf("Схемы мира", "Полевой ремонт", "Мастер механики")
            else -> listOf("Знание мира", "Ловкость пути", "Лидерство")
        }
        val raceThemes = when (HeroCreationCatalog.raceFamily(character.race)) {
            "халфлинг" -> listOf("Удача малого народа", "Проворство")
            "эльф" -> listOf("Древняя память", "Чутьё рощи")
            "дворф" -> listOf("Каменная воля", "Знание ремесла")
            "орк" -> listOf("Сила духа", "Дикая смекалка")
            "демон" -> listOf("Пепельная стойкость", "Пламенная тайна")
            "нежить" -> listOf("Эхо прошлого", "Могильная тишина")
            "дракон" -> listOf("Драконье наследие", "Хранитель кладов")
            "фея" -> listOf("Песня леса", "Загадки чар")
            "море" -> listOf("Танец течений", "Голос глубины")
            "механизм" -> listOf("Механическое чутьё", "Резервный привод")
            "небесный" -> listOf("Свет надежды", "Долг хранителя")
            "зверолюд" -> listOf("Охотничий инстинкт", "Сила стаи")
            "растение" -> listOf("Живые корни", "Тихая роща")
            else -> listOf("Родные традиции", "Зов открытий")
        }
        val branches = listOf("Общее" to "Путь искателя") +
            raceThemes.map { "Раса" to it } + classThemes.map { "Класс" to it }
        val result = mutableListOf(TalentNode("root", "Начало пути", "Выбери свой путь: общие, расовые и классовые направления.", TalentTier.SMALL))
        branches.forEachIndexed { branchIndex, (kind, title) ->
            val angle = -PI / 2 + branchIndex * 2 * PI / branches.size
            var previous = "root"
            for (step in 1..4) {
                val tier = when (step) {
                    1 -> TalentTier.SMALL
                    2 -> TalentTier.NOTABLE
                    3 -> TalentTier.KEYSTONE
                    else -> TalentTier.LEGENDARY
                }
                // Small class/race attribute progress after 4th level; early traits remain situational.
                val stat = if (step == 2) attributes[(branchIndex + 1) % attributes.size] else null
                val text = when (step) {
                    1 -> "Практика «$title»: +1 к одной тематической проверке по согласованию с мастером. Не добавляет +1 ко всем проверкам характеристики."
                    2 -> "Постоянно +1 к характеристике «$stat». Бонус применяется автоматически, когда навык открыт."
                    3 -> "Раз за игровой день: +2 к одной тематической проверке направления «$title» (решает мастер)."
                    else -> "Легендарный приём «$title»: раз за игровой день можно повторить одну неудачную тематическую проверку. Второй результат обязателен."
                }
                val radius = 82f + step * 63f
                val id = "v2_${kind}_${branchIndex}_$step"
                result.add(TalentNode(id, when(step) {
                    1 -> "$title · истоки"
                    2 -> "$title · мастерство"
                    3 -> "$title · особый приём"
                    else -> "$title · легенда"
                }, text, tier, stat, if (stat != null) 1 else 0,
                    previous, (cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat(), "$kind · $title"))
                previous = id
            }
        }
        return result
    }
    /** Динамический лимит: цена всех изучаемых узлов (корень бесплатный). */
    fun maxPoints(character: Character): Int =
        nodes(character).asSequence().filter { it.id != "root" }.sumOf { it.tier.cost }

    fun totalPoints(character: Character, state: TalentState): Int {
        val earned = levelTreePoints(character.level, character.buildVersion).toLong() +
            character.prestigePoints.toLong() + state.grantedPoints.toLong()
        val limit = if (character.buildVersion >= 2) maxPoints(character).toLong()
            else Int.MAX_VALUE.toLong()
        return earned.coerceIn(0L, limit).toInt()
    }

    fun spent(nodes: List<TalentNode>, state: TalentState): Int =
        nodes.filter { it.id != "root" && it.id in state.unlocked }.sumOf { it.tier.cost }

    /** При переходе с v23 выученные узлы сохраняются, даже если потрачено больше нового бюджета. */
    fun remainingPoints(character: Character, state: TalentState, nodes: List<TalentNode>): Int =
        (totalPoints(character, state) - spent(nodes, state)).coerceAtLeast(0)

    /** Бонус мастера ограничен суммарной стоимостью нового древа; у v1 правила прежние. */
    fun adjustGrantedPoints(character: Character, state: TalentState, delta: Int): TalentState {
        val maxGrant = if (character.buildVersion >= 2) {
            (maxPoints(character).toLong() - levelTreePoints(character.level, character.buildVersion) -
                character.prestigePoints.toLong()).coerceIn(-100_000L, 100_000L)
        } else 100_000L
        val next = (state.grantedPoints.toLong() + delta.toLong())
            .coerceIn(-100_000L, maxGrant.coerceAtLeast(-100_000L)).toInt()
        return state.copy(grantedPoints = next)
    }

    fun canLearn(character: Character, state: TalentState, node: TalentNode, nodes: List<TalentNode>): Boolean =
        node.id != "root" && node.id !in state.unlocked && character.level >= node.tier.minLevel &&
            (node.parentId == "root" || node.parentId in state.unlocked) &&
            remainingPoints(character, state, nodes) >= node.tier.cost

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
