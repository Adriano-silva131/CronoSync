package com.adriano.cronosync.desktop.integration.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin

fun interface AlarmPlayer {
    fun play()
}

class ChimeAlarmPlayer : AlarmPlayer {

    override fun play() {
        Thread(::playBlocking, "cronosync-chime").apply { isDaemon = true }.start()
    }

    private fun playBlocking() {
        val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
        try {
            AudioSystem.getSourceDataLine(format).use { line ->
                line.open(format)
                line.start()
                val chime = tone(880.0, 180) + silence(40) + tone(1318.5, 280)
                repeat(3) {
                    line.write(chime, 0, chime.size)
                    val pause = silence(380)
                    line.write(pause, 0, pause.size)
                }
                line.drain()
            }
        } catch (e: Exception) {
        }
    }

    private fun tone(frequency: Double, millis: Int): ByteArray {
        val samples = SAMPLE_RATE * millis / 1_000
        val fade = SAMPLE_RATE * 15 / 1_000
        val bytes = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val envelope = minOf(1.0, i.toDouble() / fade, (samples - i).toDouble() / (fade * 6))
            val value = (sin(2 * PI * frequency * i / SAMPLE_RATE) * envelope * VOLUME * Short.MAX_VALUE).toInt()
            bytes[2 * i] = value.toByte()
            bytes[2 * i + 1] = (value shr 8).toByte()
        }
        return bytes
    }

    private fun silence(millis: Int) = ByteArray(SAMPLE_RATE * millis / 1_000 * 2)

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val VOLUME = 0.3
    }
}
