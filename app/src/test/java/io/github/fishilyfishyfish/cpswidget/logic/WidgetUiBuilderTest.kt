package io.github.fishilyfishyfish.cpswidget.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetUiBuilderTest {

    // 2026-10-30은 금요일
    private val friday = LocalDate.of(2026, 10, 30)

    private val settings = Settings(school = School("J10", "7530000", "테스트고등학교"), allergens = setOf(2))
        .withCell(5, 1, "국어", "2-3")
        .withCell(5, 2, "수학", "2-3")
        .withCell(5, 4, "체육", "체육관")
        .withCell(5, 5, "영어", "")
        .withCell(1, 1, "과학", "과학실")

    private val lunch = MealEntry.Menu("쌀밥<br/>돈까스(1.2.5.6.10)<br/>김치(9)")
    private val dinner = MealEntry.Menu("카레라이스(2.5.6)<br/>단무지")

    private fun lookup(date: LocalDate, type: MealType): MealEntry? = when (type) {
        MealType.LUNCH -> lunch
        MealType.DINNER -> dinner
        MealType.BREAKFAST -> null
    }

    @Test
    fun duringClassNowIsRedNextIsBlue() {
        val ui = WidgetUiBuilder.build(friday.atTime(9, 0), settings, ::lookup, null)
        assertEquals("금", ui.dayLabel)
        assertEquals(listOf(1, 2, 4, 5), ui.periods.map { it.period })
        assertEquals("2-3", ui.periods[0].room)
        assertEquals(
            listOf(Highlight.NOW, Highlight.NEXT, Highlight.NONE, Highlight.NONE),
            ui.periods.map { it.highlight },
        )
    }

    @Test
    fun breakOnlyBlue() {
        val ui = WidgetUiBuilder.build(friday.atTime(10, 45), settings, ::lookup, null)
        assertEquals(
            listOf(Highlight.NONE, Highlight.NONE, Highlight.NEXT, Highlight.NONE),
            ui.periods.map { it.highlight },
        )
    }

    @Test
    fun lunchTimeMarksMealAsNow() {
        val ui = WidgetUiBuilder.build(friday.atTime(12, 45), settings, ::lookup, null)
        assertEquals("중식", ui.mealLabel)
        assertTrue(ui.mealNow)
        assertEquals(Highlight.NEXT, ui.periods.first { it.period == 5 }.highlight)
    }

    @Test
    fun afterLunchShowsDinner() {
        val ui = WidgetUiBuilder.build(friday.atTime(14, 0), settings, ::lookup, null)
        assertEquals("석식", ui.mealLabel)
        assertFalse(ui.mealNow)
        val meal = ui.meal as MealUi.Menu
        assertEquals(listOf("카레라이스", "단무지"), meal.dishes.map { it.name })
        assertEquals(listOf(true, false), meal.dishes.map { it.flagged })
    }

    @Test
    fun afterDismissalSwitchesToNextSchoolDay() {
        val ui = WidgetUiBuilder.build(friday.atTime(17, 0), settings, ::lookup, null)
        assertEquals("월", ui.dayLabel)
        assertEquals(listOf("과학"), ui.periods.map { it.subject })
        assertTrue(ui.periods.all { it.highlight == Highlight.NONE })
        assertEquals("중식", ui.mealLabel)
    }

    @Test
    fun allergyIsFlagged() {
        val ui = WidgetUiBuilder.build(friday.atTime(9, 0), settings, ::lookup, null)
        val meal = ui.meal as MealUi.Menu
        assertEquals(listOf(false, true, false), meal.dishes.map { it.flagged })
    }

    @Test
    fun mealStates() {
        val t = friday.atTime(9, 0)
        assertEquals(MealUi.NoSchool, WidgetUiBuilder.build(t, settings.copy(school = null), ::lookup, null).meal)
        assertEquals(MealUi.NoMeal, WidgetUiBuilder.build(t, settings, { _, _ -> MealEntry.None }, null).meal)
        assertEquals(MealUi.Loading, WidgetUiBuilder.build(t, settings, { _, _ -> null }, null).meal)
        assertEquals(MealUi.Failed("오류"), WidgetUiBuilder.build(t, settings, { _, _ -> null }, "오류").meal)

        val allOff = MealType.entries.fold(settings) { s, type -> s.withMeal(type, s.mealOf(type).copy(enabled = false)) }
        val ui = WidgetUiBuilder.build(t, allOff, ::lookup, null)
        assertEquals(MealUi.AllOff, ui.meal)
        assertEquals("", ui.mealLabel)
    }
}
