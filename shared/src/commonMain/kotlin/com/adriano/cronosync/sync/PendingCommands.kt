package com.adriano.cronosync.sync

/**
 * Previsões locais: comandos já enviados que o servidor ainda não confirmou.
 *
 * O que a tela mostra = estado OFICIAL do servidor + estes comandos reaplicados por cima ([predict]).
 * Assim o toque tem efeito na hora, mas o servidor continua sendo a fonte da verdade:
 * - aceito: a previsão sai quando chega um estado com a versão em que o comando foi aplicado
 *   (aí o próprio estado oficial já contém o efeito — sem "piscar" para trás);
 * - recusado: TODAS as previsões saem (as seguintes foram feitas em cima da recusada), e a tela
 *   volta ao estado oficial;
 * - sem resposta em [timeoutMillis] (ex.: a mensagem se perdeu): a previsão sai.
 *
 * Não é thread-safe: a SyncSession acessa sob um Mutex.
 */
internal class PendingCommands(private val timeoutMillis: Long = 5_000L) {

    private class Pending(
        val id: String,
        val command: RoomCommand,
        val atMillis: Long,
        val sentAtLocalMillis: Long,
        var confirmedAtVersion: Long? = null,
    )

    private val items = mutableListOf<Pending>()

    val isEmpty: Boolean get() = items.isEmpty()

    fun add(message: ClientMessage.Command, sentAtLocalMillis: Long) {
        items += Pending(message.id, message.command, message.atMillis, sentAtLocalMillis)
    }

    /** @return true se o comando foi recusado (as previsões foram todas descartadas). */
    fun onResult(result: ServerMessage.CommandResult, latestServerVersion: Long): Boolean {
        if (!result.accepted) {
            items.clear()
            return true
        }
        items.firstOrNull { it.id == result.id }?.confirmedAtVersion = result.version
        onState(latestServerVersion)
        return false
    }

    /** Chegou um estado oficial: sai o que ele já contém. */
    fun onState(version: Long) {
        items.removeAll { pending -> pending.confirmedAtVersion?.let { it <= version } ?: false }
    }

    /** @return true se alguma previsão expirou (a tela precisa ser atualizada). */
    fun expire(nowLocalMillis: Long): Boolean =
        items.removeAll { nowLocalMillis - it.sentAtLocalMillis > timeoutMillis }

    fun clear() = items.clear()

    fun predict(server: RoomState): RoomState =
        items.fold(server) { room, pending -> room.handle(pending.command, pending.atMillis) }
}
