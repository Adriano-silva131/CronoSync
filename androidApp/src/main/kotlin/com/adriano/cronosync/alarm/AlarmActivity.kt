package com.adriano.cronosync.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adriano.cronosync.alarm.data.AlarmSource
import com.adriano.cronosync.ui.alarm.AlarmRoute
import com.adriano.cronosync.ui.alarm.PomodoroAlarmRoute
import com.adriano.cronosync.ui.theme.CronoSyncTheme

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        source = intent.alarmSource()
        setContent {
            CronoSyncTheme {
                when (source) {
                    AlarmSource.Timer -> AlarmRoute(onDismissed = ::finish)
                    AlarmSource.Pomodoro -> PomodoroAlarmRoute(onDismissed = ::finish)
                }
            }
        }
    }

    private var source by mutableStateOf(AlarmSource.Timer)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        source = intent.alarmSource()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }
}
