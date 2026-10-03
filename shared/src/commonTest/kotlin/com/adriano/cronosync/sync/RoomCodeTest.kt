package com.adriano.cronosync.sync

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomCodeTest {

    private val random = Random(seed = 42) // semente fixa: teste reproduzível

    @Test
    fun generatedCodesAreAlwaysValid() {
        repeat(1_000) {
            val code = RoomCode.generate(random)
            assertEquals(code, RoomCode.parse(code.value))
            assertEquals(code, RoomCode.parse(code.formatted))
        }
    }

    @Test
    fun formattedCodeHasAHyphenInTheMiddle() {
        val code = RoomCode.generate(random)

        assertTrue(Regex("^[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$").matches(code.formatted), code.formatted)
    }

    @Test
    fun anySingleMistypedCharacterIsDetected() {
        repeat(50) {
            val code = RoomCode.generate(random).value
            for (position in code.indices) {
                for (wrong in RoomCode.ALPHABET) {
                    if (wrong == code[position]) continue
                    val typo = code.substring(0, position) + wrong + code.substring(position + 1)
                    assertNull(RoomCode.parse(typo), "erro não detectado: $code → $typo")
                }
            }
        }
    }

    @Test
    fun mostSwapsOfNeighbouringCharactersAreDetected() {
        var swaps = 0
        var detected = 0
        repeat(200) {
            val code = RoomCode.generate(random).value
            for (i in 0 until code.length - 1) {
                if (code[i] == code[i + 1]) continue
                val swapped = code.substring(0, i) + code[i + 1] + code[i] + code.substring(i + 2)
                swaps++
                if (RoomCode.parse(swapped) == null) detected++
            }
        }
        // Luhn não pega 100% das trocas de vizinhos, mas pega quase todas.
        assertTrue(detected >= swaps * 0.95, "detectou $detected de $swaps trocas")
    }

    @Test
    fun parsingIsForgivingWithWhatPeopleActuallyType() {
        val code = RoomCode.generate(random)
        val typedCasually = " " + code.formatted.lowercase().replace('-', ' ') + " "

        assertEquals(code, RoomCode.parse(typedCasually))
    }

    @Test
    fun ambiguousLettersAreReadAsDigits() {
        // Procura um código gerado que contenha 0 e 1, e digita O e I/L no lugar.
        val code = generateSequence { RoomCode.generate(random) }.first { '0' in it.value && '1' in it.value }
        val typed = code.value.replace('0', 'O').replaceFirst('1', 'I').replace('1', 'L')

        assertEquals(code, RoomCode.parse(typed))
    }

    @Test
    fun rejectsWrongLengthAndForeignCharacters() {
        val valid = RoomCode.generate(random).value
        assertNull(RoomCode.parse(""))
        assertNull(RoomCode.parse(valid.dropLast(1)))
        assertNull(RoomCode.parse(valid + "0"))
        assertNull(RoomCode.parse("teste"))
        assertNull(RoomCode.parse(valid.replaceRange(0, 1, "U"))) // U não faz parte do alfabeto
        assertNotNull(RoomCode.parse(valid))
    }

    @Test
    fun urlsAreBuiltFromWhatTheUserTyped() {
        val code = RoomCode.generate(random)

        assertEquals("ws://192.168.0.10:8080/rooms/${code.value}", roomWebSocketUrl("192.168.0.10:8080", code))
        assertEquals("ws://localhost:8080/rooms/${code.value}", roomWebSocketUrl(" localhost:8080/ ", code))
        assertEquals("wss://crono.exemplo.com/rooms/${code.value}", roomWebSocketUrl("https://crono.exemplo.com", code))
        assertEquals("http://localhost:8080/rooms", createRoomUrl("localhost:8080"))
        assertEquals("https://crono.exemplo.com/rooms", createRoomUrl("wss://crono.exemplo.com/"))
    }
}
