package com.adriano.cronosync.desktop.integration

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin

/** Toca o aviso sonoro de fim do timer. */
fun interface AlarmPlayer {
    fun play()
}

/**
 * Aviso SUAVE: dois tons curtos (como um "plim-plom"), repetidos 3 vezes, ~2,5 s no total. Não fica
 * tocando até alguém parar — no desktop o alerta é propositalmente menos agressivo que no celular.
 *
 * O som é gerado pelo código (onda senoidal com entrada e saída suaves), então funciona igual no
 * Windows e no Linux sem arquivo de áudio e sem depender dos sons do sistema.
 */
class ChimeAlarmPlayer : AlarmPlayer {

    override fun play() {
        // Thread própria: tocar bloqueia até o fim, e não pode travar a interface.
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
            // Sem dispositivo de áudio disponível: a notificação e a janela ainda avisam.
        }
    }

    /** Tom senoidal em PCM 16 bits, com envelope de entrada/saída para não "estalar". */
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
        /** 30% do volume máximo: audível, sem susto. */
        const val VOLUME = 0.3
    }
}
