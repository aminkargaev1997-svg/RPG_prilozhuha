package com.example.rpgcreators

import org.junit.Assert.*
import org.junit.Test

class CombatEngineTest {
    private fun prepared(): CombatState {
        var s = CombatEngine.setup()
        s = CombatEngine.add(s, CombatFighter("amin", "Амин", "player", initiativeBonus = 3))
        s = CombatEngine.add(s, CombatFighter("goblin", "Гоблин", "marker"))
        s = CombatEngine.add(s, CombatFighter("ren", "Ренхардс", "player", initiativeBonus = 1))
        return s
    }

    @Test fun noAutomaticDiceRollsAndAllRollsMustBeGiven() {
        var s = prepared()
        assertFalse(s.ready)
        assertEquals(s, CombatEngine.start(s))
        assertEquals(s, CombatEngine.initiative(s, "amin", 0))
        assertEquals(s, CombatEngine.initiative(s, "amin", 21))
        s = CombatEngine.initiative(s, "amin", 14)
        s = CombatEngine.initiative(s, "goblin", 17)
        s = CombatEngine.initiative(s, "ren", 16)
        assertTrue(s.ready)
        assertEquals(listOf("amin", "ren", "goblin"), CombatEngine.start(s).fighters.map { it.id })
    }

    @Test fun twoActionsMaximumAndOnlyCurrentFighterMayAct() {
        var s = prepared().let { CombatEngine.initiative(it, "amin", 14) }
            .let { CombatEngine.initiative(it, "goblin", 17) }
            .let { CombatEngine.initiative(it, "ren", 16) }
            .let { CombatEngine.start(it) }
        assertEquals(s, CombatEngine.action(s, "goblin", "Атака"))
        s = CombatEngine.action(s, "amin", "Перемещение")
        assertTrue(s.current!!.movementAllowed)
        s = CombatEngine.action(s, "amin", "Атака")
        assertEquals(2, s.current!!.actionsSpent)
        assertEquals(s, CombatEngine.action(s, "amin", "Атака"))
        s = CombatEngine.refund(s)
        assertEquals(1, s.current!!.actionsSpent)
        assertTrue("Undoing an attack must not undo an earlier move", s.current!!.movementAllowed)
        s = CombatEngine.refund(s)
        assertFalse(s.current!!.movementAllowed)
    }

    @Test fun turnsResetActionsAndRoundAdvancesAtQueueWrap() {
        var s = prepared().let { CombatEngine.initiative(it, "amin", 10) }
            .let { CombatEngine.initiative(it, "goblin", 9) }
            .let { CombatEngine.initiative(it, "ren", 8) }
            .let { CombatEngine.start(it) }
        s = CombatEngine.action(s, "amin", "Атака")
        s = CombatEngine.next(s)
        assertEquals("goblin", s.currentId)
        s = CombatEngine.next(s)
        s = CombatEngine.next(s)
        assertEquals(2, s.round)
        assertEquals("amin", s.currentId)
        assertEquals(0, s.current!!.actionsSpent)
        s = CombatEngine.finish(s)
        assertEquals("idle", s.phase)
        assertTrue(s.fighters.isEmpty())
    }
}
