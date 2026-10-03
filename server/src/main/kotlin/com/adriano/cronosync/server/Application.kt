package com.adriano.cronosync.server

import com.adriano.cronosync.core.Clock
import com.adriano.cronosync.core.SystemClock
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Sobe o servidor. Configuração por variáveis de ambiente:
 * - PORT: porta HTTP/WebSocket (padrão 8080);
 * - CRONOSYNC_DB_URL, CRONOSYNC_DB_USER, CRONOSYNC_DB_PASSWORD: o PostgreSQL das salas. Só em modo
 *   de desenvolvimento (-Dio.ktor.development=true) há um padrão: o banco do docker-compose.yml
 *   (porta 5434). Fora dele as três são obrigatórias — o servidor se recusa a subir sem elas, em
 *   vez de cair calado numa senha de desenvolvimento.
 * - CRONOSYNC_BEHIND_PROXY=true: SÓ quando o servidor estiver atrás de um proxy reverso (nginx,
 *   Caddy, o balanceador da hospedagem). Ver [module].
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val development = System.getProperty("io.ktor.development") == "true"
    /** Variável de ambiente; o padrão de desenvolvimento só vale em modo de desenvolvimento. */
    fun config(name: String, developmentDefault: String): String =
        System.getenv(name) ?: developmentDefault.takeIf { development }
            ?: error("Defina a variável de ambiente $name (fora do modo de desenvolvimento ela é obrigatória)")
    val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config("CRONOSYNC_DB_URL", developmentDefault = "jdbc:postgresql://localhost:5434/cronosync")
            username = config("CRONOSYNC_DB_USER", developmentDefault = "cronosync")
            password = config("CRONOSYNC_DB_PASSWORD", developmentDefault = "cronosync")
            // Poucas conexões bastam: cada operação leva milissegundos e os comandos são toques de gente.
            maximumPoolSize = 5
        },
    )
    // Antes de tudo: deixa as tabelas na versão que este código espera.
    migrateDatabase(dataSource)
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module(
            store = PostgresRoomStore(dataSource),
            behindProxy = System.getenv("CRONOSYNC_BEHIND_PROXY") == "true",
        )
        monitor.subscribe(ApplicationStopped) { dataSource.close() }
        if (developmentMode) log.info("Página de teste: http://localhost:{}/dev/", port)
    }.start(wait = true)
}

/**
 * Configuração do servidor, separada do main() para os testes poderem subir o mesmo servidor
 * em memória, com um relógio falso e as salas guardadas num [InMemoryRoomStore].
 *
 * [behindProxy]: atrás de um proxy reverso, toda conexão chega com o IP DO PROXY, e os limites
 * por IP tratariam todos os usuários como um só. Ligado, o servidor lê o IP real do cabeçalho
 * X-Forwarded-For que o proxy acrescenta. Desligado de propósito fora de um proxy: aí qualquer um
 * poderia mandar um X-Forwarded-For inventado e escapar dos limites trocando de "IP" à vontade.
 */
fun Application.module(
    clock: Clock = SystemClock,
    store: RoomStore = InMemoryRoomStore(),
    behindProxy: Boolean = false,
) {
    val registry = RoomRegistry(clock, store)
    // Limpeza das salas expiradas: uma vez ao subir e depois de hora em hora. Uma sala pode durar
    // até 1 hora além do limite de inatividade — irrelevante para um limite de 1 dia.
    launch {
        while (true) {
            val removed = registry.removeInactive()
            if (removed > 0) log.info("Salas expiradas removidas: {}", removed)
            delay(1.hours)
        }
    }

    if (behindProxy) {
        install(XForwardedHeaders) {
            // O ÚLTIMO endereço da lista é o que o NOSSO proxy viu. Os anteriores vieram do próprio
            // cliente e podem ser inventados.
            useLastProxy()
        }
    }

    install(WebSockets) {
        // Pings de controle do próprio WebSocket: detectam aparelhos que sumiram sem avisar
        // (ex.: perderam o sinal) e liberam a conexão.
        pingPeriod = 15.seconds
        timeout = 30.seconds
        // Tamanho máximo de uma mensagem. Os aparelhos mandam mensagens de poucas centenas de bytes;
        // a maior que o servidor ENVIA (estado com 200 voltas) fica em ~15 KB. Algo maior é abuso:
        // a conexão é fechada antes de o servidor ler o conteúdo.
        maxFrameSize = MAX_FRAME_BYTES
    }
    install(RateLimit) {
        // Criação de salas: até 10 por minuto por endereço IP. Ao passar, o servidor responde
        // 429 (Too Many Requests) e o app avisa para esperar.
        // Atrás de um proxy reverso, o IP real só é lido com behindProxy ligado (ver acima).
        register(CREATE_ROOM_RATE_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteAddress }
        }
        // Tentativas de conexão a salas: até 60 por minuto por IP. Sobra para reconexões normais
        // (mesmo várias pessoas atrás do mesmo IP); um script testando códigos ao acaso esbarra logo.
        register(ROOM_CONNECT_RATE_LIMIT) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteAddress }
        }
    }
    routing {
        get("/") { call.respondText("CronoSync server") }
        syncRoutes(registry, clock, ConnectionLimiter(MAX_CONNECTIONS_PER_ADDRESS))
        // Página para testar a sincronização pelo navegador. Só em modo de desenvolvimento.
        if (developmentMode) {
            staticResources("/dev", "dev", index = "sync-tester.html")
        }
    }
}

val CREATE_ROOM_RATE_LIMIT = RateLimitName("create-room")
val ROOM_CONNECT_RATE_LIMIT = RateLimitName("room-connect")

/** Conexões WebSocket abertas ao mesmo tempo por IP (ver ConnectionLimiter). */
const val MAX_CONNECTIONS_PER_ADDRESS = 50

const val MAX_FRAME_BYTES = 32L * 1024
