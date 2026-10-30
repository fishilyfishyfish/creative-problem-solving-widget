package io.github.fishilyfishyfish.cpswidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import io.github.fishilyfishyfish.cpswidget.schedule.MealFetchWorker
import io.github.fishilyfishyfish.cpswidget.schedule.TickScheduler

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        TickScheduler.scheduleNext(context)
        MealFetchWorker.schedulePeriodic(context)
        MealFetchWorker.ensureFresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        TickScheduler.cancel(context)
        MealFetchWorker.cancelPeriodic(context)
    }
}
