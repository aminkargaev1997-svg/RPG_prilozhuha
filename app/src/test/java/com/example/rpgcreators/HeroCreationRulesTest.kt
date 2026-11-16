package com.example.rpgcreators

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HeroCreationRulesTest {
    @Test fun everyRaceHasFiveDistinctBalancedOptions() {
        for (race in RpgCatalog.races.map { it.name } + "Пепельный демон") {
            val choices = HeroCreationCatalog.racialPowers(race)
            assertEquals("$race", 5, choices.size)
            assertEquals("$race", 5, choices.map { it.name }.distinct().size)
            assertTrue(choices.all { it.rules.isNotBlank() })
        }
    }
    @Test fun everyClassHasSixStartingOptions() {
        for (profession in RpgCatalog.professions.map { it.name }) {
            val choices = HeroCreationCatalog.classPowers(profession)
            assertEquals("$profession", 6, choices.size)
            assertEquals("$profession", 6, choices.map { it.name }.distinct().size)
        }
    }
    @Test fun baseAttributesHaveNoRacialPowerCreep() {
        for (profession in RpgCatalog.professions.map { it.name }) {
            for (build in HeroCreationCatalog.builds) {
                assertEquals(listOf(15, 14, 13, 12, 10, 8),
                    HeroCreationCatalog.attributesFor(profession, build).values.sortedDescending())
            }
        }
    }
    @Test fun storyReweavesOnlySelectedFragment() {
        val start = HeroCreationCatalog.weave("Мрачная", random = Random(11))
        val changed = HeroCreationCatalog.weave("Мрачная", start, "secret", Random(17))
        assertEquals(start.origin, changed.origin)
        assertEquals(start.moment, changed.moment)
        assertNotEquals(start.secret, changed.secret)
    }
    @Test fun starterPacksOnlyUseUsefulKnownSupplies() {
        val supplies = HeroCreationCatalog.supplies
        val names = supplies.map { it.name }
        assertEquals(names.size, names.distinct().size)
        assertFalse("Рюкзак" in names)
        assertTrue(supplies.all { it.description.isNotBlank() && it.quantity > 0 })
        for ((title, entries) in HeroCreationCatalog.packs) {
            assertTrue("$title exceeds the limit", entries.size <= HeroCreationCatalog.maxStartingItems)
            assertTrue("$title contains an unknown supply", entries.all { it in names })
        }
        assertTrue(HeroCreationCatalog.packs["Походный"]!!.isNotEmpty())
        assertTrue(HeroCreationCatalog.packs["Свой набор"]!!.isEmpty())
    }
    @Test fun startingWeaponsHaveExistingDamageTemplates() {
        for (name in listOf("Кинжал", "Посох", "Короткий меч", "Короткий лук")) {
            assertTrue(name in HeroCreationCatalog.supplies.map { it.name })
            assertTrue("$name has no weapon template", ItemEffectEngine.template(name).any { it.kind == "weapon" })
        }
        assertEquals(10, HeroCreationCatalog.supplies.first { it.name == "Стрелы" }.quantity)
    }
    @Test fun newTreeDoesNotOverwriteLegacyTree() {
        val oldHero = Character(name = "old", race = "Халфлинг", profession = "Следопыт")
        val newHero = oldHero.copy(buildVersion = 2)
        assertEquals(161, SkillTreeEngine.nodes(oldHero).size)
        assertEquals(25, SkillTreeEngine.nodes(newHero).size)
    }
}
