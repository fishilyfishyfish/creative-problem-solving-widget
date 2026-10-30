package io.github.fishilyfishyfish.cpswidget.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DayPlannerTest {

    // 2026-10-30은 금요일
    private val friday = LocalDate.of(2026, 10, 30)

    private val settings = Settings(
        dismissal = mapOf(1 to 16 * 60 + 30, 2 to 16 * 60 + 30, 3 to 15 * 60 + 30, 4 to 16 * 60 + 30, 5 to 16 * 60 + 30),
    )

    private fun at(date: LocalDate, h: Int, m: Int) = date.atTime(h, m)
    private fun min(h: Int, m: Int) = h * 60 + m

    @Test
    fun beforeDismissalShowsToday() {
        assertEquals(TargetDay(friday, DayMode.TODAY), DayPlanner.targetDay(at(friday, 10, 0), settings))
    }

    @Test
    fun afterFridayDismissalShowsMonday() {
        val monday = LocalDate.of(2026, 11, 2)
        assertEquals(TargetDay(monday, DayMode.NEXT), DayPlanner.targetDay(at(friday, 16, 30), settings))
        assertEquals(TargetDay(monday, DayMode.NEXT), DayPlanner.targetDay(at(friday.plusDays(1), 12, 0), settings))
    }

    @Test
    fun dismissalIsPerWeekday() {
        val wednesday = LocalDate.of(2026, 10, 28)
        // 수요일만 15:30 하교
        assertEquals(DayMode.TODAY, DayPlanner.targetDay(at(wednesday, 15, 29), settings).mode)
        assertEquals(TargetDay(wednesday.plusDays(1), DayMode.NEXT), DayPlanner.targetDay(at(wednesday, 15, 30), settings))
    }

    private val classTimes = mapOf(
        1 to PeriodTime(min(8, 40), min(9, 30)),
        2 to PeriodTime(min(9, 40), min(10, 30)),
        4 to PeriodTime(min(11, 40), min(12, 30)),
        5 to PeriodTime(min(13, 30), min(14, 20)),
    )
    private val meals = mapOf(
        MealType.LUNCH to PeriodTime(min(12, 30), min(13, 30)),
        MealType.DINNER to PeriodTime(min(17, 30), min(18, 30)),
    )

    @Test
    fun duringClassShowsNowAndNext() {
        assertEquals(Moment(1, null, 2), DayPlanner.moment(min(9, 0), classTimes, meals))
        assertEquals(Moment(4, null, 5), DayPlanner.moment(min(12, 0), classTimes, meals))
    }

    @Test
    fun breakHasOnlyNext() {
        assertEquals(Moment(null, null, 2), DayPlanner.moment(min(9, 30), classTimes, meals))
        assertEquals(Moment(null, null, 1), DayPlanner.moment(min(8, 0), classTimes, meals))
        // 3교시가 비어 있으면 그 시간엔 다음이 4교시
        assertEquals(Moment(null, null, 4), DayPlanner.moment(min(11, 0), classTimes, meals))
    }

    @Test
    fun mealTimeIsNow() {
        assertEquals(Moment(null, MealType.LUNCH, 5), DayPlanner.moment(min(12, 45), classTimes, meals))
        assertEquals(Moment(null, null, null), DayPlanner.moment(min(15, 0), classTimes, meals))
    }

    @Test
    fun mealToShowFollowsTime() {
        val withBreakfast = meals + (MealType.BREAKFAST to PeriodTime(min(7, 30), min(8, 10)))
        assertEquals(MealType.BREAKFAST, DayPlanner.mealToShow(DayMode.TODAY, min(7, 0), withBreakfast))
        assertEquals(MealType.LUNCH, DayPlanner.mealToShow(DayMode.TODAY, min(8, 10), withBreakfast))
        assertEquals(MealType.LUNCH, DayPlanner.mealToShow(DayMode.TODAY, min(13, 29), meals))
        assertEquals(MealType.DINNER, DayPlanner.mealToShow(DayMode.TODAY, min(13, 30), meals))
        assertEquals(MealType.DINNER, DayPlanner.mealToShow(DayMode.TODAY, min(20, 0), meals))
        assertEquals(MealType.LUNCH, DayPlanner.mealToShow(DayMode.NEXT, min(20, 0), meals))
        assertNull(DayPlanner.mealToShow(DayMode.TODAY, min(9, 0), emptyMap()))
    }

    @Test
    fun nextBoundaryPicksClosestChange() {
        // 기본 교시 시각 + 기본 중식(12:30~13:30)·석식(17:30~18:30)
        assertEquals(at(friday, 9, 30), DayPlanner.nextBoundary(at(friday, 9, 0), settings))
        assertEquals(at(friday, 9, 40), DayPlanner.nextBoundary(LocalDateTime.of(2026, 10, 30, 9, 30, 2), settings))
        assertEquals(at(friday, 16, 30), DayPlanner.nextBoundary(at(friday, 16, 25), settings))
        assertEquals(at(friday, 17, 30), DayPlanner.nextBoundary(at(friday, 16, 30), settings))
        assertEquals(friday.plusDays(1).atStartOfDay(), DayPlanner.nextBoundary(at(friday, 23, 0), settings))
    }
}
