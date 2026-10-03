package com.adriano.cronosync.stopwatch.presentation

import com.adriano.cronosync.core.pad2

/**
 * Formata milissegundos como `MM:SS.cc` (centésimos), ou `H:MM:SS.cc` a partir de uma hora.
 * Fica no código compartilhado para que Android, desktop e web exibam o tempo exatamente igual.
 */
fun formatElapsed(millis: Long): String {
    val safeMillis = millis.coerceAtLeast(0L)
    val centis = (safeMillis / 10) % 100
    val totalSeconds = safeMillis / 1_000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3_600

    val base = "${minutes.pad2()}:${seconds.pad2()}.${centis.pad2()}"
    return if (hours > 0) "$hours:$base" else base
}
