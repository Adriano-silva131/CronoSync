package com.adriano.cronosync.sync

import kotlin.jvm.JvmInline
import kotlin.random.Random

/**
 * Código de sala, exibido como "ABCD-EFGH".
 *
 * - Alfabeto Crockford Base32 (sem I, L, O, U): ao ditar ou digitar, ninguém confunde 0/O ou 1/I/L.
 * - 7 caracteres aleatórios (32⁷ ≈ 34 bilhões de combinações) + 1 dígito verificador.
 * - Dígito verificador (Luhn mod 32, o mesmo princípio do cartão de crédito): detecta QUALQUER
 *   caractere digitado errado e quase todas as trocas de dois vizinhos — o app avisa do erro antes
 *   mesmo de falar com o servidor.
 *
 * Unicidade: quem gera os códigos é o servidor, e ele confere se o código já existe antes de
 * entregá-lo (ver RoomRegistry no módulo :server). O app nunca inventa códigos.
 */
@JvmInline
value class RoomCode private constructor(
    /** Forma canônica, sem hífen: usada na URL e no armazenamento. */
    val value: String,
) {
    /** Forma para mostrar ao usuário. */
    val formatted: String get() = "${value.take(4)}-${value.drop(4)}"

    override fun toString(): String = formatted

    companion object {
        const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        const val LENGTH = 8
        private const val RANDOM_LENGTH = LENGTH - 1
        private const val BASE = 32

        /** Gera um código novo. No servidor, [random] deve ser criptograficamente seguro. */
        fun generate(random: Random): RoomCode {
            val payload = buildString { repeat(RANDOM_LENGTH) { append(ALPHABET[random.nextInt(BASE)]) } }
            return RoomCode(payload + checkCharacter(payload))
        }

        /**
         * Interpreta o que o usuário digitou, com tolerância: minúsculas, hífens e espaços são
         * aceitos, e O → 0, I/L → 1. Devolve null se o formato ou o dígito verificador não bater.
         */
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

        /**
         * Luhn mod N: da direita para a esquerda, multiplica alternadamente por 2 e 1 e soma os
         * "dígitos" do produto na base N. Um código válido tem soma múltipla de N.
         */
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

/** Endereço digitado ("192.168.0.10:8080", "ws://…", "https://…") → partes para montar URLs. */
private data class ServerAddress(val secure: Boolean, val host: String)

private fun parseServerAddress(serverAddress: String): ServerAddress {
    val address = serverAddress.trim().trimEnd('/')
    val secureSchemes = listOf("wss://", "https://")
    val plainSchemes = listOf("ws://", "http://")
    secureSchemes.firstOrNull { address.startsWith(it) }?.let { return ServerAddress(true, address.removePrefix(it)) }
    plainSchemes.firstOrNull { address.startsWith(it) }?.let { return ServerAddress(false, address.removePrefix(it)) }
    return ServerAddress(false, address)
}

/** "192.168.0.10:8080" → "ws://192.168.0.10:8080/rooms/ABCDEFGH". */
fun roomWebSocketUrl(serverAddress: String, code: RoomCode): String {
    val server = parseServerAddress(serverAddress)
    return "${if (server.secure) "wss" else "ws"}://${server.host}/rooms/${code.value}"
}

/** "192.168.0.10:8080" → "http://192.168.0.10:8080/rooms" (onde se cria uma sala nova). */
fun createRoomUrl(serverAddress: String): String {
    val server = parseServerAddress(serverAddress)
    return "${if (server.secure) "https" else "http"}://${server.host}/rooms"
}
