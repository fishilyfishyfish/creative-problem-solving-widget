package io.github.fishilyfishyfish.cpswidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

object WidgetRefresher {
    /** 값이 바뀌면 위젯이 다시 그려진다. */
    val TICK = longPreferencesKey("tick")

    suspend fun refresh(context: Context) {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(TodayWidget::class.java)
        for (id in ids) {
            updateAppWidgetState(context, id) { prefs -> prefs[TICK] = System.currentTimeMillis() }
            TodayWidget().update(context, id)
        }
    }

    fun hasWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TodayWidgetReceiver::class.java))
            .isNotEmpty()
}
