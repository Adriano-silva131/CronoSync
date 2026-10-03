package com.adriano.cronosync.desktop.integration

/** Mostra uma notificação do sistema operacional. */
fun interface NotificationSender {
    fun send(title: String, message: String)
}

/**
 * Escolhe a implementação para o sistema atual:
 * - Linux: `notify-send`, o padrão do desktop Linux (GNOME, KDE…) — funciona até sem bandeja;
 * - Windows (ou Linux sem notify-send): balão da área de notificação, pelo MESMO ícone de bandeja
 *   do app ([tray]) — sem criar um segundo ícone;
 * - nada disponível: não mostra nada — o timer continua vermelho na janela.
 */
fun createNotificationSender(tray: TrayController): NotificationSender {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        "linux" in os && LinuxNotificationSender.isAvailable() -> LinuxNotificationSender()
        // Sem bandeja instalada, notify() não mostra nada — o timer continua vermelho na janela.
        else -> NotificationSender { title, message -> tray.notify(title, message) }
    }
}
