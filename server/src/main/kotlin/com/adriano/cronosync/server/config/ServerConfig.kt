package com.adriano.cronosync.server.config

data class ServerConfig(
    val port: Int,
    val database: DatabaseConfig,
    val behindProxy: Boolean,
) {
    companion object {
        fun fromEnvironment(
            environment: (String) -> String? = System::getenv,
            development: Boolean = System.getProperty("io.ktor.development") == "true",
        ): ServerConfig {
            fun required(name: String, developmentDefault: String): String =
                environment(name) ?: developmentDefault.takeIf { development }
                    ?: error("Defina a variável de ambiente $name (fora do modo de desenvolvimento ela é obrigatória)")

            return ServerConfig(
                port = environment("PORT")?.toIntOrNull() ?: 8080,
                database = DatabaseConfig(
                    url = required("CRONOSYNC_DB_URL", developmentDefault = "jdbc:postgresql://localhost:5434/cronosync"),
                    user = required("CRONOSYNC_DB_USER", developmentDefault = "cronosync"),
                    password = required("CRONOSYNC_DB_PASSWORD", developmentDefault = "cronosync"),
                ),
                behindProxy = environment("CRONOSYNC_BEHIND_PROXY") == "true",
            )
        }
    }
}

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
) {
    override fun toString() = "DatabaseConfig(url=$url, user=$user, password=***)"
}
