package io.github.fishilyfishyfish.cpswidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import io.github.fishilyfishyfish.cpswidget.data.MealStore
import io.github.fishilyfishyfish.cpswidget.data.SettingsStore
import io.github.fishilyfishyfish.cpswidget.logic.WidgetUiBuilder
import java.time.LocalDateTime

object WidgetRefresher {

    /**
     * 위젯을 지금 바로 다시 그린다. 백그라운드 작업 대기열을 거치지 않아서
     * 알람이 울린 그 순간에 화면이 바뀐다.
     */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, TodayWidgetReceiver::class.java))
        if (ids.isEmpty()) return
        val settings = SettingsStore(app).load()
        val meals = MealStore(app)
        val ui = WidgetUiBuilder.build(LocalDateTime.now(), settings, meals::get, meals.lastError)
        manager.updateAppWidget(ids, WidgetRenderer.render(app, ui))
    }

    fun hasWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TodayWidgetReceiver::class.java))
            .isNotEmpty()
}
