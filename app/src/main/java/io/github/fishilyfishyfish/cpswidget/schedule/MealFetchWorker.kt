package io.github.fishilyfishyfish.cpswidget.schedule

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.fishilyfishyfish.cpswidget.data.MealStore
import io.github.fishilyfishyfish.cpswidget.data.NeisClient
import io.github.fishilyfishyfish.cpswidget.data.SettingsStore
import io.github.fishilyfishyfish.cpswidget.data.effectiveApiKey
import io.github.fishilyfishyfish.cpswidget.logic.DayPlanner
import io.github.fishilyfishyfish.cpswidget.widget.WidgetRefresher
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** 나이스에서 점심 메뉴를 받아 캐시에 저장하고 위젯을 다시 그린다. */
class MealFetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).load()
        val school = settings.school ?: return Result.success()
        val store = MealStore(applicationContext)
        val apiKey = settings.effectiveApiKey()

        val today = LocalDate.now()
        // 인증키가 없으면 한 번에 받을 수 있는 양이 적어서 다음 등교일까지만 받는다.
        val to = if (apiKey.isBlank()) DayPlanner.nextSchoolDay(today) else today.plusDays(7)

        return try {
            val result = NeisClient.fetchLunch(school, today, to, apiKey)
            store.saveRange(today, to, result.meals, result.complete)
            WidgetRefresher.refresh(applicationContext)
            Result.success()
        } catch (e: Exception) {
            store.saveError(e.message ?: "알 수 없는 오류")
            WidgetRefresher.refresh(applicationContext)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val NOW = "meal-fetch-now"
        private const val PERIODIC = "meal-fetch-periodic"

        private val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** 지금 바로 받아오기 (설정 저장, 수동 새로고침) */
        fun enqueueNow(context: Context, replace: Boolean = true) {
            val request = OneTimeWorkRequestBuilder<MealFetchWorker>()
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /** 위젯에 보일 날짜의 급식이 캐시에 없으면 받아온다. */
        fun ensureFresh(context: Context) {
            val settings = SettingsStore(context).load()
            if (settings.school == null) return
            val store = MealStore(context)
            val target = DayPlanner.targetDay(LocalDateTime.now(), settings)
            val missing = store.get(LocalDate.now()) == null || store.get(target.date) == null
            if (!missing) return
            // 방금 실패했다면 잠깐 쉬었다가 다시 시도한다.
            val recentlyFailed = System.currentTimeMillis() - store.lastErrorAt < 10 * 60 * 1000L &&
                store.lastErrorAt > store.lastSuccessAt
            if (recentlyFailed) return
            enqueueNow(context, replace = false)
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<MealFetchWorker>(6, TimeUnit.HOURS)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
