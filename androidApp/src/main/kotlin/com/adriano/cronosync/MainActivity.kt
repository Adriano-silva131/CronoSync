package com.adriano.cronosync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.adriano.cronosync.ui.CronoSyncContent
import com.adriano.cronosync.ui.pomodoro.AndroidPomodoroRoute
import com.adriano.cronosync.ui.theme.CronoSyncTheme
import com.adriano.cronosync.ui.timer.AndroidTimerRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CronoSyncTheme {
                CronoSyncContent(
                    timer = { AndroidTimerRoute(modifier = it) },
                    pomodoro = { AndroidPomodoroRoute(modifier = it) },
                )
            }
        }
    }
}
