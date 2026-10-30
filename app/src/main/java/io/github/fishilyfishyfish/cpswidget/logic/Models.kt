package io.github.fishilyfishyfish.cpswidget.logic

/** 하루 최대 교시 수 */
const val MAX_PERIODS = 8

/** 기본 하교 시각 (16:30) */
const val DEFAULT_DISMISSAL = 16 * 60 + 30

/** 월~금 (java.time.DayOfWeek 값) */
val SCHOOL_DAYS = 1..5

val DAY_NAMES = listOf("월", "화", "수", "목", "금", "토", "일")

/** 교시 시작·끝 시각. 단위는 자정부터 흐른 분. */
data class PeriodTime(val startMin: Int, val endMin: Int) {
    val isValid: Boolean
        get() = startMin in 0 until 24 * 60 && endMin in 1..24 * 60 && endMin > startMin
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
    /** 앱에서 직접 입력한 나이스 인증키. 비어 있으면 빌드에 포함된 키를 쓴다. */
    val apiKey: String = "",
    /** 요일(1=월 … 5=금) → 교시별 과목. 빈 문자열은 수업 없음. */
    val timetable: Map<Int, List<String>> = SCHOOL_DAYS.associateWith { List(MAX_PERIODS) { "" } },
    /** 교시별 시각. null은 사용 안 함. */
    val periodTimes: List<PeriodTime?> = DEFAULT_PERIOD_TIMES,
    /** 요일 → 하교 시각(분) */
    val dismissal: Map<Int, Int> = SCHOOL_DAYS.associateWith { DEFAULT_DISMISSAL },
    /** 체크한 알레르기 번호 (1~19) */
    val allergens: Set<Int> = emptySet(),
    /** 직접 추가한 재료. 쉼표로 구분된 원문 그대로 저장한다. */
    val keywordsRaw: String = "",
) {
    val keywords: List<String>
        get() = keywordsRaw.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() }

    fun subjectsOf(day: Int): List<String> =
        List(MAX_PERIODS) { i -> timetable[day]?.getOrNull(i) ?: "" }

    fun timeOf(period: Int): PeriodTime? = periodTimes.getOrNull(period - 1)

    fun dismissalOf(day: Int): Int = dismissal[day] ?: DEFAULT_DISMISSAL
}

/** 캐시에 저장된 하루치 점심 */
sealed interface MealEntry {
    data class Menu(val raw: String) : MealEntry
    data object None : MealEntry
}

fun formatMinutes(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
