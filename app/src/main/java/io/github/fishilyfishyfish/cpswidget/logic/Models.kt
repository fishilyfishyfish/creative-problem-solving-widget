package io.github.fishilyfishyfish.cpswidget.logic

/** 하루 최대 교시 수 */
const val MAX_PERIODS = 9

/** 기본 하교 시각 (16:30) */
const val DEFAULT_DISMISSAL = 16 * 60 + 30

/** 월~금 (java.time.DayOfWeek 값) */
val SCHOOL_DAYS = 1..5

val DAY_NAMES = listOf("월", "화", "수", "목", "금", "토", "일")

/** 시작·끝 시각. 단위는 자정부터 흐른 분. */
data class PeriodTime(val startMin: Int, val endMin: Int) {
    val isValid: Boolean
        get() = startMin in 0 until 24 * 60 && endMin in 1..24 * 60 && endMin > startMin

    operator fun contains(minute: Int): Boolean = minute >= startMin && minute < endMin
}

val DEFAULT_PERIOD_TIMES: List<PeriodTime?> = listOf(
    PeriodTime(8 * 60 + 40, 9 * 60 + 30),
    PeriodTime(9 * 60 + 40, 10 * 60 + 30),
    PeriodTime(10 * 60 + 40, 11 * 60 + 30),
    PeriodTime(11 * 60 + 40, 12 * 60 + 30),
    PeriodTime(13 * 60 + 30, 14 * 60 + 20),
    PeriodTime(14 * 60 + 30, 15 * 60 + 20),
    PeriodTime(15 * 60 + 30, 16 * 60 + 20),
    null,
    null,
)

/** 나이스 급식 종류. code는 MMEAL_SC_CODE 값. */
enum class MealType(val code: Int, val label: String) {
    BREAKFAST(1, "조식"),
    LUNCH(2, "중식"),
    DINNER(3, "석식");

    companion object {
        fun ofCode(code: Int): MealType? = entries.firstOrNull { it.code == code }
    }
}

/** enabled=false가 앱 화면의 "없음" */
data class MealSetting(val enabled: Boolean, val time: PeriodTime)

val DEFAULT_MEALS: Map<MealType, MealSetting> = mapOf(
    MealType.BREAKFAST to MealSetting(false, PeriodTime(7 * 60 + 30, 8 * 60 + 10)),
    MealType.LUNCH to MealSetting(true, PeriodTime(12 * 60 + 30, 13 * 60 + 30)),
    MealType.DINNER to MealSetting(true, PeriodTime(17 * 60 + 30, 18 * 60 + 30)),
)

data class School(
    val officeCode: String,
    val schoolCode: String,
    val name: String,
    val officeName: String = "",
    val address: String = "",
)

data class Settings(
    val school: School? = null,
    /** 교육청(나이스) 인증키 */
    val apiKey: String = "",
    /** 요일(1=월 … 5=금) → 교시별 과목. 빈 문자열은 수업 없음. */
    val subjects: Map<Int, List<String>> = emptyMap(),
    /** 요일 → 교시별 교실 */
    val rooms: Map<Int, List<String>> = emptyMap(),
    /** 교시별 시각 (모든 요일 공통). null은 사용 안 함. */
    val periodTimes: List<PeriodTime?> = DEFAULT_PERIOD_TIMES,
    /** 요일 → 하교 시각(분) */
    val dismissal: Map<Int, Int> = SCHOOL_DAYS.associateWith { DEFAULT_DISMISSAL },
    val meals: Map<MealType, MealSetting> = DEFAULT_MEALS,
    /** 체크한 알레르기 번호 (1~19) */
    val allergens: Set<Int> = emptySet(),
) {
    fun subjectsOf(day: Int): List<String> = List(MAX_PERIODS) { i -> subjects[day]?.getOrNull(i) ?: "" }

    fun roomsOf(day: Int): List<String> = List(MAX_PERIODS) { i -> rooms[day]?.getOrNull(i) ?: "" }

    fun timeOf(period: Int): PeriodTime? = periodTimes.getOrNull(period - 1)

    fun dismissalOf(day: Int): Int = dismissal[day] ?: DEFAULT_DISMISSAL

    fun mealOf(type: MealType): MealSetting = meals[type] ?: DEFAULT_MEALS.getValue(type)

    /** "없음"이 아니고 시각이 올바른 끼니만 */
    fun enabledMeals(): Map<MealType, PeriodTime> =
        MealType.entries
            .map { it to mealOf(it) }
            .filter { (_, m) -> m.enabled && m.time.isValid }
            .associate { (t, m) -> t to m.time }

    fun withCell(day: Int, period: Int, subject: String, room: String): Settings {
        val s = subjectsOf(day).toMutableList().also { it[period - 1] = subject }
        val r = roomsOf(day).toMutableList().also { it[period - 1] = room }
        return copy(subjects = subjects + (day to s), rooms = rooms + (day to r))
    }

    fun withPeriodTime(period: Int, time: PeriodTime?): Settings {
        val list = MutableList(MAX_PERIODS) { i -> periodTimes.getOrNull(i) }
        list[period - 1] = time
        return copy(periodTimes = list)
    }

    fun withMeal(type: MealType, setting: MealSetting): Settings = copy(meals = meals + (type to setting))
}

/** 캐시에 저장된 끼니 하나 */
sealed interface MealEntry {
    data class Menu(val raw: String) : MealEntry
    data object None : MealEntry
}

fun formatMinutes(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
