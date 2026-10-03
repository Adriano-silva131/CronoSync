package com.adriano.cronosync.timer.presentation

internal fun Long.toDurationFields(): DurationFields {
    val totalSeconds = this / 1_000
    return DurationFields(
        hours = (totalSeconds / 3_600).toInt(),
        minutes = ((totalSeconds / 60) % 60).toInt(),
        seconds = (totalSeconds % 60).toInt(),
    )
}

internal fun DurationFields.toMillis(): Long = ((hours * 3_600L) + (minutes * 60L) + seconds) * 1_000L

/**
 * Soma [delta] a um campo, "dando a volta" dentro do limite dele (como um relógio):
 * segundos/minutos em 0..59, horas em 0..99. Os outros campos não mudam.
 */
internal fun DurationFields.step(field: DurationField, delta: Int): DurationFields = when (field) {
    DurationField.Hours -> copy(hours = (hours + delta).wrap(100))
    DurationField.Minutes -> copy(minutes = (minutes + delta).wrap(60))
    DurationField.Seconds -> copy(seconds = (seconds + delta).wrap(60))
}

private fun Int.wrap(size: Int): Int = mod(size)
