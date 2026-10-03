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
import com.adriano.cronosync.alarm.AlarmSource
import com.adriano.cronosync.timer.data.AlarmPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.koin.android.ext.android.inject

/**
 * Toca o alarme de verdade: som em loop no volume de ALARME + vibração, até alguém parar.
 *
 * Por que um foreground service e não o som da notificação?
 * - O som de notificação passa pelos filtros do sistema (modo vibrar, "Não perturbe", regras de
 *   fabricantes como a Xiaomi) e pode simplesmente não tocar. Um MediaPlayer com USAGE_ALARM é
 *   tratado como despertador: toca mesmo com o celular no vibrar.
 * - Um foreground service mantém o processo vivo enquanto toca; sem ele o Android poderia
 *   matar o app no meio do alarme.
 *
 * Quem liga é o [AlarmFiredReceiver] (fim do timer, troca de fase do Pomodoro); quem desliga são os
 * controllers de cada fonte, quando o alarme dela é zerado ou silenciado.
 */
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
        // A fonte pedida pode já ter sido silenciada no meio do caminho: mostra a que ainda toca.
        val source = requested.takeIf { it in ringingSources.value } ?: ringingSources.value.firstOrNull()
        // startForeground é OBRIGATÓRIO logo no início, mesmo que vá parar em seguida: quem inicia um
        // serviço em primeiro plano tem 5 s para chamá-lo, senão o Android encerra o app.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notificationFor(source ?: requested),
            // systemExempted: tipo reservado a apps de alarme/timer com permissão de alarme exato.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0,
        )
        if (source == null) {
            // Tudo foi silenciado antes de o serviço rodar.
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (source == AlarmSource.Timer) timerNotifications.cancelRunning()
        if (!ringing) {
            ringing = true
            startRinging()
        }
        // Se o sistema matar o serviço, não recriar sozinho: os controllers decidem se ainda deve tocar.
        return START_NOT_STICKY
    }

    private fun notificationFor(source: AlarmSource) = when (source) {
        AlarmSource.Timer -> timerNotifications.buildFinished()
        AlarmSource.Pomodoro -> pomodoroNotifications.buildRinging()
    }

    /**
     * Respeita as preferências deste aparelho. Com som e vibração desligados, só a tela de alarme
     * aparece (quem abre é a notificação de foreground, com full-screen intent).
     */
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
        }.getOrNull() // sem som disponível (ex.: toque removido), ainda sobra a vibração e a tela

        // repeat = 0: repete o padrão desde o início até cancel().
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

        /** Notificação própria do serviço (as fontes têm as suas para quando ele não pode rodar). */
        private const val NOTIFICATION_ID = 10

        /**
         * Quais fontes estão tocando agora. O som é um só; ele para quando TODAS forem silenciadas —
         * parar o alarme do Pomodoro não pode calar um timer que acabou ao mesmo tempo.
         */
        private val ringingSources = MutableStateFlow<Set<AlarmSource>>(emptySet())

        /** @return false se o sistema não permitiu iniciar o serviço agora. */
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
                // Continua tocando pela outra fonte: troca a notificação para a dela.
                runCatching { ContextCompat.startForegroundService(context, intent(context, remaining.first())) }
            }
        }

        private fun intent(context: Context, source: AlarmSource) =
            Intent(context, AlarmRingingService::class.java).putExtra(EXTRA_ALARM_SOURCE, source.name)
    }
}
