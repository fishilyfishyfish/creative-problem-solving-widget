package io.github.fishilyfishyfish.cpswidget.logic

import org.junit.Assert.assertEquals
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
        // 수요일은 15:30 하교
        assertEquals(DayMode.TODAY, DayPlanner.targetDay(at(wednesday, 15, 29), settings).mode)
        assertEquals(TargetDay(wednesday.plusDays(1), DayMode.NEXT), DayPlanner.targetDay(at(wednesday, 15, 30), settings))
    }

    private val times = mapOf(
        1 to PeriodTime(min(8, 40), min(9, 30)),
        2 to PeriodTime(min(9, 40), min(10, 30)),
        3 to PeriodTime(min(10, 40), min(11, 30)),
        4 to PeriodTime(min(11, 40), min(12, 30)),
        5 to PeriodTime(min(13, 30), min(14, 20)),
        6 to PeriodTime(min(14, 30), min(15, 20)),
    )

    @Test
    fun periodStatusAcrossTheDay() {
        val classes = setOf(1, 2, 3, 4, 5, 6)
        assertEquals(PeriodStatus.BeforeSchool(1), DayPlanner.periodStatus(min(8, 0), times, classes))
        assertEquals(PeriodStatus.InClass(1), DayPlanner.periodStatus(min(8, 40), times, classes))
        assertEquals(PeriodStatus.Break(2, false), DayPlanner.periodStatus(min(9, 30), times, classes))
        assertEquals(PeriodStatus.Break(5, true), DayPlanner.periodStatus(min(12, 45), times, classes))
        assertEquals(PeriodStatus.InClass(6), DayPlanner.periodStatus(min(15, 19), times, classes))
        assertEquals(PeriodStatus.AfterClasses, DayPlanner.periodStatus(min(15, 20), times, classes))
    }

    @Test
    fun emptyPeriodIsNotLunch() {
        // 3교시가 비어 있으면 2교시 뒤는 공강이지 점심이 아님
        val classes = setOf(1, 2, 4, 5)
        assertEquals(PeriodStatus.Break(4, false), DayPlanner.periodStatus(min(11, 0), times, classes))
    }

    @Test
    fun noClassesWhenTimetableEmpty() {
        assertEquals(PeriodStatus.NoClasses, DayPlanner.periodStatus(min(9, 0), times, emptySet()))
    }

    @Test
    fun nextBoundaryPicksClosestChange() {
        assertEquals(at(friday, 9, 30), DayPlanner.nextBoundary(at(friday, 9, 0), settings))
        assertEquals(at(friday, 9, 40), DayPlanner.nextBoundary(LocalDateTime.of(2026, 10, 30, 9, 30, 2), settings))
        assertEquals(at(friday, 16, 30), DayPlanner.nextBoundary(at(friday, 16, 25), settings))
        assertEquals(friday.plusDays(1).atStartOfDay(), DayPlanner.nextBoundary(at(friday, 23, 0), settings))
    }
}
