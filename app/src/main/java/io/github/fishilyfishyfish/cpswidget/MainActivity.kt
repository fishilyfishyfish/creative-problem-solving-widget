package io.github.fishilyfishyfish.cpswidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import io.github.fishilyfishyfish.cpswidget.schedule.MealFetchWorker
import io.github.fishilyfishyfish.cpswidget.schedule.TickScheduler
import io.github.fishilyfishyfish.cpswidget.ui.AppTheme
import io.github.fishilyfishyfish.cpswidget.ui.SettingsScreen
import io.github.fishilyfishyfish.cpswidget.widget.WidgetRefresher
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (WidgetRefresher.hasWidgets(this)) {
            MealFetchWorker.schedulePeriodic(this)
            MealFetchWorker.ensureFresh(this)
        }
        setContent {
            AppTheme {
                SettingsScreen()
            }
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
}
