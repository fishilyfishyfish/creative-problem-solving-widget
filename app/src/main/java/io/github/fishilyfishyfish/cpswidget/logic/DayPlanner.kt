package io.github.fishilyfishyfish.cpswidget.logic

import java.time.LocalDate
import java.time.LocalDateTime

enum class DayMode { TODAY, NEXT }

/** 위젯이 보여줄 날짜. 하교 전이면 오늘, 하교 후·주말이면 다음 등교일. */
data class TargetDay(val date: LocalDate, val mode: DayMode)

sealed interface PeriodStatus {
    /** 첫 수업 전 */
    data class BeforeSchool(val nextPeriod: Int) : PeriodStatus

    /** 수업 중 */
    data class InClass(val period: Int) : PeriodStatus

    /** 쉬는 시간·점심시간·공강 */
    data class Break(val nextPeriod: Int, val isLunch: Boolean) : PeriodStatus

    /** 그날 마지막 수업이 끝남 */
    data object AfterClasses : PeriodStatus

    /** 시간표나 교시 시각이 비어 있음 */
    data object NoClasses : PeriodStatus
}

object DayPlanner {
    /** 교시 사이 간격이 이 이상이면 점심시간으로 본다. */
    const val LUNCH_GAP_MIN = 30

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
     * @param allTimes 교시 번호 → 시각 (과목 유무와 상관없이 설정된 전체)
     * @param classes 그날 과목이 입력된 교시 번호
     */
    fun periodStatus(nowMin: Int, allTimes: Map<Int, PeriodTime>, classes: Set<Int>): PeriodStatus {
        val ordered = allTimes.entries
            .filter { it.value.isValid }
            .sortedBy { it.value.startMin }
        val classTimes = ordered.filter { it.key in classes }
        if (classTimes.isEmpty()) return PeriodStatus.NoClasses

        classTimes.firstOrNull { nowMin >= it.value.startMin && nowMin < it.value.endMin }
            ?.let { return PeriodStatus.InClass(it.key) }

        val next = classTimes.firstOrNull { it.value.startMin > nowMin }
            ?: return PeriodStatus.AfterClasses

        if (nowMin < classTimes.first().value.startMin) {
            return PeriodStatus.BeforeSchool(next.key)
        }

        val isLunch = ordered.zipWithNext().any { (a, b) ->
            nowMin >= a.value.endMin && nowMin < b.value.startMin &&
                b.value.startMin - a.value.endMin >= LUNCH_GAP_MIN
        }
        return PeriodStatus.Break(next.key, isLunch)
    }

    /** 위젯을 다시 그려야 하는 다음 시각: 교시 시작·끝, 하교, 자정 중 가장 가까운 것 */
    fun nextBoundary(now: LocalDateTime, settings: Settings): LocalDateTime {
        val today = now.toLocalDate()
        val candidates = mutableListOf<Int>()
        settings.periodTimes.filterNotNull().filter { it.isValid }.forEach {
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
