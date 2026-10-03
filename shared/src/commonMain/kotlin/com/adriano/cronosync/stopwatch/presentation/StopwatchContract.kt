package com.adriano.cronosync.stopwatch.presentation

import com.adriano.cronosync.stopwatch.domain.StopwatchStatus

/**
 * Fluxo unidirecional (UDF):
 *
 *   UI ──(StopwatchAction)──▶ ViewModel ──(StopwatchCommand)──▶ Repository
 *   UI ◀──(StopwatchUiState)── ViewModel ◀──(Stopwatch)──────── Repository
 *
 * A UI nunca altera estado diretamente: ela só desenha o [StopwatchUiState] e emite [StopwatchAction]s.
 */
data class StopwatchUiState(
    val status: StopwatchStatus = StopwatchStatus.Idle,
    val elapsedText: String = formatElapsed(0L),
    val laps: List<LapUiModel> = emptyList(),
    /** false numa sala sem conexão: os botões ficam desabilitados em vez de engolir toques. */
    val controlsEnabled: Boolean = true,
    /** Chegou a [com.adriano.cronosync.stopwatch.domain.Stopwatch.MAX_LAPS]: o botão Volta fica desabilitado. */
    val lapLimitReached: Boolean = false,
)

/** Volta já formatada para exibição; [number] serve de chave estável em listas. */
data class LapUiModel(
    val number: Int,
    val lapText: String,
    val totalText: String,
)

/**
 * O que o usuário fez na tela. Hoje mapeia 1:1 para os comandos de domínio, mas são conceitos
 * diferentes: ações futuras como "entrar numa sala" são eventos de UI, não comandos do cronômetro.
 */
sealed interface StopwatchAction {
    data object Start : StopwatchAction
    data object Pause : StopwatchAction
    data object Reset : StopwatchAction
    data object Lap : StopwatchAction
}
