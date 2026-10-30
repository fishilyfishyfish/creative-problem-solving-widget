package io.github.fishilyfishyfish.cpswidget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import io.github.fishilyfishyfish.cpswidget.schedule.MealFetchWorker
import io.github.fishilyfishyfish.cpswidget.schedule.TickScheduler
import io.github.fishilyfishyfish.cpswidget.ui.AppTheme
import io.github.fishilyfishyfish.cpswidget.ui.SettingsScreen
import io.github.fishilyfishyfish.cpswidget.update.InstallState
import io.github.fishilyfishyfish.cpswidget.update.UpdateCheckWorker
import io.github.fishilyfishyfish.cpswidget.update.UpdateInstaller
import io.github.fishilyfishyfish.cpswidget.update.Updates
import io.github.fishilyfishyfish.cpswidget.widget.WidgetRefresher
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (WidgetRefresher.hasWidgets(this)) {
            MealFetchWorker.schedulePeriodic(this)
            MealFetchWorker.ensureFresh(this)
        }

        // 업데이트: 하루 한 번 백그라운드 확인 + 앱을 열 때 한 번 확인
        Updates.load(this)
        UpdateCheckWorker.schedule(this)
        lifecycleScope.launch { runCatching { Updates.check(this@MainActivity) } }
        askNotificationPermissionOnce()

        setContent {
            AppTheme {
                SettingsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // "출처를 알 수 없는 앱 설치"를 허용하고 돌아왔으면 다시 업데이트 버튼을 보여준다.
        if (Updates.install.value == InstallState.NeedsPermission && UpdateInstaller.canInstall(this)) {
            Updates.install.value = InstallState.Idle
        }
    }

    /** 앱을 나갈 때 마지막으로 바꾼 내용을 위젯에 반영한다. */
    override fun onStop() {
        super.onStop()
        val app = applicationContext
        lifecycleScope.launch {
            runCatching {
                WidgetRefresher.refresh(app)
                TickScheduler.scheduleNext(app)
                MealFetchWorker.ensureFresh(app)
            }
        }
    }

    /** 새 버전 알림을 위해 안드로이드 13 이상에서 알림 권한을 처음 한 번만 묻는다. */
    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit { putBoolean("asked_notifications", true) }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
