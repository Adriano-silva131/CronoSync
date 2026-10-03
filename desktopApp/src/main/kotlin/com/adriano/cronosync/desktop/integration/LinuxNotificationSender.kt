package com.adriano.cronosync.desktop.integration

import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Notificação no Linux pelo `notify-send` (libnotify), que conversa com o servidor de notificações
 * do ambiente (no GNOME, a própria gnome-shell) pelo padrão freedesktop. Os textos vão como
 * argumentos separados, sem passar por shell: nenhum caractere precisa ser escapado.
 */
class LinuxNotificationSender : NotificationSender {

    override fun send(title: String, message: String) {
        try {
            ProcessBuilder(
                "notify-send",
                "--app-name=CronoSync",
                "--icon=alarm-symbolic",
                "--urgency=normal",
                title,
                message,
            ).redirectErrorStream(true).start()
        } catch (e: IOException) {
            // notify-send sumiu depois da checagem inicial: sem notificação, a janela ainda mostra o fim.
        }
    }

    companion object {
        fun isAvailable(): Boolean = try {
            ProcessBuilder("notify-send", "--version").start().waitFor(2, TimeUnit.SECONDS)
        } catch (e: IOException) {
            false
        }
    }
}
