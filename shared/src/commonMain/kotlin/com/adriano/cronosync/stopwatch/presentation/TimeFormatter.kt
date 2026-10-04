package com.adriano.cronosync.stopwatch.presentation

import com.adriano.cronosync.core.pad2

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
