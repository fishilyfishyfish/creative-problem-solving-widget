package io.github.fishilyfishyfish.cpswidget.data

import android.content.Context
import androidx.core.content.edit
import io.github.fishilyfishyfish.cpswidget.BuildConfig
import io.github.fishilyfishyfish.cpswidget.logic.DEFAULT_DISMISSAL
import io.github.fishilyfishyfish.cpswidget.logic.DEFAULT_MEALS
import io.github.fishilyfishyfish.cpswidget.logic.DEFAULT_PERIOD_TIMES
import io.github.fishilyfishyfish.cpswidget.logic.MAX_PERIODS
import io.github.fishilyfishyfish.cpswidget.logic.MealSetting
import io.github.fishilyfishyfish.cpswidget.logic.MealType
import io.github.fishilyfishyfish.cpswidget.logic.PeriodTime
import io.github.fishilyfishyfish.cpswidget.logic.SCHOOL_DAYS
import io.github.fishilyfishyfish.cpswidget.logic.School
import io.github.fishilyfishyfish.cpswidget.logic.Settings

/** 앱 설정을 SharedPreferences에 저장한다. 위젯·알람·워커가 같은 값을 읽는다. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings_v2", Context.MODE_PRIVATE)

    fun load(): Settings {
        val schoolCode = prefs.getString(K_SCHOOL_CODE, null)
        val school = if (schoolCode.isNullOrBlank()) null else School(
            officeCode = prefs.getString(K_SCHOOL_OFFICE, "") ?: "",
            schoolCode = schoolCode,
            name = prefs.getString(K_SCHOOL_NAME, "") ?: "",
            officeName = prefs.getString(K_SCHOOL_OFFICE_NAME, "") ?: "",
            address = prefs.getString(K_SCHOOL_ADDRESS, "") ?: "",
        )

        val subjects = SCHOOL_DAYS.associateWith { d ->
            List(MAX_PERIODS) { i -> prefs.getString("subject_${d}_${i + 1}", "") ?: "" }
        }
        val rooms = SCHOOL_DAYS.associateWith { d ->
            List(MAX_PERIODS) { i -> prefs.getString("room_${d}_${i + 1}", "") ?: "" }
        }

        val periodTimes = List(MAX_PERIODS) { i ->
            val p = i + 1
            if (!prefs.contains("pt_start_$p")) {
                DEFAULT_PERIOD_TIMES.getOrNull(i)
            } else {
                val start = prefs.getInt("pt_start_$p", -1)
                val end = prefs.getInt("pt_end_$p", -1)
                if (start < 0 || end < 0) null else PeriodTime(start, end)
            }
        }

        val dismissal = SCHOOL_DAYS.associateWith { d -> prefs.getInt("dismiss_$d", DEFAULT_DISMISSAL) }

        val meals = MealType.entries.associateWith { type ->
            val def = DEFAULT_MEALS.getValue(type)
            MealSetting(
                enabled = prefs.getBoolean("meal_${type.code}_on", def.enabled),
                time = PeriodTime(
                    prefs.getInt("meal_${type.code}_start", def.time.startMin),
                    prefs.getInt("meal_${type.code}_end", def.time.endMin),
                ),
            )
        }

        val allergens = (prefs.getStringSet(K_ALLERGENS, emptySet()) ?: emptySet())
            .mapNotNull { it.toIntOrNull() }
            .toSet()

        return Settings(
            school = school,
            apiKey = prefs.getString(K_API_KEY, "") ?: "",
            subjects = subjects,
            rooms = rooms,
            periodTimes = periodTimes,
            dismissal = dismissal,
            meals = meals,
            allergens = allergens,
        )
    }

    fun save(s: Settings) {
        prefs.edit {
            val school = s.school
            if (school == null) {
                remove(K_SCHOOL_CODE)
                remove(K_SCHOOL_OFFICE)
                remove(K_SCHOOL_NAME)
                remove(K_SCHOOL_OFFICE_NAME)
                remove(K_SCHOOL_ADDRESS)
            } else {
                putString(K_SCHOOL_CODE, school.schoolCode)
                putString(K_SCHOOL_OFFICE, school.officeCode)
                putString(K_SCHOOL_NAME, school.name)
                putString(K_SCHOOL_OFFICE_NAME, school.officeName)
                putString(K_SCHOOL_ADDRESS, school.address)
            }
            putString(K_API_KEY, s.apiKey.trim())

            for (d in SCHOOL_DAYS) {
                val subjects = s.subjectsOf(d)
                val rooms = s.roomsOf(d)
                for (i in 0 until MAX_PERIODS) {
                    putString("subject_${d}_${i + 1}", subjects[i])
                    putString("room_${d}_${i + 1}", rooms[i])
                }
                putInt("dismiss_$d", s.dismissalOf(d))
            }

            for (i in 0 until MAX_PERIODS) {
                val t = s.periodTimes.getOrNull(i)
                putInt("pt_start_${i + 1}", t?.startMin ?: -1)
                putInt("pt_end_${i + 1}", t?.endMin ?: -1)
            }

            for (type in MealType.entries) {
                val m = s.mealOf(type)
                putBoolean("meal_${type.code}_on", m.enabled)
                putInt("meal_${type.code}_start", m.time.startMin)
                putInt("meal_${type.code}_end", m.time.endMin)
            }

            putStringSet(K_ALLERGENS, s.allergens.map { it.toString() }.toSet())
        }
    }

    private companion object {
        const val K_SCHOOL_CODE = "school_code"
        const val K_SCHOOL_OFFICE = "school_office"
        const val K_SCHOOL_NAME = "school_name"
        const val K_SCHOOL_OFFICE_NAME = "school_office_name"
        const val K_SCHOOL_ADDRESS = "school_address"
        const val K_API_KEY = "api_key"
        const val K_ALLERGENS = "allergens"
    }
}

/** 앱에 입력한 키가 있으면 그걸, 없으면 빌드에 포함된 키를 쓴다. */
fun Settings.effectiveApiKey(): String = apiKey.trim().ifBlank { BuildConfig.NEIS_API_KEY }
