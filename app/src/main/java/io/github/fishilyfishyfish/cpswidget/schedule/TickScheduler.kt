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
                // 폰이 잠들어 있어도(절전 상태 포함) 제시각에 깨워서 위젯을 바꿔둔다.
                // 하루 20번 정도, 매번 1초 미만이라 배터리 영향은 거의 없다.
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
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
