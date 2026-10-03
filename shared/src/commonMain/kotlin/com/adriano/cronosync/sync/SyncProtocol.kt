package com.adriano.cronosync.sync

import com.adriano.cronosync.pomodoro.domain.Pomodoro
import com.adriano.cronosync.pomodoro.domain.PomodoroCommand
import com.adriano.cronosync.pomodoro.domain.handle
import com.adriano.cronosync.stopwatch.domain.Stopwatch
import com.adriano.cronosync.stopwatch.domain.StopwatchCommand
import com.adriano.cronosync.stopwatch.domain.handle
import com.adriano.cronosync.timer.domain.Timer
import com.adriano.cronosync.timer.domain.TimerCommand
import com.adriano.cronosync.timer.domain.handle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Protocolo de sincronização: as mensagens trocadas pelo WebSocket entre aparelhos e servidor.
 *
 * Fica no shared para que cliente e servidor usem EXATAMENTE as mesmas classes. Os @SerialName
 * são o contrato da rede: podem renomear a classe Kotlin à vontade, mas mudar um SerialName
 * quebra a comunicação com aparelhos que ainda estão na versão antiga do app.
 */

/**
 * Tudo o que uma sala sincroniza. O servidor é o dono deste estado.
 *
 * [version] sobe 1 a cada mudança aplicada pelo servidor: é como os aparelhos sabem se o que
 * estão vendo é atual ("versão esperada") e quando uma previsão local já foi confirmada.
 * [updatedAtMillis] é o instante (relógio do servidor) da última mudança.
 */
@Serializable
data class RoomState(
    val stopwatch: Stopwatch = Stopwatch(),
    val timer: Timer = Timer(),
    val pomodoro: Pomodoro = Pomodoro(),
    val version: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

/** Um comando para um dos modos da sala. */
@Serializable
sealed interface RoomCommand {
    @Serializable @SerialName("stopwatch")
    data class StopwatchCmd(val command: StopwatchCommand) : RoomCommand

    @Serializable @SerialName("timer")
    data class TimerCmd(val command: TimerCommand) : RoomCommand

    @Serializable @SerialName("pomodoro")
    data class PomodoroCmd(val command: PomodoroCommand) : RoomCommand
}

/** Aplica um comando ao modo certo. Não mexe em [RoomState.version] (isso é papel do servidor). */
fun RoomState.handle(command: RoomCommand, atMillis: Long): RoomState = when (command) {
    is RoomCommand.StopwatchCmd -> copy(stopwatch = stopwatch.handle(command.command, atMillis))
    is RoomCommand.TimerCmd -> copy(timer = timer.handle(command.command, atMillis))
    is RoomCommand.PomodoroCmd -> copy(pomodoro = pomodoro.handle(command.command, atMillis))
}

/**
 * Instante em que o servidor aplica um comando: o do TOQUE ([requestedAtMillis], já no relógio do
 * servidor), e não o da chegada — assim a latência da rede não "atrasa" uma pausa. Com limites:
 * - nunca no futuro (relógio do aparelho mal ajustado não adianta nada);
 * - nunca antes da última mudança da sala (não dá para "reescrever" o que já aconteceu);
 * - no máximo [MAX_BACKDATE_MILLIS] no passado (ninguém "volta no tempo" de propósito).
 */
fun effectiveCommandTime(requestedAtMillis: Long, nowMillis: Long, lastChangeAtMillis: Long): Long {
    val earliest = maxOf(lastChangeAtMillis, nowMillis - MAX_BACKDATE_MILLIS)
    return requestedAtMillis.coerceIn(minOf(earliest, nowMillis), nowMillis)
}

const val MAX_BACKDATE_MILLIS = 5_000L

/** Aparelho → servidor. */
@Serializable
sealed interface ClientMessage {
    /**
     * Um comando da pessoa.
     * @param id identifica o comando, para a resposta ([ServerMessage.CommandResult]) e a previsão local.
     * @param atMillis instante do toque, no relógio do servidor (ver AlignedClock).
     * @param expectedVersion última versão da sala que o aparelho recebeu: se OUTRO aparelho mudou
     *   a sala depois disso, o comando é recusado — a decisão foi tomada com informação velha.
     */
    @Serializable @SerialName("command")
    data class Command(
        val id: String,
        val command: RoomCommand,
        val atMillis: Long,
        val expectedVersion: Long,
    ) : ClientMessage

    /**
     * Pergunta "que horas são aí?". Com a resposta ([ServerMessage.Pong]) o aparelho calcula a
     * diferença entre o relógio dele e o do servidor — necessário porque os instantes do estado
     * (ex.: runningSinceMillis) estão no relógio do servidor.
     */
    @Serializable @SerialName("ping")
    data class Ping(val clientTimeMillis: Long) : ClientMessage
}

/** Por que o servidor recusou um comando. */
@Serializable
enum class RejectionReason {
    /** Outro aparelho mudou a sala depois da versão que este aparelho estava vendo. */
    @SerialName("stale") Stale,

    /** Comandos demais em pouco tempo vindos desta conexão (proteção contra scripts; ver servidor). */
    @SerialName("too_many_commands") TooManyCommands,
}

/** Servidor → aparelho. */
@Serializable
sealed interface ServerMessage {
    /** Estado completo da sala. Enviado ao conectar e depois de cada mudança. */
    @Serializable @SerialName("state")
    data class State(val room: RoomState) : ServerMessage

    /**
     * Resposta, só para quem enviou, ao comando [id]. [version]: versão da sala logo após processar
     * o comando — a previsão local é descartada quando o aparelho já recebeu um estado com essa versão.
     */
    @Serializable @SerialName("command_result")
    data class CommandResult(
        val id: String,
        val accepted: Boolean,
        val version: Long,
        val reason: RejectionReason? = null,
    ) : ServerMessage

    @Serializable @SerialName("pong")
    data class Pong(val clientTimeMillis: Long, val serverTimeMillis: Long) : ServerMessage
}

/** Resposta de `POST /rooms`: o código da sala recém-criada, já formatado ("ABCD-EFGH"). */
@Serializable
data class CreateRoomResponse(val roomId: String)

/**
 * Códigos de fechamento do WebSocket usados pelo servidor para recusar uma conexão.
 * A faixa 4000–4999 é reservada pelo padrão WebSocket para uso das aplicações.
 */
object SyncCloseCodes {
    const val INVALID_ROOM_CODE: Short = 4400
    const val ROOM_NOT_FOUND: Short = 4404

    /**
     * Conexões simultâneas demais vindas do mesmo endereço IP (proteção contra scripts). Não é uma
     * recusa definitiva: o app trata como queda e tenta de novo, com espera crescente.
     */
    const val TOO_MANY_CONNECTIONS: Short = 4429
}

/**
 * Configuração JSON compartilhada. ignoreUnknownKeys: um app antigo ignora campos novos que um
 * servidor mais novo passar a enviar, em vez de falhar.
 */
val SyncJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}
