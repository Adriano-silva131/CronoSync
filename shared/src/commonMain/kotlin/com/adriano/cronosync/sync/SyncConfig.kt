package com.adriano.cronosync.sync

/** Qual versão do app é esta — decide para qual servidor ela aponta. */
enum class AppEnvironment {
    /** Testes no computador de desenvolvimento: presa ao servidor local. */
    LocalTesting,

    /** Homologação: presa ao servidor de testes publicado na internet (homologacao.focussync.com.br). */
    Staging,

    /** Versão final: ainda não há servidor de produção, então o usuário informa o endereço. */
    Production,
}

/**
 * Para onde o app se conecta. Cada app escolhe a sua no módulo Koin da plataforma, conforme a
 * versão (ver [AppEnvironment]).
 */
data class SyncConfig(
    val defaultServerAddress: String,
    val environment: AppEnvironment,
) {
    /**
     * Versões de testes (local e homologação): o endereço é fixo ([defaultServerAddress]) e a tela
     * não mostra o campo "Servidor" — impossível, sem querer, conectar em outro lugar.
     */
    val isFixedServer: Boolean get() = environment != AppEnvironment.Production

    companion object {
        /**
         * Servidor na própria máquina (desktop). O celular não usa este: a versão de testes do
         * Android aponta para o IP do PC na rede local, detectado no build (ver androidApp).
         */
        val LocalTesting = SyncConfig(defaultServerAddress = "localhost:8080", environment = AppEnvironment.LocalTesting)

        /** wss:// = conexão criptografada (o nginx do servidor cuida do certificado HTTPS). */
        val Staging = SyncConfig(defaultServerAddress = "wss://homologacao.focussync.com.br", environment = AppEnvironment.Staging)

        /** Versões finais: ainda não há servidor publicado, então o usuário informa o endereço. */
        val Configurable = SyncConfig(defaultServerAddress = "localhost:8080", environment = AppEnvironment.Production)
    }
}
