package com.example.rpgcreators

/** Categories are independent of a catalog entry's fantasy genre.
 *  No combat effects are inferred from category names.
 */
object ItemCatalogCategories {
    const val RING_LEFT = "Кольцо · левая рука"
    const val RING_RIGHT = "Кольцо · правая рука"
    // Legacy slots from v18: mapped to the ring fingers during loading.
    val legacyRingSlots = setOf(RING_LEFT, RING_RIGHT)
    val fingers = listOf("Большой", "Указательный", "Средний", "Безымянный", "Мизинец")
    val leftFingerSlots = fingers.map { "Кольцо · левая кисть · $it" }
    val rightFingerSlots = fingers.map { "Кольцо · правая кисть · $it" }
    val ringSlots = (leftFingerSlots + rightFingerSlots).toSet()

    fun fingerSlot(right: Boolean, index: Int): String =
        (if (right) rightFingerSlots else leftFingerSlots)[index.coerceIn(0, 4)]

    fun fingerLabel(slot: String): String = when (slot.substringAfterLast(" · ", "")) {
        "Большой" -> "Большой палец"
        "Указательный" -> "Указательный палец"
        "Средний" -> "Средний палец"
        "Безымянный" -> "Безымянный палец"
        "Мизинец" -> "Мизинец"
        else -> slot
    }

    /** v18 had one ring per hand. Preserve it on the ring finger in v19.
     *  Never duplicate the same inventory item across slots on migration.
     */
    fun migrateRingSlots(equipment: Map<String, Long>): Map<String, Long> {
        if (equipment.keys.none { it in legacyRingSlots }) return equipment
        val result = equipment.toMutableMap()
        listOf(RING_LEFT to leftFingerSlots, RING_RIGHT to rightFingerSlots).forEach { (old, slots) ->
            val itemId = result.remove(old) ?: return@forEach
            if (itemId <= 0L || itemId in result.values) return@forEach
            (listOf(slots[3]) + slots).firstOrNull { it !in result }?.let { result[it] = itemId }
        }
        return result
    }
    val all = listOf("Оружие", "Броня", "Кольца", "Украшения", "Боеприпасы",
        "Расходники", "Инструменты", "Материалы", "Квестовые", "Прочее")

    private fun String.hasAny(vararg words: String): Boolean = words.any { contains(it) }

    /** Classify known catalog names by their *kind*, not by their setting.
     *  Unknown custom items keep their explicitly chosen category.
     */
    fun classify(name: String, fallback: String = "Прочее"): String {
        val n = name.trim().lowercase()
        return when {
            n.hasAny("кольц", "перстн", "перстен") -> "Кольца"
            n.hasAny("патрон", "стрел", "заряд", "болт", "колчан", "боеприпас") -> "Боеприпасы"
            n.hasAny("щит", "брон", "доспех", "латы", "кольчуг", "кирас", "шлем", "скафандр", "сапог", "куртк", "кимоно", "мантия", "плащ", "противогаз", "перчатк", "бронепластин") -> "Броня"
            n.hasAny("меч", "клинок", "кинжал", "нож", "пистолет", "револьвер", "карабин", "арбалет", "лук", "бластер", "копь", "кусаригам", "кол ", "посох", "катана") -> "Оружие"
            n.hasAny("амулет", "талисман", "оберег", "браслет", "цепочк", "венок", "бусин", "жетон", "монокл", "очк") -> "Украшения"
            n.hasAny("зель", "эликсир", "флакон", "инжектор", "аптечк", "паёк", "воды", "ракет", "шашк", "тушь", "чаша саке", "соль ", "солью") -> "Расходники"
            n.hasAny("ключ", "пропуск", "письмо", "дневник", "досье", "запись", "план ", "карта ", "чертёж", "книга", "гримуар", "свиток", "блокнот", "справочник", "фотографи") -> "Квестовые"
            n.hasAny("набор", "отмычк", "компас", "фонарь", "лампа", "телескоп", "терминал", "планшет", "камера", "датчик", "анализатор", "счётчик", "рация", "магнитофон", "рюкзак", "сумка", "чемодан", "факел", "верёвк", "фильтр", "глушител", "телефон", "интерфейс", "дрон", "доска", "сосуд", "зеркало", "хронометр", "манометр", "лира", "кубок", "купол", "дека", "кристалл чар", "коготь") -> "Инструменты"
            n.hasAny("кристалл", "камень", "руна", "осколок", "прах", "пепел", "шестерн", "проволок", "цепь", "зубчат", "пружин", "катушка", "элемент", "батаре", "образец", "обломок", "изолента", "мел ", "маска", "лента", "нить", "перо", "клык", "пластин", "благовони") -> "Материалы"
            else -> fallback
        }
    }

    fun isRing(name: String, category: String): Boolean =
        category == "Кольца" || classify(name) == "Кольца"
}
