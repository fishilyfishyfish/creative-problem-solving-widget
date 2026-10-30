package io.github.fishilyfishyfish.cpswidget.logic

import java.time.LocalDateTime

enum class Highlight { NONE, NEXT, NOW }

data class PeriodRow(
    val period: Int,
    val subject: String,
    val time: String,
    val highlight: Highlight,
)

data class DishRow(val name: String, val warning: String?)

sealed interface MealUi {
    data object NoSchool : MealUi
    data object Loading : MealUi
    data class Failed(val message: String) : MealUi
    data object NoMeal : MealUi
    data class Menu(val dishes: List<DishRow>) : MealUi
}

data class WidgetUi(
    val title: String,
    val status: String,
    val periods: List<PeriodRow>,
    val meal: MealUi,
)

/** 설정·급식 캐시·현재 시각으로 위젯에 그릴 내용을 만든다. 안드로이드 의존성 없음. */
object WidgetUiBuilder {

    fun build(now: LocalDateTime, settings: Settings, meal: MealEntry?, mealError: String?): WidgetUi {
        val target = DayPlanner.targetDay(now, settings)
        val date = target.date
        val day = date.dayOfWeek.value
        val subjects = settings.subjectsOf(day)

        val classes = (1..MAX_PERIODS).filter { subjects[it - 1].isNotBlank() }
        val allTimes = (1..MAX_PERIODS)
            .mapNotNull { p -> settings.timeOf(p)?.takeIf { it.isValid }?.let { p to it } }
            .toMap()

        val status: PeriodStatus? = if (target.mode == DayMode.TODAY) {
            DayPlanner.periodStatus(now.hour * 60 + now.minute, allTimes, classes.toSet())
        } else {
            null
        }

        val rows = classes.map { p ->
            PeriodRow(
                period = p,
                subject = subjects[p - 1].trim(),
                time = allTimes[p]?.let { formatMinutes(it.startMin) } ?: "",
                highlight = highlightOf(status, p),
            )
        }

        val label = when {
            target.mode == DayMode.TODAY -> "오늘"
            date == now.toLocalDate().plusDays(1) -> "내일"
            else -> "다음 등교일"
        }
        val title = "${date.monthValue}월 ${date.dayOfMonth}일 (${DAY_NAMES[day - 1]}) · $label"

        return WidgetUi(
            title = title,
            status = statusText(status, allTimes),
            periods = rows,
            meal = mealUi(settings, meal, mealError),
        )
    }

    private fun highlightOf(status: PeriodStatus?, period: Int): Highlight = when (status) {
        is PeriodStatus.InClass -> if (status.period == period) Highlight.NOW else Highlight.NONE
        is PeriodStatus.Break -> if (status.nextPeriod == period) Highlight.NEXT else Highlight.NONE
        is PeriodStatus.BeforeSchool -> if (status.nextPeriod == period) Highlight.NEXT else Highlight.NONE
        else -> Highlight.NONE
    }

    private fun statusText(status: PeriodStatus?, times: Map<Int, PeriodTime>): String = when (status) {
        null -> ""
        is PeriodStatus.InClass ->
            "${status.period}교시 수업 중" + (times[status.period]?.let { " · ~${formatMinutes(it.endMin)}" } ?: "")
        is PeriodStatus.Break ->
            (if (status.isLunch) "점심시간" else "쉬는 시간") + " · 다음 ${status.nextPeriod}교시"
        is PeriodStatus.BeforeSchool ->
            "등교 전 · ${status.nextPeriod}교시" + (times[status.nextPeriod]?.let { " ${formatMinutes(it.startMin)}" } ?: "")
        PeriodStatus.AfterClasses -> "수업 끝"
        PeriodStatus.NoClasses -> ""
    }

    private fun mealUi(settings: Settings, meal: MealEntry?, mealError: String?): MealUi {
        if (settings.school == null) return MealUi.NoSchool
        return when (meal) {
            null -> if (mealError != null) MealUi.Failed(mealError) else MealUi.Loading
            MealEntry.None -> MealUi.NoMeal
            is MealEntry.Menu -> {
                val keywords = settings.keywords
                val dishes = MealParser.parse(meal.raw).map { dish ->
                    val m = MealParser.match(dish, settings.allergens, keywords)
                    DishRow(dish.name, if (m.flagged) m.label() else null)
                }
                if (dishes.isEmpty()) MealUi.NoMeal else MealUi.Menu(dishes)
            }
        }
    }
}
