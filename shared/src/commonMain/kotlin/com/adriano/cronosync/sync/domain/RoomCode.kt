package com.adriano.cronosync.sync.domain

import kotlin.jvm.JvmInline
import kotlin.random.Random

@JvmInline
value class RoomCode private constructor(
    val value: String,
) {
    val formatted: String get() = "${value.take(4)}-${value.drop(4)}"

    override fun toString(): String = formatted

    companion object {
        const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        const val LENGTH = 8
        private const val RANDOM_LENGTH = LENGTH - 1
        private const val BASE = 32

        fun generate(random: Random): RoomCode {
            val payload = buildString { repeat(RANDOM_LENGTH) { append(ALPHABET[random.nextInt(BASE)]) } }
            return RoomCode(payload + checkCharacter(payload))
        }

        fun parse(input: String): RoomCode? {
            val normalized = buildString {
                for (char in input.uppercase()) {
                    when (char) {
                        '-', ' ' -> Unit
                        'O' -> append('0')
                        'I', 'L' -> append('1')
                        else -> append(char)
                    }
                }
            }
            if (normalized.length != LENGTH || normalized.any { it !in ALPHABET }) return null
            return if (luhnSum(normalized, startFactor = 1) % BASE == 0) RoomCode(normalized) else null
        }

        private fun checkCharacter(payload: String): Char =
            ALPHABET[(BASE - luhnSum(payload, startFactor = 2) % BASE) % BASE]

        private fun luhnSum(code: String, startFactor: Int): Int {
            var factor = startFactor
            var sum = 0
            for (index in code.indices.reversed()) {
                val product = factor * ALPHABET.indexOf(code[index])
                sum += product / BASE + product % BASE
                factor = if (factor == 2) 1 else 2
            }
            return sum
        }
    }
}
