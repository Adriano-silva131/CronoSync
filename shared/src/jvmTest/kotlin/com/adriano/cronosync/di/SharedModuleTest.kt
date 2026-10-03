package com.adriano.cronosync.di

import com.russhwolf.settings.Settings
import com.adriano.cronosync.sync.SyncConfig
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

class SharedModuleTest {

    /** Garante que todo construtor declarado no Koin tem suas dependências definidas (falha no teste, não no app). */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun sharedModuleIsComplete() {
        // Settings, o CoroutineScope do app e o SyncConfig são fornecidos por cada plataforma.
        // HttpClientEngine: o verify olha o construtor do HttpClient, mas criamos pelo builder
        // `HttpClient { }`, que escolhe o motor de rede sozinho (OkHttp no Android).
        sharedModule.verify(extraTypes = listOf(Settings::class, CoroutineScope::class, HttpClientEngine::class, SyncConfig::class))
    }
}
