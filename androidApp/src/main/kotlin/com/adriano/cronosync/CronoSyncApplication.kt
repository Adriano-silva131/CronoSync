package com.adriano.cronosync

import android.app.Application
import com.adriano.cronosync.alarm.PomodoroAlarmController
import com.adriano.cronosync.alarm.TimerAlarmController
import com.adriano.cronosync.alarm.TimerNotifications
import com.adriano.cronosync.di.androidModule
import com.adriano.cronosync.di.initKoin
import com.adriano.cronosync.sync.ConnectionWatcher
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

/**
 * Primeira classe criada quando o processo do app nasce — seja para abrir a tela, seja para
 * atender um alarme ou um botão de notificação com o app fechado.
 */
class CronoSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidLogger()
            androidContext(this@CronoSyncApplication)
            modules(androidModule)
        }
        get<TimerNotifications>().createChannels()
        get<TimerAlarmController>().start(scope = get())
        get<PomodoroAlarmController>().start(scope = get())
        get<ConnectionWatcher>().start()
    }
}
