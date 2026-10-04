package com.adriano.cronosync.timer.domain

fun Timer.finishesAtMillis(): Long? =
    if (status == TimerStatus.Running && runningSinceMillis != null) {
        runningSinceMillis + (durationMillis - accumulatedMillis)
    } else {
        null
    }

sealed interface AlarmPlan {
    data class Schedule(val atMillis: Long) : AlarmPlan

    data object Ringing : AlarmPlan

    data object Cancel : AlarmPlan
}

fun Timer.alarmPlan(nowMillis: Long, silencedFinishAtMillis: Long? = null): AlarmPlan {
    val finishesAt = finishesAtMillis() ?: return AlarmPlan.Cancel
    if (finishesAt == silencedFinishAtMillis) return AlarmPlan.Cancel
    return if (finishesAt > nowMillis) AlarmPlan.Schedule(finishesAt) else AlarmPlan.Ringing
}
