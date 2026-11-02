package io.github.fishilyfishyfish.cpswidget.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import io.github.fishilyfishyfish.cpswidget.MainActivity
import io.github.fishilyfishyfish.cpswidget.R
import io.github.fishilyfishyfish.cpswidget.logic.Highlight
import io.github.fishilyfishyfish.cpswidget.logic.MealUi
import io.github.fishilyfishyfish.cpswidget.logic.WidgetUi

/**
 * WidgetUi를 위젯 화면(RemoteViews)으로 바꾼다.
 *
 * 홈 화면은 같은 레이아웃이면 기존 화면 위에 덮어쓰는 방식으로 갱신한다.
 * 그래서 글자, 배경, 보이기/숨기기를 매번 빠짐없이 지정한다. 하나라도 생략하면 예전 값이 남는다.
 */
object WidgetRenderer {

    private val ROWS = intArrayOf(
        R.id.period_row_1, R.id.period_row_2, R.id.period_row_3,
        R.id.period_row_4, R.id.period_row_5, R.id.period_row_6,
        R.id.period_row_7, R.id.period_row_8, R.id.period_row_9,
    )
    private val SUBJECTS = intArrayOf(
        R.id.period_subject_1, R.id.period_subject_2, R.id.period_subject_3,
        R.id.period_subject_4, R.id.period_subject_5, R.id.period_subject_6,
        R.id.period_subject_7, R.id.period_subject_8, R.id.period_subject_9,
    )
    private val ROOMS = intArrayOf(
        R.id.period_room_1, R.id.period_room_2, R.id.period_room_3,
        R.id.period_room_4, R.id.period_room_5, R.id.period_room_6,
        R.id.period_room_7, R.id.period_room_8, R.id.period_room_9,
    )
    private val DIVIDERS = intArrayOf(
        R.id.period_div_1, R.id.period_div_2, R.id.period_div_3,
        R.id.period_div_4, R.id.period_div_5, R.id.period_div_6,
        R.id.period_div_7, R.id.period_div_8, R.id.period_div_9,
    )
    private val DISHES = intArrayOf(
        R.id.dish_1, R.id.dish_2, R.id.dish_3, R.id.dish_4,
        R.id.dish_5, R.id.dish_6, R.id.dish_7, R.id.dish_8,
        R.id.dish_9, R.id.dish_10, R.id.dish_11, R.id.dish_12,
    )

    fun render(context: Context, ui: WidgetUi): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_today)

        // 머리줄
        rv.setTextViewText(R.id.day_label, ui.dayLabel)
        rv.setInt(R.id.meal_title, "setBackgroundResource", if (ui.mealNow) R.drawable.marker_red else R.drawable.marker_none)
        rv.setTextViewText(R.id.meal_label, ui.mealLabel)
        rv.setViewVisibility(R.id.meal_label, visibleIf(ui.mealLabel.isNotEmpty()))

        // 교시
        note(rv, R.id.period_note, if (ui.periods.isEmpty()) "시간표 미입력" else null)
        for (i in ROWS.indices) {
            val row = ui.periods.getOrNull(i)
            rv.setViewVisibility(ROWS[i], visibleIf(row != null))
            rv.setViewVisibility(DIVIDERS[i], visibleIf(row != null && i < ui.periods.lastIndex))
            if (row == null) {
                rv.setTextViewText(SUBJECTS[i], "")
                rv.setInt(SUBJECTS[i], "setBackgroundResource", R.drawable.marker_none)
                rv.setTextViewText(ROOMS[i], "")
                rv.setViewVisibility(ROOMS[i], View.GONE)
                continue
            }
            val marker = when (row.highlight) {
                Highlight.NOW -> R.drawable.marker_red
                Highlight.NEXT -> R.drawable.marker_blue
                Highlight.NONE -> R.drawable.marker_none
            }
            rv.setTextViewText(SUBJECTS[i], styled(row.subject, bold = row.highlight != Highlight.NONE))
            rv.setInt(SUBJECTS[i], "setBackgroundResource", marker)
            rv.setTextViewText(ROOMS[i], row.room)
            rv.setViewVisibility(ROOMS[i], visibleIf(row.room.isNotEmpty()))
        }

        // 급식
        val meal = ui.meal
        val dishes = if (meal is MealUi.Menu) meal.dishes else emptyList()
        note(
            rv,
            R.id.meal_note,
            when (meal) {
                MealUi.NoSchool -> "학교 미설정"
                MealUi.AllOff -> "식사 시간 모두 없음"
                MealUi.Loading -> "불러오는 중"
                MealUi.NoMeal -> "급식 정보 없음"
                is MealUi.Failed -> "불러오지 못함\n${meal.message}"
                is MealUi.Menu -> null
            },
        )
        for (i in DISHES.indices) {
            val dish = dishes.getOrNull(i)
            rv.setViewVisibility(DISHES[i], visibleIf(dish != null))
            rv.setTextViewText(DISHES[i], if (dish == null) "" else styled(dish.name, bold = dish.flagged))
            rv.setInt(
                DISHES[i],
                "setBackgroundResource",
                if (dish?.flagged == true) R.drawable.marker_yellow else R.drawable.marker_none,
            )
        }

        // 위젯 아무 곳이나 누르면 앱 열기
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        rv.setOnClickPendingIntent(R.id.widget_root, open)
        return rv
    }

    private fun note(rv: RemoteViews, id: Int, text: String?) {
        rv.setTextViewText(id, text ?: "")
        rv.setViewVisibility(id, visibleIf(text != null))
    }

    private fun visibleIf(visible: Boolean) = if (visible) View.VISIBLE else View.GONE

    private fun styled(text: String, bold: Boolean): CharSequence {
        if (!bold) return text
        return SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
