package io.github.fishilyfishyfish.cpswidget.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetUiBuilderTest {

    private val friday = LocalDate.of(2026, 10, 30)
    private val monday = LocalDate.of(2026, 11, 2)

    private val settings = Settings(
        school = School("J10", "7530000", "테스트고등학교"),
        timetable = SCHOOL_DAYS.associateWith { List(MAX_PERIODS) { "" } } +
            mapOf(
                5 to listOf("국어", "수학", "", "체육", "영어", "", "", ""),
                1 to listOf("과학", "", "", "", "", "", "", ""),
            ),
        allergens = setOf(2),
        keywordsRaw = "오이, ",
    )

    private val menu = MealEntry.Menu("쌀밥<br/>돈까스(1.2.5.6.10)<br/>오이무침<br/>김치(9)")

    @Test
    fun todayDuringClass() {
        val ui = WidgetUiBuilder.build(friday.atTime(9, 0), settings, menu, null)
        assertEquals("10월 30일 (금) · 오늘", ui.title)
        assertEquals("1교시 수업 중 · ~09:30", ui.status)
        assertEquals(listOf(1, 2, 4, 5), ui.periods.map { it.period })
        assertEquals(Highlight.NOW, ui.periods[0].highlight)
        assertTrue(ui.periods.drop(1).all { it.highlight == Highlight.NONE })
    }

    @Test
    fun breakHighlightsNextClass() {
        val ui = WidgetUiBuilder.build(friday.atTime(10, 45), settings, menu, null)
        assertEquals("쉬는 시간 · 다음 4교시", ui.status)
        assertEquals(Highlight.NEXT, ui.periods.first { it.period == 4 }.highlight)
    }

    @Test
    fun afterDismissalSwitchesToNextSchoolDay() {
        val ui = WidgetUiBuilder.build(friday.atTime(17, 0), settings, null, null)
        assertEquals("11월 2일 (월) · 다음 등교일", ui.title)
        assertEquals("", ui.status)
        assertEquals(listOf("과학"), ui.periods.map { it.subject })
        assertEquals(MealUi.Loading, ui.meal)
    }

    @Test
    fun tomorrowLabel() {
        val ui = WidgetUiBuilder.build(monday.atTime(18, 0), settings, null, null)
        assertTrue(ui.title.endsWith("· 내일"))
    }

    @Test
    fun mealHighlightsAllergenAndKeyword() {
        val ui = WidgetUiBuilder.build(friday.atTime(9, 0), settings, menu, null)
        val meal = ui.meal as MealUi.Menu
        assertNull(meal.dishes[0].warning)
        assertEquals("우유", meal.dishes[1].warning)
        assertEquals("오이", meal.dishes[2].warning)
        assertNull(meal.dishes[3].warning)
    }

    @Test
    fun mealStates() {
        assertEquals(MealUi.NoSchool, WidgetUiBuilder.build(friday.atTime(9, 0), settings.copy(school = null), menu, null).meal)
        assertEquals(MealUi.NoMeal, WidgetUiBuilder.build(friday.atTime(9, 0), settings, MealEntry.None, null).meal)
        assertEquals(MealUi.Failed("오류"), WidgetUiBuilder.build(friday.atTime(9, 0), settings, null, "오류").meal)
    }
}
