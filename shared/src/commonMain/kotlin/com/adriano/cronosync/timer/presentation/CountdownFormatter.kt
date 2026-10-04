package com.adriano.cronosync.timer.presentation

import com.adriano.cronosync.core.pad2

fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) + 999) / 1_000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3_600

    val base = "${minutes.pad2()}:${seconds.pad2()}"
    return if (hours > 0) "$hours:$base" else base
}

fun formatOvertime(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1_000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3_600

    val base = "${minutes.pad2()}:${seconds.pad2()}"
    return if (hours > 0) "-$hours:$base" else "-$base"
}
