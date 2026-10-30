package io.github.fishilyfishyfish.cpswidget.logic

import java.time.LocalDate
import java.time.LocalDateTime

/** NOW = 빨강(지금), NEXT = 파랑(다음) */
enum class Highlight { NONE, NEXT, NOW }

data class PeriodRow(
    val period: Int,
    val subject: String,
    val room: String,
    val highlight: Highlight,
)

/** flagged = 노랑(알레르기) */
data class DishRow(val name: String, val flagged: Boolean)

sealed interface MealUi {
    data object NoSchool : MealUi
    data object AllOff : MealUi
    data object Loading : MealUi
    data class Failed(val message: String) : MealUi
    data object NoMeal : MealUi
    data class Menu(val dishes: List<DishRow>) : MealUi
}

data class WidgetUi(
    /** 위젯 왼쪽 위 요일 ("화") */
    val dayLabel: String,
    val periods: List<PeriodRow>,
    /** 급식 칸에 보이는 끼니 ("중식"). 끼니가 모두 "없음"이면 빈 문자열 */
    val mealLabel: String,
    /** 지금 식사 시간이면 true → 급식 쪽 빨강 */
    val mealNow: Boolean,
    val meal: MealUi,
)

/** 설정·급식 캐시·현재 시각으로 위젯에 그릴 내용을 만든다. 안드로이드 의존성 없음. */
object WidgetUiBuilder {

    fun build(
        now: LocalDateTime,
        settings: Settings,
        mealLookup: (LocalDate, MealType) -> MealEntry?,
        mealError: String?,
    ): WidgetUi {
        val target = DayPlanner.targetDay(now, settings)
        val day = target.date.dayOfWeek.value
        val nowMin = now.hour * 60 + now.minute
        val subjects = settings.subjectsOf(day)
        val rooms = settings.roomsOf(day)

        val classes = (1..MAX_PERIODS).filter { subjects[it - 1].isNotBlank() }
        val classTimes = classes
            .mapNotNull { p -> settings.timeOf(p)?.takeIf { it.isValid }?.let { p to it } }
            .toMap()
        val meals = settings.enabledMeals()

        val moment = if (target.mode == DayMode.TODAY) DayPlanner.moment(nowMin, classTimes, meals) else null

        val rows = classes.map { p ->
            PeriodRow(
                period = p,
                subject = subjects[p - 1].trim(),
                room = rooms[p - 1].trim(),
                highlight = when (p) {
                    moment?.nowPeriod -> Highlight.NOW
                    moment?.nextPeriod -> Highlight.NEXT
                    else -> Highlight.NONE
                },
            )
        }

        val shownMeal = DayPlanner.mealToShow(target.mode, nowMin, meals)
        val mealUi = when {
            settings.school == null -> MealUi.NoSchool
            shownMeal == null -> MealUi.AllOff
            else -> when (val entry = mealLookup(target.date, shownMeal)) {
                null -> if (mealError != null) MealUi.Failed(mealError) else MealUi.Loading
                MealEntry.None -> MealUi.NoMeal
                is MealEntry.Menu -> {
                    val dishes = MealParser.parse(entry.raw).map { DishRow(it.name, MealParser.isFlagged(it, settings.allergens)) }
                    if (dishes.isEmpty()) MealUi.NoMeal else MealUi.Menu(dishes)
                }
            }
        }

        return WidgetUi(
            dayLabel = DAY_NAMES[day - 1],
            periods = rows,
            mealLabel = shownMeal?.label ?: "",
            mealNow = shownMeal != null && moment?.nowMeal == shownMeal,
            meal = mealUi,
        )
    }
}
