package com.adriano.cronosync.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.adriano.cronosync.alarm.data.AlarmPreferencesRepository
import com.adriano.cronosync.alarm.data.AlarmSource
import com.adriano.cronosync.alarm.notification.PomodoroNotifications
import com.adriano.cronosync.alarm.notification.TimerNotifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.koin.android.ext.android.inject

class AlarmRingingService : Service() {

    private val timerNotifications: TimerNotifications by inject()
    private val pomodoroNotifications: PomodoroNotifications by inject()
    private val alarmPreferences: AlarmPreferencesRepository by inject()
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var ringing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = intent?.alarmSource() ?: AlarmSource.Timer
        val source = requested.takeIf { it in ringingSources.value } ?: ringingSources.value.firstOrNull()
        // Obrigatório logo no início: sem startForeground em 5 s o Android encerra o app.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notificationFor(source ?: requested),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0,
        )
        if (source == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (source == AlarmSource.Timer) timerNotifications.cancelRunning()
        if (!ringing) {
            ringing = true
            startRinging()
        }
        return START_NOT_STICKY
    }

    private fun notificationFor(source: AlarmSource) = when (source) {
        AlarmSource.Timer -> timerNotifications.buildFinished()
        AlarmSource.Pomodoro -> pomodoroNotifications.buildRinging()
    }

    private fun startRinging() {
        val preferences = alarmPreferences.preferences.value
        val alarmAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = if (!preferences.sound) null else runCatching {
            MediaPlayer().apply {
                setAudioAttributes(alarmAudio)
                setDataSource(this@AlarmRingingService, sound)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()

        vibrator = if (!preferences.vibration) null else vibrator().also { vibrator ->
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> vibrator.vibrate(
                    VibrationEffect.createWaveform(VIBRATION_PATTERN, 0),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> @Suppress("DEPRECATION")
                vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0), alarmAudio)
                else -> @Suppress("DEPRECATION")
                vibrator.vibrate(VIBRATION_PATTERN, 0, alarmAudio)
            }
        }
    }

    override fun onDestroy() {
        player?.run {
            stop()
            release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        super.onDestroy()
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    companion object {
        private val VIBRATION_PATTERN = longArrayOf(0L, 800L, 600L)

        private const val NOTIFICATION_ID = 10

        // O som só para quando todas as fontes forem silenciadas: parar o Pomodoro não cala um timer.
        private val ringingSources = MutableStateFlow<Set<AlarmSource>>(emptySet())

        fun start(context: Context, source: AlarmSource): Boolean {
            ringingSources.update { it + source }
            return runCatching {
                ContextCompat.startForegroundService(context, intent(context, source))
            }.onFailure { ringingSources.update { sources -> sources - source } }.isSuccess
        }

        fun stop(context: Context, source: AlarmSource) {
            if (source !in ringingSources.value) return
            ringingSources.update { it - source }
            val remaining = ringingSources.value
            if (remaining.isEmpty()) {
                context.stopService(Intent(context, AlarmRingingService::class.java))
            } else {
                runCatching { ContextCompat.startForegroundService(context, intent(context, remaining.first())) }
            }
        }

        private fun intent(context: Context, source: AlarmSource) =
            Intent(context, AlarmRingingService::class.java).putExtra(EXTRA_ALARM_SOURCE, source.name)
    }
}
