package com.example.rpgcreators

/** What the character actually holds. Ring fingers and gloves are different slots. */
object EquipmentHands {
    const val LEFT = "Левая рука"
    const val RIGHT = "Правая рука"
    const val OLD_SHIELD = "Щит"
    val handSlots = listOf(LEFT, RIGHT)

    fun isShield(item: InventoryItem): Boolean =
        item.name.lowercase().contains("щит") || item.name.lowercase().contains("shield")

    /** Conservatively classify only unmistakable two-handed weapons. */
    fun isTwoHanded(item: InventoryItem): Boolean {
        if (item.category != "Оружие" && item.effects.none { it.kind == "weapon" }) return false
        val name = item.name.lowercase()
        return listOf("двуруч", "алебард", "глеф", "длинный лук", "короткий лук",
            "арбалет", "секира", "пика", "longbow", "shortbow", "greatsword", "greataxe",
            "halberd", "glaive", "crossbow").any { name.contains(it) }
    }

    /** Old saves had a standalone shield slot, and sometimes a weapon in `Руки`.
     *  Transfer only recognized held equipment; do not move gloves or duplicate an item.
     */
    fun migrate(old: Map<String, Long>, items: List<InventoryItem>): Map<String, Long> {
        val result = ItemCatalogCategories.migrateRingSlots(old).toMutableMap()
        val oldShield = result.remove(OLD_SHIELD)
        if (oldShield != null && oldShield > 0 && oldShield !in result.values) {
            handSlots.firstOrNull { it !in result }?.let { result[it] = oldShield }
                ?: run { result[OLD_SHIELD] = oldShield } // never silently lose an equipped item
        }
        val oldArms = result["Руки"]
        if (oldArms != null) {
            val item = items.firstOrNull { it.id == oldArms }
            if (item != null && (isShield(item) || item.category == "Оружие" ||
                    item.effects.any { it.kind == "weapon" })) {
                val hand = handSlots.firstOrNull { it !in result }
                if (hand != null) {
                    result.remove("Руки")
                    result[hand] = oldArms
                }
            }
        }
        return result
    }

    /** Prevent using both hands with a two-handed item or simultaneously wearing two shields. */
    fun canEquip(slot: String, item: InventoryItem, current: Map<String, Long>, items: List<InventoryItem>): Boolean {
        if (!ItemEffectEngine.slotCompatible(slot, item)) return false
        if (slot !in handSlots) return true
        val otherSlot = if (slot == LEFT) RIGHT else LEFT
        val other = items.firstOrNull { it.id == current[otherSlot] }
        if (other != null && (isTwoHanded(item) || isTwoHanded(other))) return false
        if (other != null && isShield(item) && isShield(other)) return false
        return true
    }
}
