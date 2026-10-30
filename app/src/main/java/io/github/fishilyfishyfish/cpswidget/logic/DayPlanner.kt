package io.github.fishilyfishyfish.cpswidget.logic

import java.time.LocalDate
import java.time.LocalDateTime

enum class DayMode { TODAY, NEXT }

/** 위젯이 보여줄 날짜. 하교 전이면 오늘, 하교 후·주말이면 다음 등교일. */
data class TargetDay(val date: LocalDate, val mode: DayMode)

/**
 * 지금 이 순간의 상태.
 * @param nowPeriod 수업 중인 교시 (빨강)
 * @param nowMeal 식사 중인 끼니 (급식 쪽 빨강)
 * @param nextPeriod 다음 수업 교시 (파랑)
 */
data class Moment(val nowPeriod: Int?, val nowMeal: MealType?, val nextPeriod: Int?)

object DayPlanner {

    fun isSchoolDay(date: LocalDate): Boolean = date.dayOfWeek.value in SCHOOL_DAYS

    fun nextSchoolDay(after: LocalDate): LocalDate {
        var d = after.plusDays(1)
        while (!isSchoolDay(d)) d = d.plusDays(1)
        return d
    }

    fun targetDay(now: LocalDateTime, settings: Settings): TargetDay {
        val today = now.toLocalDate()
        if (isSchoolDay(today)) {
            val nowMin = now.hour * 60 + now.minute
            if (nowMin < settings.dismissalOf(today.dayOfWeek.value)) {
                return TargetDay(today, DayMode.TODAY)
            }
        }
        return TargetDay(nextSchoolDay(today), DayMode.NEXT)
    }

    /**
     * @param classTimes 그날 과목이 있는 교시 → 시각
     * @param meals "없음"이 아닌 끼니 → 시각
     */
    fun moment(nowMin: Int, classTimes: Map<Int, PeriodTime>, meals: Map<MealType, PeriodTime>): Moment {
        val ordered = classTimes.entries.filter { it.value.isValid }.sortedBy { it.value.startMin }
        val nowPeriod = ordered.firstOrNull { nowMin in it.value }?.key
        val nextPeriod = ordered.firstOrNull { it.value.startMin > nowMin }?.key
        val nowMeal = meals.entries.firstOrNull { it.value.isValid && nowMin in it.value }?.key
        return Moment(nowPeriod, nowMeal, nextPeriod)
    }

    /**
     * 급식 칸에 보여줄 끼니.
     * 오늘: 아직 안 끝난 첫 끼니 (모두 끝났으면 마지막 끼니). 다음 등교일: 첫 끼니.
     */
    fun mealToShow(mode: DayMode, nowMin: Int, meals: Map<MealType, PeriodTime>): MealType? {
        val enabled = meals.entries.filter { it.value.isValid }.sortedBy { it.value.startMin }
        if (enabled.isEmpty()) return null
        if (mode == DayMode.NEXT) return enabled.first().key
        return (enabled.firstOrNull { nowMin < it.value.endMin } ?: enabled.last()).key
    }

    /** 위젯을 다시 그려야 하는 다음 시각: 교시·식사 시작과 끝, 하교, 자정 중 가장 가까운 것 */
    fun nextBoundary(now: LocalDateTime, settings: Settings): LocalDateTime {
        val today = now.toLocalDate()
        val candidates = mutableListOf<Int>()
        val times = settings.periodTimes.filterNotNull() + settings.enabledMeals().values
        times.filter { it.isValid }.forEach {
            candidates += it.startMin
            candidates += it.endMin
        }
        if (isSchoolDay(today)) candidates += settings.dismissalOf(today.dayOfWeek.value)

        val nowSec = now.toLocalTime().toSecondOfDay()
        val next = candidates.filter { it * 60 > nowSec && it < 24 * 60 }.minOrNull()
        return if (next != null) {
            today.atStartOfDay().plusMinutes(next.toLong())
        } else {
            today.plusDays(1).atStartOfDay()
        }
    }
}
