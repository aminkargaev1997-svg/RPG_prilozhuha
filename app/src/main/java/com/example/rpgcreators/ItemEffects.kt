package com.example.rpgcreators

import org.json.JSONArray
import org.json.JSONObject

/** Per-instance effects: catalog templates are copied, never mutated. */
data class ItemEffect(val kind: String, val target: String = "", val amount: Int = 0) {
    fun readable(): String = when (kind) {
        "armor" -> when (target) {
            "heavy" -> "Броня: КД $amount"
            "medium" -> "Броня: $amount + Ловк. (макс. +2)"
            "light" -> "Броня: $amount + Ловкость"
            else -> "Броня: КД $amount"
        }
        "ac" -> "КД ${signedAmount()}"
        "attribute" -> "$target ${signedAmount()}"
        "skill" -> "$target ${signedAmount()}"
        "save" -> "Спасбросок $target ${signedAmount()}"
        "maxhp" -> "Макс. HP ${signedAmount()}"
        "weapon" -> "Урон $target" // descriptive only: no digital dice
        else -> "$target ${signedAmount()}"
    }
    private fun signedAmount() = if (amount >= 0) "+$amount" else "$amount"
}

data class EquippedEffects(
    val attributes: Map<String, Int> = emptyMap(),
    val skills: Map<String, Int> = emptyMap(),
    val saves: Map<String, Int> = emptyMap(),
    val acBonus: Int = 0,
    val maxHpBonus: Int = 0,
    val armor: ItemEffect? = null
) {
    fun armorClass(manualAc: Int, dexterityScore: Int): Int {
        val dex = Math.floorDiv(dexterityScore - 10, 2)
        val base = when (armor?.target) {
            "heavy" -> armor.amount
            "medium" -> armor.amount + dex.coerceAtMost(2)
            "light" -> armor.amount + dex
            else -> manualAc
        }
        return (base + acBonus).coerceIn(0, 99)
    }
    fun acDescription(): String {
        val basis = armor?.readable() ?: "Ручной базовый КД"
        return "$basis; бонусы ${if (acBonus >= 0) "+" else ""}$acBonus"
    }
}

object ItemEffectEngine {
    fun total(items: List<InventoryItem>, slots: Map<String, Long>): EquippedEffects {
        val equipped = slots.entries.distinctBy { it.value }.mapNotNull { (slot, id) ->
            items.firstOrNull {
                it.id == id && (it.quantity.toIntOrNull() ?: 1) > 0 &&
                    ((slot !in ItemCatalogCategories.ringSlots && slot !in ItemCatalogCategories.legacyRingSlots) || ItemCatalogCategories.isRing(it.name, it.category))
            }?.let { slot to it }
        }
        val effects = equipped.flatMap { (slot, item) ->
            item.effects.filter { effect ->
                effect.kind != "armor" || slot == "Тело"
            }
        }
        fun summed(kind: String): Map<String, Int> = effects.filter { it.kind == kind }
            .groupBy { it.target }.mapValues { (_, values) -> values.sumOf { it.amount }.coerceIn(-100, 100) }
        // Only one worn armor is applied, and other equipment adds separately.
        return EquippedEffects(
            attributes = summed("attribute"), skills = summed("skill"), saves = summed("save"),
            acBonus = effects.filter { it.kind == "ac" }.sumOf { it.amount }.coerceIn(-50, 50),
            maxHpBonus = effects.filter { it.kind == "maxhp" }.sumOf { it.amount }.coerceIn(-9999, 9999),
            armor = effects.filter { it.kind == "armor" }.maxByOrNull { it.amount }
        )
    }

    fun slotCompatible(slot: String, item: InventoryItem): Boolean {
        val ring = ItemCatalogCategories.isRing(item.name, item.category)
        if (slot in ItemCatalogCategories.ringSlots || slot in ItemCatalogCategories.legacyRingSlots) return ring
        if (ring) return slot == "Аксессуар"
        val armor = item.effects.any { it.kind == "armor" }
        if (armor) return slot == "Тело"
        if (slot in EquipmentHands.handSlots) {
            // A hand may hold a weapon, shield, torch, potion or another object,
            // but not body armor or jewelry worn in other equipment slots.
            if (EquipmentHands.isShield(item)) return true
            return item.category !in setOf("Броня", "Украшения", "Кольца", "Боеприпасы")
        }
        if (slot == "Руки") return !EquipmentHands.isShield(item) &&
            (item.category == "Броня" || item.name.lowercase().contains("перчат") ||
                item.name.lowercase().contains("наруч"))
        if (EquipmentHands.isShield(item)) return slot == EquipmentHands.OLD_SHIELD // legacy saves only
        return true // Preserve flexible equipment outside the specialized held-item slots.
    }

    fun toJson(effects: List<ItemEffect>): JSONArray = JSONArray().apply {
        effects.take(24).forEach { e -> put(JSONObject().put("kind", e.kind).put("target", e.target).put("amount", e.amount)) }
    }
    fun fromJson(array: JSONArray?): List<ItemEffect> {
        if (array == null) return emptyList()
        return (0 until minOf(array.length(), 24)).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val kind = obj.optString("kind", "")
            if (kind !in setOf("armor", "ac", "attribute", "skill", "save", "maxhp", "weapon")) null
            else ItemEffect(kind, obj.optString("target", "").take(100), obj.optInt("amount", 0).coerceIn(-9999, 9999))
        }
    }

    /** Known classic 5e equipment. Fantasy/invented entries deliberately have no guessed modifiers. */
    fun template(name: String): List<ItemEffect> = when (name.trim().lowercase()) {
        "кольчуга" -> listOf(ItemEffect("armor", "heavy", 16))
        "кожаный доспех", "кожаная броня" -> listOf(ItemEffect("armor", "light", 11))
        "проклёпанная кожаная броня" -> listOf(ItemEffect("armor", "light", 12))
        "чешуйчатый доспех", "чешуйчатая броня" -> listOf(ItemEffect("armor", "medium", 14))
        "кираса" -> listOf(ItemEffect("armor", "medium", 14))
        "полулаты" -> listOf(ItemEffect("armor", "medium", 15))
        "латы", "полный латный доспех" -> listOf(ItemEffect("armor", "heavy", 18))
        "стальной щит", "щит" -> listOf(ItemEffect("ac", "", 2))
        "кинжал" -> listOf(ItemEffect("weapon", "1d4 колющего", 0))
        "короткий меч" -> listOf(ItemEffect("weapon", "1d6 колющего", 0))
        "длинный меч" -> listOf(ItemEffect("weapon", "1d8 рубящего (1d10 двумя руками)", 0))
        "короткий лук" -> listOf(ItemEffect("weapon", "1d6 колющего", 0))
        "длинный лук" -> listOf(ItemEffect("weapon", "1d8 колющего", 0))
        "булава" -> listOf(ItemEffect("weapon", "1d6 дробящего", 0))
        "боевой посох", "посох" -> listOf(ItemEffect("weapon", "1d6 дробящего (1d8 двумя руками)", 0))
        else -> emptyList()
    }
    fun templateCategory(name: String, fallback: String = "Прочее"): String =
        ItemCatalogCategories.classify(name, fallback)
    fun templateDescription(name: String, original: String): String {
        val props = template(name).joinToString(" · ") { it.readable() }
        return if (props.isBlank()) original else "$props. $original"
    }
}

