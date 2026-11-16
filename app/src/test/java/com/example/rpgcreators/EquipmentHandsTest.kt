package com.example.rpgcreators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentHandsTest {
    private val sword = InventoryItem(id = 1L, name = "Короткий меч", category = "Оружие",
        effects = ItemEffectEngine.template("Короткий меч"))
    private val shield = InventoryItem(id = 2L, name = "Щит", category = "Броня",
        effects = ItemEffectEngine.template("Щит"))
    private val bow = InventoryItem(id = 3L, name = "Длинный лук", category = "Оружие")
    private val glove = InventoryItem(id = 4L, name = "Кожаные перчатки", category = "Броня")
    private val ring = InventoryItem(id = 5L, name = "Кольцо защиты", category = "Кольца")

    @Test fun swordAndShieldCanBeHeldSimultaneously() {
        val equipment = mapOf(EquipmentHands.RIGHT to sword.id)
        assertTrue(EquipmentHands.canEquip(EquipmentHands.LEFT, shield, equipment, listOf(sword, shield)))
        assertTrue(EquipmentHands.canEquip(EquipmentHands.RIGHT, sword,
            mapOf(EquipmentHands.LEFT to shield.id), listOf(sword, shield)))
        val effects = ItemEffectEngine.total(listOf(sword, shield),
            mapOf(EquipmentHands.RIGHT to sword.id, EquipmentHands.LEFT to shield.id))
        assertEquals(2, effects.acBonus)
    }

    @Test fun doubleHandedWeaponLocksOtherHand() {
        assertFalse(EquipmentHands.canEquip(EquipmentHands.LEFT, shield,
            mapOf(EquipmentHands.RIGHT to bow.id), listOf(shield, bow)))
        assertFalse(EquipmentHands.canEquip(EquipmentHands.RIGHT, bow,
            mapOf(EquipmentHands.LEFT to shield.id), listOf(shield, bow)))
    }

    @Test fun ringsStayOnFingersNotHeldInHands() {
        assertFalse(EquipmentHands.canEquip(EquipmentHands.LEFT, ring, emptyMap(), listOf(ring)))
        assertTrue(ItemEffectEngine.slotCompatible(ItemCatalogCategories.leftFingerSlots[3], ring))
        assertFalse(ItemEffectEngine.slotCompatible("Руки", shield))
    }

    @Test fun migrateOldShieldAndOldWeaponWithoutLosingGloves() {
        val migrated = EquipmentHands.migrate(mapOf("Щит" to shield.id, "Руки" to sword.id),
            listOf(sword, shield))
        assertEquals(shield.id, migrated[EquipmentHands.LEFT])
        assertEquals(sword.id, migrated[EquipmentHands.RIGHT])
        assertFalse(migrated.containsKey("Щит"))
        assertFalse(migrated.containsKey("Руки"))

        val gloves = EquipmentHands.migrate(mapOf("Щит" to shield.id, "Руки" to glove.id),
            listOf(glove, shield))
        assertEquals(glove.id, gloves["Руки"])
        assertEquals(shield.id, gloves[EquipmentHands.LEFT])
    }

    @Test fun shieldsCannotBeDoubled() {
        val second = shield.copy(id = 6)
        assertFalse(EquipmentHands.canEquip(EquipmentHands.LEFT, second,
            mapOf(EquipmentHands.RIGHT to shield.id), listOf(shield, second)))
    }
}
