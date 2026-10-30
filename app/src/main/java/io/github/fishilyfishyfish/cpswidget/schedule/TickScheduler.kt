package io.github.fishilyfishyfish.cpswidget.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.fishilyfishyfish.cpswidget.data.SettingsStore
import io.github.fishilyfishyfish.cpswidget.logic.DayPlanner
import io.github.fishilyfishyfish.cpswidget.widget.WidgetRefresher
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 교시 시작·끝, 하교, 자정마다 위젯을 다시 그리도록 알람을 하나씩 예약한다.
 * 알람이 울리면 TickReceiver가 위젯을 갱신하고 다음 알람을 다시 건다.
 */
object TickScheduler {
    private const val ACTION_TICK = "io.github.fishilyfishyfish.cpswidget.TICK"

    fun scheduleNext(context: Context) {
        if (!WidgetRefresher.hasWidgets(context)) {
            cancel(context)
            return
        }
        val settings = SettingsStore(context).load()
        // 경계 시각 직후에 깨어나도록 2초 여유를 둔다.
        val next = DayPlanner.nextBoundary(LocalDateTime.now(), settings).plusSeconds(2)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                // RTC(비깨우기): 화면이 꺼져 있으면 다음에 켜질 때 울린다. 배터리 부담 없음.
                alarmManager.setExact(AlarmManager.RTC, triggerAt, pi)
            } else {
                alarmManager.setWindow(AlarmManager.RTC, triggerAt, 60_000L, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.setWindow(AlarmManager.RTC, triggerAt, 60_000L, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TickReceiver::class.java).setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
