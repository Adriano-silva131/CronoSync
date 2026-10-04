package com.adriano.cronosync.di

import com.adriano.cronosync.sync.data.SyncConfig
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

class SharedModuleTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun sharedModuleIsComplete() {
        // HttpClientEngine: o HttpClient é criado pelo builder, que o verify não enxerga.
        sharedModule.verify(extraTypes = listOf(Settings::class, CoroutineScope::class, HttpClientEngine::class, SyncConfig::class))
    }
}
