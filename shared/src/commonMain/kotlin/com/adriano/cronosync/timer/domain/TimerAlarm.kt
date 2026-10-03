package com.adriano.cronosync.timer.domain

/** Instante (epoch millis) em que o timer vai acabar, ou `null` se ele não está rodando. */
fun Timer.finishesAtMillis(): Long? =
    if (status == TimerStatus.Running && runningSinceMillis != null) {
        runningSinceMillis + (durationMillis - accumulatedMillis)
    } else {
        null
    }

/**
 * O que a plataforma deve fazer com o alarme para um dado estado do timer.
 *
 * A decisão é regra de negócio (fica aqui, testável e reaproveitável no desktop); só a *execução*
 * — AlarmManager e notificações no Android — é específica de cada plataforma.
 */
sealed interface AlarmPlan {
    /** Timer rodando: garantir um alarme agendado para [atMillis]. */
    data class Schedule(val atMillis: Long) : AlarmPlan

    /** Timer terminou e ninguém zerou: o alarme está (ou deveria estar) tocando — não mexer. */
    data object Ringing : AlarmPlan

    /** Parado, pausado ou zerado: cancelar alarme agendado e silenciar o que estiver tocando. */
    data object Cancel : AlarmPlan
}

/**
 * @param silencedFinishAtMillis fim de timer cujo alarme já foi parado NESTE aparelho (ver
 *   AlarmSilenceRepository). Esse fim não toca mais, mesmo que o servidor ainda não saiba.
 */
fun Timer.alarmPlan(nowMillis: Long, silencedFinishAtMillis: Long? = null): AlarmPlan {
    val finishesAt = finishesAtMillis() ?: return AlarmPlan.Cancel
    if (finishesAt == silencedFinishAtMillis) return AlarmPlan.Cancel
    return if (finishesAt > nowMillis) AlarmPlan.Schedule(finishesAt) else AlarmPlan.Ringing
}
