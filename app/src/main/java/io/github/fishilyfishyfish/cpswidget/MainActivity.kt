package io.github.fishilyfishyfish.cpswidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.fishilyfishyfish.cpswidget.schedule.MealFetchWorker
import io.github.fishilyfishyfish.cpswidget.ui.AppTheme
import io.github.fishilyfishyfish.cpswidget.ui.SettingsScreen
import io.github.fishilyfishyfish.cpswidget.widget.WidgetRefresher

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
}
