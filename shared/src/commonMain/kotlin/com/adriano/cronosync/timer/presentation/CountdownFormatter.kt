package com.adriano.cronosync.timer.presentation

import com.adriano.cronosync.core.pad2

/**
 * Formata o tempo restante como `MM:SS`, ou `H:MM:SS` a partir de uma hora.
 *
 * Arredonda os segundos PARA CIMA, como todo timer: com 4,2 s restantes mostra "00:05", e só
 * mostra "00:00" quando realmente acabou.
 */
fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) + 999) / 1_000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3_600

    val base = "${minutes.pad2()}:${seconds.pad2()}"
    return if (hours > 0) "$hours:$base" else base
}

/**
 * Tempo que já passou do fim, como `-MM:SS` (ou `-H:MM:SS`). Arredonda para BAIXO: "-00:01" só
 * aparece depois de um segundo inteiro de alarme tocando.
 */
fun formatOvertime(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1_000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3_600

    val base = "${minutes.pad2()}:${seconds.pad2()}"
    return if (hours > 0) "-$hours:$base" else "-$base"
}
