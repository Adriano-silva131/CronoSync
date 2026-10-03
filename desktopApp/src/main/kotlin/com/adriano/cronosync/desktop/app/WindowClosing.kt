package com.adriano.cronosync.desktop.app

/** O que fazer quando a pessoa fecha a janela principal. */
enum class CloseAction {
    /** Há bandeja: a janela some e o app continua rodando (alarmes valem). */
    HideToTray,

    /** Sem bandeja e com contagem rodando: perguntar (Minimizar / Sair / Cancelar). */
    AskBeforeExit,

    /** Sem bandeja e nada rodando: encerrar. */
    Exit,
}

/**
 * Regra do fechamento. Sem bandeja, o app NÃO se esconde: não haveria como recuperá-lo. Nesse caso
 * só pergunta antes de sair se algo está contando, porque com o app fechado não há aviso no fim.
 */
fun closeAction(trayAvailable: Boolean, hasActiveCountdown: Boolean): CloseAction = when {
    trayAvailable -> CloseAction.HideToTray
    hasActiveCountdown -> CloseAction.AskBeforeExit
    else -> CloseAction.Exit
}
