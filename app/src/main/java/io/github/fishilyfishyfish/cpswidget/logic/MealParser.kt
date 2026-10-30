package io.github.fishilyfishyfish.cpswidget.logic

/** 학교급식 알레르기 유발 식품 19종 (나이스 메뉴의 괄호 숫자) */
object Allergens {
    val NAMES: Map<Int, String> = linkedMapOf(
        1 to "난류",
        2 to "우유",
        3 to "메밀",
        4 to "땅콩",
        5 to "대두",
        6 to "밀",
        7 to "고등어",
        8 to "게",
        9 to "새우",
        10 to "돼지고기",
        11 to "복숭아",
        12 to "토마토",
        13 to "아황산류",
        14 to "호두",
        15 to "닭고기",
        16 to "쇠고기",
        17 to "오징어",
        18 to "조개류",
        19 to "잣",
    )
}

data class Dish(val name: String, val allergens: Set<Int>)

object MealParser {
    private val BR = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

    /** "(5.6.13.16)" 처럼 괄호 안 숫자 */
    private val PAREN_NUMS = Regex("\\(\\s*(\\d[\\d.,\\s]*)\\)")

    /** 괄호 없이 끝에 붙은 "9.13." 형태 */
    private val TRAILING_NUMS = Regex("\\s*((?:\\d{1,2}\\.)+\\d{0,2})\\s*$")

    private val NON_DIGITS = Regex("[^0-9]+")

    /** 나이스 DDISH_NM 원문을 메뉴 목록으로 나눈다. */
    fun parse(raw: String): List<Dish> =
        raw.split(BR)
            .map { unescape(it).trim() }
            .filter { it.isNotEmpty() }
            .map(::parseDish)

    fun parseDish(text: String): Dish {
        val nums = mutableSetOf<Int>()
        PAREN_NUMS.findAll(text).forEach { nums += numbers(it.groupValues[1]) }
        var name = PAREN_NUMS.replace(text, "")
        if (nums.isEmpty()) {
            TRAILING_NUMS.find(name)?.let { m ->
                nums += numbers(m.groupValues[1])
                name = name.substring(0, m.range.first)
            }
        }
        name = name.trim()
        return Dish(name.ifEmpty { text.trim() }, nums)
    }

    /** 체크한 알레르기 번호가 하나라도 들어 있으면 true */
    fun isFlagged(dish: Dish, allergens: Set<Int>): Boolean = dish.allergens.any { it in allergens }

    private fun numbers(s: String): Set<Int> =
        s.split(NON_DIGITS)
            .mapNotNull { it.toIntOrNull() }
            .filter { it in 1..19 }
            .toSet()

    private fun unescape(s: String): String =
        s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
}
