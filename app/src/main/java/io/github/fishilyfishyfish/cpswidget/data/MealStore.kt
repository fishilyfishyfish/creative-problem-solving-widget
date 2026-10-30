package io.github.fishilyfishyfish.cpswidget.data

import android.content.Context
import androidx.core.content.edit
import io.github.fishilyfishyfish.cpswidget.logic.MealEntry
import io.github.fishilyfishyfish.cpswidget.logic.MealType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 날짜·끼니별 급식 캐시. 위젯은 네트워크 없이 이 값만 읽는다. */
class MealStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("meals_v2", Context.MODE_PRIVATE)

    fun get(date: LocalDate, type: MealType): MealEntry? {
        val v = prefs.getString(key(date, type), null) ?: return null
        return if (v == NONE) MealEntry.None else MealEntry.Menu(v)
    }

    /**
     * @param complete true면 범위 안에서 메뉴가 없는 날·끼니를 "급식 없음"으로 기록한다.
     *   응답이 잘려서 왔을 수 있으면 false로 넘겨 빈 칸을 건드리지 않는다.
     */
    fun saveRange(from: LocalDate, to: LocalDate, meals: Map<Pair<LocalDate, MealType>, String>, complete: Boolean) {
        prefs.edit {
            var d = from
            while (!d.isAfter(to)) {
                for (type in MealType.entries) {
                    val menu = meals[d to type]
                    when {
                        !menu.isNullOrBlank() -> putString(key(d, type), menu)
                        complete -> putString(key(d, type), NONE)
                    }
                }
                d = d.plusDays(1)
            }
            putLong(K_LAST_SUCCESS, System.currentTimeMillis())
            remove(K_LAST_ERROR)

            val oldest = LocalDate.now().minusDays(7)
            prefs.all.keys
                .filter { it.startsWith(PREFIX) }
                .filter { k -> parseDate(k)?.isBefore(oldest) ?: true }
                .forEach { remove(it) }
        }
    }

    fun saveError(message: String) {
        prefs.edit {
            putString(K_LAST_ERROR, message)
            putLong(K_LAST_ERROR_AT, System.currentTimeMillis())
        }
    }

    val lastError: String? get() = prefs.getString(K_LAST_ERROR, null)
    val lastErrorAt: Long get() = prefs.getLong(K_LAST_ERROR_AT, 0L)
    val lastSuccessAt: Long get() = prefs.getLong(K_LAST_SUCCESS, 0L)

    fun clear() {
        prefs.edit { clear() }
    }

    /** meal_2_20261030 */
    private fun key(date: LocalDate, type: MealType) =
        "$PREFIX${type.code}_" + date.format(DateTimeFormatter.BASIC_ISO_DATE)

    private fun parseDate(k: String): LocalDate? =
        runCatching { LocalDate.parse(k.substringAfterLast('_'), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()

    private companion object {
        const val PREFIX = "meal_"
        const val NONE = "__NONE__"
        const val K_LAST_SUCCESS = "last_success"
        const val K_LAST_ERROR = "last_error"
        const val K_LAST_ERROR_AT = "last_error_at"
    }
}
