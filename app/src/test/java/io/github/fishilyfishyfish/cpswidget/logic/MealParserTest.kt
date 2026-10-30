package io.github.fishilyfishyfish.cpswidget.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealParserTest {

    private val raw = "기장밥<br/>쇠고기미역국 (5.6.13.16)<br/>돈까스&amp;소스(1.2.5.6.10.12.13)<br/>깍두기 9.13.<br/>우유(2)"

    @Test
    fun splitsDishesAndAllergens() {
        val dishes = MealParser.parse(raw)
        assertEquals(listOf("기장밥", "쇠고기미역국", "돈까스&소스", "깍두기", "우유"), dishes.map { it.name })
        assertEquals(emptySet<Int>(), dishes[0].allergens)
        assertEquals(setOf(5, 6, 13, 16), dishes[1].allergens)
        assertEquals(setOf(1, 2, 5, 6, 10, 12, 13), dishes[2].allergens)
        assertEquals(setOf(9, 13), dishes[3].allergens)
        assertEquals(setOf(2), dishes[4].allergens)
    }

    @Test
    fun handlesBrVariants() {
        val dishes = MealParser.parse("밥<br>국(5)<BR />김치")
        assertEquals(listOf("밥", "국", "김치"), dishes.map { it.name })
    }

    @Test
    fun flagsByAllergenNumber() {
        val dishes = MealParser.parse(raw)
        val flagged = dishes.filter { MealParser.match(it, setOf(2), emptyList()).flagged }.map { it.name }
        assertEquals(listOf("돈까스&소스", "우유"), flagged)
    }

    @Test
    fun flagsByKeyword() {
        val dish = MealParser.parseDish("오이무침 (5.13)")
        val m = MealParser.match(dish, emptySet(), listOf("오이"))
        assertTrue(m.flagged)
        assertEquals("오이", m.label())
    }

    @Test
    fun labelListsAllergenNamesInOrder() {
        val dish = MealParser.parseDish("돈까스(1.2.5.6.10)")
        val m = MealParser.match(dish, setOf(10, 2), emptyList())
        assertEquals("우유·돼지고기", m.label())
    }

    @Test
    fun ignoresNumbersOutsideRange() {
        val dish = MealParser.parseDish("특식(0.20.2)")
        assertEquals(setOf(2), dish.allergens)
        assertFalse(MealParser.match(dish, setOf(1), emptyList()).flagged)
    }
}
