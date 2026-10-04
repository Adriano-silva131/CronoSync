package com.adriano.cronosync.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Não apagar por parecer vazio: acordar o app no boot/atualização faz o Application reagendar os alarmes.
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
    }
}
