package com.example.rpgcreators

import android.content.Context
import org.json.JSONObject

/** A master command delivered only over the established LAN host -> player socket. */
internal data class LanMasterEdit(
    val commandId: String,
    val characterId: Long,
    val operation: String,
    val payload: String
)

/**
 * Apply a single master command to the actual character saved on the PLAYER's device.
 * Never apply edits to the host's local copy: the host only observes snapshots.
 */
internal fun applyLanMasterEdit(
    context: Context,
    edit: LanMasterEdit,
    onCharacterUpdate: (Character) -> Unit
): Boolean {
    val original = CharacterStorage.loadCharacters(context).firstOrNull { it.id == edit.characterId }
        ?: return false
    val data = try { JSONObject(edit.payload) } catch (_: Exception) { return false }

    return try {
        when (edit.operation) {
            "identity" -> {
                val newName = data.optString("name").trim().take(80)
                val race = data.optString("race").trim().take(80)
                val profession = data.optString("profession").trim().take(80)
                if (newName.isBlank() || race.isBlank() || profession.isBlank()) return false
                onCharacterUpdate(original.copy(name = newName, race = race, profession = profession))
            }
            "level" -> {
                val level = data.optInt("value", 0)
                if (level !in 1..1_000_000) return false
                onCharacterUpdate(original.withLevel(level))
            }
            "grant_xp" -> {
                val amount = data.optInt("value", 0)
                if (amount !in 1..1_000_000_000) return false
                onCharacterUpdate(original.addExperience(amount))
            }
            "set_xp" -> {
                val xp = data.optInt("value", -1)
                if (xp < 0) return false
                // If the target XP crosses a threshold, level up normally.
                onCharacterUpdate(original.copy(experience = 0).addExperience(xp))
            }
            "max_xp" -> {
                val maximum = data.optInt("value", 0)
                if (maximum !in 1..Int.MAX_VALUE) return false
                onCharacterUpdate(original.copy(
                    experienceRequired = maximum,
                    experience = 0,
                    autoExperience = false
                ).addExperience(original.experience))
            }
            "auto_xp" -> {
                val required = experienceForLevel(original.level)
                onCharacterUpdate(original.copy(
                    experienceRequired = required,
                    experience = original.experience.coerceAtMost(required - 1),
                    autoExperience = true
                ))
            }
            "attribute" -> {
                val name = data.optString("name").trim().take(60)
                val value = data.optInt("value", Int.MIN_VALUE)
                if (name.isBlank() || value !in -100_000..100_000) return false
                val attributes = CharacterStorage.loadAttributes(context, edit.characterId).toMutableMap()
                attributes[name] = value
                CharacterStorage.saveAttributes(context, edit.characterId, attributes)
            }
            "item_save" -> {
                val name = data.optString("name").trim().take(100)
                if (name.isBlank()) return false
                val id = data.optLong("id", 0)
                val current = CharacterStorage.loadInventory(context, edit.characterId).toMutableList()
                val index = current.indexOfFirst { it.id == id }
                val item = InventoryItem(
                    id = if (index >= 0) id else System.nanoTime(),
                    name = name,
                    quantity = data.optString("quantity").take(60),
                    description = data.optString("description").take(500)
                )
                if (index >= 0) current[index] = item else current.add(item)
                CharacterStorage.saveInventory(context, edit.characterId, current)
            }
            "item_delete" -> {
                val id = data.optLong("id", 0)
                val current = CharacterStorage.loadInventory(context, edit.characterId).toMutableList()
                if (current.none { it.id == id }) return false
                current.removeAll { it.id == id }
                CharacterStorage.saveInventory(context, edit.characterId, current)
                val equipment = CharacterStorage.loadEquipment(context, edit.characterId)
                    .filterValues { it != id }
                CharacterStorage.saveEquipment(context, edit.characterId, equipment)
            }
            "ability_save" -> {
                val name = data.optString("name").trim().take(100)
                if (name.isBlank()) return false
                val id = data.optLong("id", 0)
                val current = CharacterStorage.loadAbilities(context, edit.characterId).toMutableList()
                val index = current.indexOfFirst { it.id == id }
                val ability = Ability(
                    id = if (index >= 0) id else System.nanoTime(),
                    name = name,
                    description = data.optString("description").take(500),
                    activatorDescription = data.optString("activator").take(500),
                    passiveDescription = data.optString("passive").take(500),
                    isUsed = data.optBoolean("used", false)
                )
                if (index >= 0) current[index] = ability else current.add(ability)
                CharacterStorage.saveAbilities(context, edit.characterId, current)
            }
            "ability_delete" -> {
                val id = data.optLong("id", 0)
                val current = CharacterStorage.loadAbilities(context, edit.characterId).toMutableList()
                if (current.none { it.id == id }) return false
                current.removeAll { it.id == id }
                CharacterStorage.saveAbilities(context, edit.characterId, current)
            }
            "equipment" -> {
                val slot = data.optString("slot").trim().take(60)
                val id = data.optLong("itemId", 0L)
                if (slot.isBlank()) return false
                if (id != 0L && CharacterStorage.loadInventory(context, edit.characterId).none { it.id == id }) return false
                val equipment = CharacterStorage.loadEquipment(context, edit.characterId).toMutableMap()
                if (id == 0L) equipment.remove(slot) else equipment[slot] = id
                CharacterStorage.saveEquipment(context, edit.characterId, equipment)
            }
            else -> return false
        }
        true
    } catch (_: Exception) {
        false
    }
}
