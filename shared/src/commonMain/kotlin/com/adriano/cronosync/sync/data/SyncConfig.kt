package com.adriano.cronosync.sync.data

enum class AppEnvironment {
    LocalTesting,

    Staging,

    Production,
}

data class SyncConfig(
    val defaultServerAddress: String,
    val environment: AppEnvironment,
) {
    val isFixedServer: Boolean get() = environment != AppEnvironment.Production

    companion object {
        val LocalTesting = SyncConfig(defaultServerAddress = "localhost:8080", environment = AppEnvironment.LocalTesting)

        val Staging = SyncConfig(defaultServerAddress = "wss://homologacao.focussync.com.br", environment = AppEnvironment.Staging)

        val Configurable = SyncConfig(defaultServerAddress = "localhost:8080", environment = AppEnvironment.Production)
    }
}
