import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("com.adriano.cronosync.server.ApplicationKt")
}

// `./gradlew :server:run` sobe em modo de desenvolvimento: habilita a página de teste em /dev/.
// Um servidor empacotado para produção não recebe essa flag, então a página não existe lá.
tasks.named<JavaExec>("run") {
    systemProperty("io.ktor.development", "true")
}

// Os .jar vêm do cache do Gradle com permissão 600 (só o dono lê). A imagem Docker roda com um
// usuário sem privilégios (ver Dockerfile), que precisa conseguir LER tudo: libera leitura para
// todos (escrita continua só do dono).
tasks.named<Sync>("installDist") {
    eachFile {
        permissions {
            group.read = true
            other.read = true
            // Os scripts de partida (bin/server) continuam executáveis.
            if (path.startsWith("bin/")) {
                user.execute = true
                group.execute = true
                other.execute = true
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // O mesmo módulo que o app usa: domínio (Stopwatch, Timer) + protocolo (SyncProtocol).
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.rate.limit)
    // IP real do aparelho quando o servidor roda atrás de um proxy reverso (ver Application.kt).
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.logback.classic)
    // Salas no PostgreSQL: o driver JDBC + um pool de conexões.
    implementation(libs.postgresql)
    implementation(libs.hikari)
    // Migrações: cada mudança na estrutura do banco é um arquivo .sql numerado em
    // src/main/resources/db/migration, aplicado uma única vez, em ordem, ao subir o servidor.
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.multiplatform.settings.test)
    // Sobe um PostgreSQL de verdade num contêiner Docker só para os testes do PostgresRoomStore.
    testImplementation(libs.testcontainers.postgresql)
}
