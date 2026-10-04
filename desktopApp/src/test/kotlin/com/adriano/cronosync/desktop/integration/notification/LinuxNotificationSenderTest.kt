package com.adriano.cronosync.desktop.integration.notification

import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals

class LinuxNotificationSenderTest {

    private val delivered = mutableListOf<Pair<String, String>>()
    private val fallback = mutableListOf<Pair<String, String>>()

    private fun sender(deliver: (String, String) -> Unit) = LinuxNotificationSender(
        fallback = { title, message -> fallback += title to message },
        executor = Executor(Runnable::run),
        deliver = deliver,
    )

    @Test
    fun notificationServerAvailableDoesNotUseTheFallback() {
        sender { title, message -> delivered += title to message }.send("Tempo esgotado!", "O timer chegou ao fim.")

        assertEquals(listOf("Tempo esgotado!" to "O timer chegou ao fim."), delivered)
        assertEquals(emptyList(), fallback)
    }

    @Test
    fun notificationServerUnavailableFallsBack() {
        sender { _, _ -> error("org.freedesktop.Notifications indisponível") }.send("Tempo esgotado!", "O timer chegou ao fim.")

        assertEquals(listOf("Tempo esgotado!" to "O timer chegou ao fim."), fallback)
    }
}
