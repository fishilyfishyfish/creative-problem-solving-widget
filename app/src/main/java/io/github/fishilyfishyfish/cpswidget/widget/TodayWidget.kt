package io.github.fishilyfishyfish.cpswidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.fishilyfishyfish.cpswidget.MainActivity
import io.github.fishilyfishyfish.cpswidget.data.MealStore
import io.github.fishilyfishyfish.cpswidget.data.SettingsStore
import io.github.fishilyfishyfish.cpswidget.logic.DishRow
import io.github.fishilyfishyfish.cpswidget.logic.Highlight
import io.github.fishilyfishyfish.cpswidget.logic.MealUi
import io.github.fishilyfishyfish.cpswidget.logic.PeriodRow
import io.github.fishilyfishyfish.cpswidget.logic.WidgetUi
import io.github.fishilyfishyfish.cpswidget.logic.WidgetUiBuilder
import java.time.LocalDateTime
import androidx.glance.color.ColorProvider as DayNight

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // 상태 값을 읽어두면 WidgetRefresher가 값을 바꿀 때마다 다시 그려진다.
            currentState(WidgetRefresher.TICK)
            WidgetContent(loadUi(LocalContext.current))
        }
    }

    private fun loadUi(context: Context): WidgetUi {
        val settings = SettingsStore(context).load()
        val meals = MealStore(context)
        return WidgetUiBuilder.build(LocalDateTime.now(), settings, meals::get, meals.lastError)
    }
}

private object Palette {
    val background: ColorProvider = DayNight(day = Color(0xFFFDFBF7), night = Color(0xFF1E1F22))
    val text: ColorProvider = DayNight(day = Color(0xFF1F2328), night = Color(0xFFE8E6E3))
    val subtle: ColorProvider = DayNight(day = Color(0xFF6B6F76), night = Color(0xFF9DA1A8))
    val line: ColorProvider = DayNight(day = Color(0xFFD9D4CA), night = Color(0xFF3A3C42))
    val clear: ColorProvider = DayNight(day = Color.Transparent, night = Color.Transparent)

    /** 지금 */
    val red: ColorProvider = DayNight(day = Color(0xFFFFA59E), night = Color(0xFF8E3B34))

    /** 다음 */
    val blue: ColorProvider = DayNight(day = Color(0xFFA9C8FF), night = Color(0xFF2F4F86))

    /** 알레르기 */
    val yellow: ColorProvider = DayNight(day = Color(0xFFFFEE58), night = Color(0xFF6E6100))
}

@Composable
private fun WidgetContent(ui: WidgetUi) {
    val openApp = GlanceModifier.clickable(actionStartActivity<MainActivity>())

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Palette.background)
            .cornerRadius(16.dp)
            .padding(10.dp)
            .then(openApp),
    ) {
        // 머리줄: 요일 | 급식
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                Text(
                    text = ui.dayLabel,
                    style = TextStyle(color = Palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
            }
            Box(GlanceModifier.width(1.dp).height(20.dp).background(Palette.line)) {}
            Row(
                modifier = GlanceModifier.defaultWeight().padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "급식",
                    modifier = marker(if (ui.mealNow) Palette.red else null),
                    style = TextStyle(color = Palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                if (ui.mealLabel.isNotEmpty()) {
                    Text(
                        text = " ${ui.mealLabel}",
                        style = TextStyle(color = Palette.subtle, fontSize = 12.sp),
                    )
                }
            }
        }
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Palette.line)) {}

        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
            item {
                Row(GlanceModifier.fillMaxWidth().then(openApp)) {
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (ui.periods.isEmpty()) {
                            Note("시간표 미입력")
                        } else {
                            // Glance 컨테이너는 자식이 10개까지라서 칸 하나를 한 묶음으로 만든다.
                            ui.periods.forEachIndexed { i, row ->
                                PeriodCell(row, divider = i < ui.periods.lastIndex)
                            }
                        }
                    }
                    Box(GlanceModifier.width(1.dp).fillMaxHeight().background(Palette.line)) {}
                    Column(modifier = GlanceModifier.defaultWeight().padding(start = 8.dp, top = 4.dp)) {
                        when (val meal = ui.meal) {
                            MealUi.NoSchool -> Note("학교 미설정")
                            MealUi.AllOff -> Note("식사 시간 모두 없음")
                            MealUi.Loading -> Note("불러오는 중")
                            MealUi.NoMeal -> Note("급식 정보 없음")
                            is MealUi.Failed -> Note("불러오지 못함\n${meal.message}")
                            is MealUi.Menu -> meal.dishes.chunked(8).forEach { chunk ->
                                Column { chunk.forEach { DishLine(it) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 형광펜처럼 글자 뒤에 색을 칠한다.
 * 강조가 없을 때도 투명 배경을 꼭 지정해야 한다. 위젯은 기존 화면에 덮어쓰는 방식으로 갱신돼서,
 * 배경을 생략하면 예전에 칠한 색이 그대로 남는다.
 */
private fun marker(color: ColorProvider?): GlanceModifier =
    GlanceModifier.background(color ?: Palette.clear).cornerRadius(4.dp).padding(horizontal = 3.dp)

@Composable
private fun PeriodCell(row: PeriodRow, divider: Boolean) {
    val color = when (row.highlight) {
        Highlight.NOW -> Palette.red
        Highlight.NEXT -> Palette.blue
        Highlight.NONE -> null
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Column(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = row.subject,
                modifier = marker(color),
                style = TextStyle(
                    color = Palette.text,
                    fontSize = 14.sp,
                    fontWeight = if (row.highlight == Highlight.NONE) FontWeight.Normal else FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            if (row.room.isNotEmpty()) {
                Text(
                    text = row.room,
                    style = TextStyle(color = Palette.subtle, fontSize = 11.sp, textAlign = TextAlign.Center),
                    maxLines = 1,
                )
            }
        }
        if (divider) {
            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Palette.line)) {}
        }
    }
}

@Composable
private fun DishLine(dish: DishRow) {
    Box(GlanceModifier.padding(bottom = 4.dp)) {
        Text(
            text = dish.name,
            modifier = marker(if (dish.flagged) Palette.yellow else null),
            style = TextStyle(
                color = Palette.text,
                fontSize = 13.sp,
                fontWeight = if (dish.flagged) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        modifier = GlanceModifier.padding(vertical = 6.dp),
        style = TextStyle(color = Palette.subtle, fontSize = 12.sp),
    )
}
