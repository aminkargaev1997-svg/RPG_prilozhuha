package com.example.rpgcreators

import org.junit.Assert.*
import org.junit.Test

class SkillTreeBalanceTest {
    private fun hero(version: Int = 2, level: Int = 1) = Character(
        id = 123, name = "Амин", race = "Халфлинг", profession = "Следопыт",
        buildVersion = version, level = level
    )

    @Test fun newTreeStartsWithThreeAndGainsOnePerLevel() {
        assertEquals(3, levelTreePoints(1, 2))
        assertEquals(7, levelTreePoints(5, 2))
        assertEquals(12, levelTreePoints(10, 2))
        assertEquals(22, levelTreePoints(20, 2))
        assertEquals(66, SkillTreeEngine.maxPoints(hero()))
        assertEquals(3, SkillTreeEngine.totalPoints(hero(), TalentState()))
    }

    @Test fun legacyTreeKeepsItsOwnRules() {
        assertEquals(3, levelTreePoints(1, 1))
        assertEquals(41, levelTreePoints(20, 1))
        val oldHero = hero(version = 1, level = 20).copy(autoExperience = false, experienceRequired = 100)
        assertEquals(4, oldHero.addExperience(200).prestigePoints)
    }

    @Test fun newTreePrestigeAddsOnePerCompletedXpBarAndStopsAtCap() {
        val level20 = hero(level = 20).copy(autoExperience = false, experienceRequired = 100)
        assertEquals(1, level20.addExperience(100).prestigePoints)
        assertEquals(3, level20.addExperience(350).prestigePoints)
        val nearCap = level20.copy(prestigePoints = 43)
        assertEquals(44, nearCap.addExperience(900).prestigePoints)
        assertEquals(66, SkillTreeEngine.totalPoints(nearCap.addExperience(900), TalentState()))
    }

    @Test fun masterGrantsAndPostCapXpShareTheSameLimit() {
        val level20 = hero(level = 20).copy(autoExperience = false, experienceRequired = 100)
        val granted = SkillTreeEngine.adjustGrantedPoints(level20, TalentState(), 100)
        assertEquals(44, granted.grantedPoints)
        val afterXp = level20.addExperience(1000, granted.grantedPoints)
        assertEquals(0, afterXp.prestigePoints)
        assertEquals(66, SkillTreeEngine.totalPoints(afterXp, granted))
        assertEquals(44, SkillTreeEngine.adjustGrantedPoints(afterXp, granted, 5).grantedPoints)
    }

    @Test fun oldUnlockedTalentsAreNotDeletedAfterBalanceChange() {
        val player = hero(level = 1)
        val nodes = SkillTreeEngine.nodes(player)
        val alreadyUnlocked = nodes.filter { it.id != "root" }.take(6).map { it.id }.toSet()
        val state = TalentState(alreadyUnlocked)
        assertTrue(SkillTreeEngine.spent(nodes, state) > SkillTreeEngine.totalPoints(player, state))
        assertEquals(0, SkillTreeEngine.remainingPoints(player, state, nodes))
        assertEquals(alreadyUnlocked, state.unlocked)
        assertFalse(SkillTreeEngine.canLearn(player, state,
            nodes.first { it.id !in alreadyUnlocked && it.id != "root" }, nodes))
    }

    @Test fun oversizeGrantsCannotMakeTheTreeBudgetGreaterThanItsCost() {
        val player = hero()
        val nodes = SkillTreeEngine.nodes(player)
        val afterMaster = SkillTreeEngine.adjustGrantedPoints(player, TalentState(), 1000)
        assertEquals(63, afterMaster.grantedPoints)
        assertEquals(SkillTreeEngine.spent(nodes, TalentState(nodes.map { it.id }.toSet())),
            SkillTreeEngine.totalPoints(player, afterMaster))
    }
}
