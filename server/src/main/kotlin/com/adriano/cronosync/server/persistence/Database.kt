package com.adriano.cronosync.server.persistence

import com.adriano.cronosync.server.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

fun createDataSource(config: DatabaseConfig): HikariDataSource = HikariDataSource(
    HikariConfig().apply {
        jdbcUrl = config.url
        username = config.user
        password = config.password
        maximumPoolSize = 5
    },
)

// Migração já aplicada nunca pode ser editada (o Flyway se recusa a subir): crie a próxima versão.
// baseline 1: bancos anteriores às migrações já têm a tabela da V1.
fun migrateDatabase(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .baselineVersion("1")
        .load()
        .migrate()
}
