package com.adriano.cronosync.server

import org.flywaydb.core.Flyway
import javax.sql.DataSource

/**
 * Deixa a estrutura do banco na versão que este servidor espera. Chamar ao subir, antes de usar
 * qualquer tabela.
 *
 * Cada mudança de estrutura é um arquivo em `src/main/resources/db/migration`, numerado
 * (`V1__...sql`, `V2__...sql`, ...). O Flyway anota na tabela `flyway_schema_history` quais já
 * rodaram neste banco e aplica só as que faltam, em ordem. Uma migração já aplicada NUNCA deve ser
 * editada (o Flyway confere e se recusa a subir): para mudar algo, crie o próximo número.
 *
 * baseline: bancos criados antes das migrações já têm a tabela `rooms`, mas não o histórico. Nesse
 * caso o Flyway registra a V1 como "já aplicada" (é exatamente essa tabela) e roda da V2 em diante.
 * Num banco vazio não há baseline: roda tudo desde a V1.
 */
fun migrateDatabase(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .baselineVersion("1")
        .load()
        .migrate()
}
