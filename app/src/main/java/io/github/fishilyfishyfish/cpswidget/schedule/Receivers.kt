package io.github.fishilyfishyfish.cpswidget.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.fishilyfishyfish.cpswidget.update.UpdateCheckWorker
import io.github.fishilyfishyfish.cpswidget.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 위젯 갱신 → 다음 알람 예약 → 필요하면 급식 받아오기 */
object Updater {
    suspend fun onTick(context: Context) {
        WidgetRefresher.refresh(context)
        TickScheduler.scheduleNext(context)
        MealFetchWorker.ensureFresh(context)
    }
}

private fun BroadcastReceiver.runAsync(context: Context, block: suspend (Context) -> Unit) {
    val pending = goAsync()
    val app = context.applicationContext
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block(app)
        } catch (e: Exception) {
            Log.w("CpsWidget", "update failed", e)
        } finally {
            pending.finish()
        }
    }
}

/** TickScheduler가 건 알람 */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync(context) { Updater.onTick(it) }
    }
}

/** 재부팅, 앱 업데이트, 시각·시간대 변경 후 알람을 다시 건다. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync(context) {
            Updater.onTick(it)
            MealFetchWorker.schedulePeriodic(it)
            UpdateCheckWorker.schedule(it)
        }
    }
}
