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
import com.adriano.cronosync.ui.alarm.AlarmRoute
import com.adriano.cronosync.ui.alarm.PomodoroAlarmRoute
import com.adriano.cronosync.ui.theme.CronoSyncTheme

/**
 * Tela cheia de "tempo esgotado", separada da MainActivity de propósito:
 * - aparece POR CIMA da tela de bloqueio, sem desbloquear o aparelho;
 * - liga o display se ele estiver apagado e o mantém aceso enquanto o alarme toca;
 * - roda numa task própria (ver manifesto), então fechar o alarme não abre o app por baixo.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        source = intent.alarmSource()
        setContent {
            CronoSyncTheme {
                // Cada fonte tem sua tela: o timer acabou (Parar zera) / a fase mudou (Parar só silencia).
                when (source) {
                    AlarmSource.Timer -> AlarmRoute(onDismissed = ::finish)
                    AlarmSource.Pomodoro -> PomodoroAlarmRoute(onDismissed = ::finish)
                }
            }
        }
    }

    /** A tela é única (singleInstance): um alarme de outra fonte chega por aqui e troca o conteúdo. */
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
