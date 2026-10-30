package io.github.fishilyfishyfish.cpswidget.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.fishilyfishyfish.cpswidget.MainActivity
import io.github.fishilyfishyfish.cpswidget.R
import java.util.concurrent.TimeUnit

/** 하루 한 번 새 버전을 확인하고, 있으면 알림을 띄운다. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val release = try {
            Updates.check(applicationContext)
        } catch (e: Exception) {
            return Result.success() // 다음 주기에 다시 확인
        } ?: return Result.success()

        if (!Updates.wasNotified(applicationContext, release.build) && notify(applicationContext, release)) {
            Updates.markNotified(applicationContext, release.build)
        }
        return Result.success()
    }

    companion object {
        private const val WORK = "update-check"
        private const val CHANNEL = "updates"
        private const val NOTIFICATION_ID = 1

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun notify(context: Context, release: Release): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return false

            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("업데이트")
                    .build(),
            )
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
                ),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_update)
                .setContentTitle("새 버전 있음")
                .setContentText("빌드 ${release.build} · 눌러서 앱 열기")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            return try {
                manager.notify(NOTIFICATION_ID, notification)
                true
            } catch (e: SecurityException) {
                false
            }
        }
    }
}
