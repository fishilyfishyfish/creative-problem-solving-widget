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
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.fishilyfishyfish.cpswidget.MainActivity
import io.github.fishilyfishyfish.cpswidget.data.MealStore
import io.github.fishilyfishyfish.cpswidget.data.SettingsStore
import io.github.fishilyfishyfish.cpswidget.logic.DayPlanner
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
            val ui = loadUi(LocalContext.current)
            WidgetContent(ui)
        }
    }

    private fun loadUi(context: Context): WidgetUi {
        val settings = SettingsStore(context).load()
        val now = LocalDateTime.now()
        val target = DayPlanner.targetDay(now, settings)
        val meals = MealStore(context)
        return WidgetUiBuilder.build(now, settings, meals.get(target.date), meals.lastError)
    }
}

private object Palette {
    val background: ColorProvider = DayNight(day = Color(0xFFFDFBF7), night = Color(0xFF1E1F22))
    val text: ColorProvider = DayNight(day = Color(0xFF1F2328), night = Color(0xFFE8E6E3))
    val subtle: ColorProvider = DayNight(day = Color(0xFF6B6F76), night = Color(0xFF9DA1A8))
    val divider: ColorProvider = DayNight(day = Color(0xFFE6E2DA), night = Color(0xFF34363B))
    val nowBg: ColorProvider = DayNight(day = Color(0xFF2F6FEB), night = Color(0xFF4C8DFF))
    val nowText: ColorProvider = DayNight(day = Color(0xFFFFFFFF), night = Color(0xFF0E1116))
    val nextBg: ColorProvider = DayNight(day = Color(0xFFE3ECFD), night = Color(0xFF26324A))
    val warnBg: ColorProvider = DayNight(day = Color(0xFFFDE7E4), night = Color(0xFF4A2724))
    val warnText: ColorProvider = DayNight(day = Color(0xFFB42318), night = Color(0xFFFFB4A9))
}

@Composable
private fun WidgetContent(ui: WidgetUi) {
    val openApp = GlanceModifier.clickable(actionStartActivity<MainActivity>())

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Palette.background)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .then(openApp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().then(openApp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ui.title,
                style = TextStyle(color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            if (ui.status.isNotEmpty()) {
                Text(
                    text = ui.status,
                    style = TextStyle(color = Palette.subtle, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            if (ui.periods.isEmpty()) {
                item { Note("시간표 미입력 · 눌러서 입력", openApp) }
            } else {
                items(ui.periods, itemId = { it.period.toLong() }) { row -> PeriodLine(row, openApp) }
            }

            item { SectionHeader("점심", openApp) }

            when (val meal = ui.meal) {
                MealUi.NoSchool -> item { Note("학교 미설정 · 눌러서 설정", openApp) }
                MealUi.Loading -> item { Note("급식 불러오는 중", openApp) }
                MealUi.NoMeal -> item { Note("급식 정보 없음", openApp) }
                is MealUi.Failed -> item { Note("급식을 불러오지 못함 · ${meal.message}", openApp) }
                is MealUi.Menu -> items(meal.dishes) { dish -> DishLine(dish, openApp) }
            }
        }
    }
}

@Composable
private fun PeriodLine(row: PeriodRow, openApp: GlanceModifier) {
    val (bg, fg) = when (row.highlight) {
        Highlight.NOW -> Palette.nowBg to Palette.nowText
        Highlight.NEXT -> Palette.nextBg to Palette.text
        Highlight.NONE -> null to Palette.text
    }
    val subFg = if (row.highlight == Highlight.NOW) Palette.nowText else Palette.subtle
    var modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    if (bg != null) modifier = modifier.background(bg).cornerRadius(8.dp)

    Column(GlanceModifier.fillMaxWidth().padding(bottom = 2.dp).then(openApp)) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${row.period}",
                modifier = GlanceModifier.width(20.dp),
                style = TextStyle(color = subFg, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = row.subject,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = fg,
                    fontSize = 14.sp,
                    fontWeight = if (row.highlight == Highlight.NONE) FontWeight.Normal else FontWeight.Bold,
                ),
                maxLines = 1,
            )
            if (row.time.isNotEmpty()) {
                Text(text = row.time, style = TextStyle(color = subFg, fontSize = 12.sp))
            }
        }
    }
}

@Composable
private fun DishLine(dish: DishRow, openApp: GlanceModifier) {
    val warning = dish.warning
    Column(GlanceModifier.fillMaxWidth().padding(bottom = 2.dp).then(openApp)) {
        if (warning == null) {
            Text(
                text = dish.name,
                modifier = GlanceModifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = TextStyle(color = Palette.text, fontSize = 14.sp),
                maxLines = 1,
            )
        } else {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(Palette.warnBg)
                    .cornerRadius(8.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "⚠ ${dish.name}",
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = Palette.warnText, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    text = warning,
                    style = TextStyle(color = Palette.warnText, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, openApp: GlanceModifier) {
    Column(GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).then(openApp)) {
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Palette.divider)) {}
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = title,
            modifier = GlanceModifier.padding(horizontal = 8.dp),
            style = TextStyle(color = Palette.subtle, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun Note(text: String, openApp: GlanceModifier) {
    Text(
        text = text,
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).then(openApp),
        style = TextStyle(color = Palette.subtle, fontSize = 13.sp),
    )
}
