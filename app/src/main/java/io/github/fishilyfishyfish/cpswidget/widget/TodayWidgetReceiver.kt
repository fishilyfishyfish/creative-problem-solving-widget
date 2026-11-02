package io.github.fishilyfishyfish.cpswidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import io.github.fishilyfishyfish.cpswidget.schedule.MealFetchWorker
import io.github.fishilyfishyfish.cpswidget.schedule.TickScheduler

/** 홈 화면 위젯. 위젯을 추가했을 때와 30분마다(안전장치) 불린다. */
class TodayWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRefresher.refresh(context)
        TickScheduler.scheduleNext(context)
        MealFetchWorker.schedulePeriodic(context)
        MealFetchWorker.ensureFresh(context)
    }

    override fun onDisabled(context: Context) {
        TickScheduler.cancel(context)
        MealFetchWorker.cancelPeriodic(context)
    }
}
