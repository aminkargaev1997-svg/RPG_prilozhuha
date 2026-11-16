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
                if (level !in 1..MAX_CHARACTER_LEVEL) return false
                onCharacterUpdate(original.withLevel(level))
            }
            "grant_xp" -> {
                val amount = data.optInt("value", 0)
                if (amount !in 1..1_000_000_000) return false
                onCharacterUpdate(original.addExperience(amount,
                    SkillTreeStorage.load(context, edit.characterId).grantedPoints))
            }
            "set_xp" -> {
                val xp = data.optInt("value", -1)
                if (xp < 0) return false
                // If the target XP crosses a threshold, level up normally.
                onCharacterUpdate(original.copy(experience = 0).addExperience(xp,
                    SkillTreeStorage.load(context, edit.characterId).grantedPoints))
            }
            "max_xp" -> {
                val maximum = data.optInt("value", 0)
                if (maximum !in 1..Int.MAX_VALUE) return false
                onCharacterUpdate(original.copy(
                    experienceRequired = maximum,
                    experience = 0,
                    autoExperience = false
                ).addExperience(original.experience,
                    SkillTreeStorage.load(context, edit.characterId).grantedPoints))
            }
            "auto_xp" -> {
                val required = experienceForLevel(original.level)
                onCharacterUpdate(original.copy(
                    experienceRequired = required,
                    experience = original.experience.coerceAtMost(required - 1),
                    autoExperience = true
                ))
            }
            "skill_points" -> {
                val delta = data.optInt("value", Int.MIN_VALUE)
                if (delta !in -1000..1000) return false
                val current = SkillTreeStorage.load(context, edit.characterId)
                SkillTreeStorage.save(context, edit.characterId,
                    SkillTreeEngine.adjustGrantedPoints(original, current, delta))
            }
            "skill_reset" -> {
                val current = SkillTreeStorage.load(context, edit.characterId)
                SkillTreeStorage.save(context, edit.characterId, SkillTreeEngine.reset(current))
            }
            "skill_learn" -> {
                val requested = data.optString("id")
                val nodes = SkillTreeEngine.nodes(original)
                val node = nodes.firstOrNull { it.id == requested } ?: return false
                val current = SkillTreeStorage.load(context, edit.characterId)
                if (!SkillTreeEngine.canLearn(original, current, node, nodes)) return false
                SkillTreeStorage.save(context, edit.characterId,
                    SkillTreeEngine.learn(original, current, node, nodes))
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
                    description = data.optString("description").take(500),
                    category = data.optString("category", if (index >= 0) current[index].category else "Прочее").take(60),
                    effects = if (data.has("effects")) ItemEffectEngine.fromJson(data.optJSONArray("effects"))
                        else if (index >= 0) current[index].effects else ItemEffectEngine.template(name)
                )
                if (index >= 0) current[index] = item else current.add(item)
                CharacterStorage.saveInventory(context, edit.characterId, current)
            }
            "item_delete" -> {
                val id = data.optLong("id", 0)
                val current = CharacterStorage.loadInventory(context, edit.characterId).toMutableList()
                if (current.none { it.id == id } || current.any { it.id == id && CharacterStorage.isBoundItem(it) }) return false
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
                    isUsed = data.optBoolean("used", false),
                    spellLevel = data.optInt("spellLevel", if (index >= 0) current[index].spellLevel else 0).coerceIn(-1, 9),
                    origin = if (index >= 0) current[index].origin.ifBlank {
                        if (current[index].isStoryTrait) "hero_story" else ""
                    } else ""
                )
                if (index >= 0) current[index] = ability else current.add(ability)
                CharacterStorage.saveAbilities(context, edit.characterId, current)
            }
            "hero_story_pair" -> {
                // The master can replay a story. Replace the previous two traits in one
                // local save instead of stacking duplicates or leaving half a pair.
                val goodName = data.optString("goodName").trim().take(100)
                val badName = data.optString("badName").trim().take(100)
                val goodEffect = data.optString("goodEffect").trim().take(500)
                val badEffect = data.optString("badEffect").trim().take(500)
                if (listOf(goodName, badName, goodEffect, badEffect).any { it.isBlank() }) return false
                val current = CharacterStorage.loadAbilities(context, edit.characterId)
                    .filterNot { it.isStoryTrait }.toMutableList()
                val baseId = System.nanoTime()
                current.add(Ability(id = baseId, name = goodName,
                    description = "Дар из истории героя", passiveDescription = goodEffect,
                    origin = "hero_story"))
                current.add(Ability(id = baseId + 1, name = badName,
                    description = "Недостаток из истории героя", passiveDescription = badEffect,
                    origin = "hero_story"))
                CharacterStorage.saveAbilities(context, edit.characterId, current)
            }
            "adjust_health" -> {
                val delta = data.optInt("delta", 0)
                if (delta !in -100000..100000 || delta == 0) return false
                val current = AdventureVitalsStorage.currentHealth(context, edit.characterId)
                val maximum = AdventureVitalsStorage.effectiveMaxHealth(context, edit.characterId).coerceAtLeast(1)
                AdventureVitalsStorage.save(context, edit.characterId,
                    (current.toLong() + delta.toLong()).coerceIn(0L, maximum.toLong()).toInt(),
                    AdventureVitalsStorage.maxHealth(context, edit.characterId))
            }
            "adventure_rest" -> {
                // End-of-night restoration. Passive features stay unchanged.
                val abilities = CharacterStorage.loadAbilities(context, edit.characterId)
                CharacterStorage.saveAbilities(context, edit.characterId, abilities.map { it.copy(isUsed = false) })
                AdventureVitalsStorage.fullRest(context, edit.characterId)
                val sheet = DndSheetStorage.load(context, edit.characterId)
                DndSheetStorage.save(context, edit.characterId,
                    sheet.copy(spellSlotsRemaining = sheet.spellSlotsMax.toList()))
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
                val item = CharacterStorage.loadInventory(context, edit.characterId).firstOrNull { it.id == id }
                if (id != 0L && (item == null || CharacterStorage.isBoundItem(item) ||
                    (item.quantity.toIntOrNull() ?: 1) <= 0 ||
                    !EquipmentHands.canEquip(slot, item, CharacterStorage.loadEquipment(context, edit.characterId),
                        CharacterStorage.loadInventory(context, edit.characterId)))) return false
                val equipment = CharacterStorage.loadEquipment(context, edit.characterId).toMutableMap()
                if (id != 0L && equipment.any { (otherSlot, otherId) -> otherSlot != slot && otherId == id }) return false
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
