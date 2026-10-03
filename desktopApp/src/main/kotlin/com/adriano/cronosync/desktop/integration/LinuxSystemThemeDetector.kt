package com.adriano.cronosync.desktop.integration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Tema do sistema no Linux, pelo portal do freedesktop (`org.freedesktop.appearance` →
 * `color-scheme`), padrão seguido pelo GNOME e pelo KDE. Valores: 0 = sem preferência (claro),
 * 1 = prefere escuro, 2 = prefere claro. Sem portal, tenta o `gsettings` do GNOME.
 *
 * Também acompanha mudanças AO VIVO: um processo `gdbus monitor` escuta o sinal SettingChanged do
 * portal, e a janela troca de tema no mesmo instante em que o usuário troca no sistema.
 */
class LinuxSystemThemeDetector(scope: CoroutineScope) : SystemThemeDetector {

    private val _isDark = MutableStateFlow(readPortal() ?: readGsettings())
    override val isDark: StateFlow<Boolean?> = _isDark.asStateFlow()

    init {
        // Leitura de processo é bloqueante: fica numa thread de I/O, fora da interface.
        scope.launch(Dispatchers.IO) { watchPortal() }
    }

    private fun watchPortal() {
        val process = try {
            ProcessBuilder("gdbus", "monitor", "--session", "--dest", PORTAL_DEST, "--object-path", PORTAL_PATH)
                .redirectErrorStream(true)
                .start()
        } catch (e: IOException) {
            return // sem gdbus: fica com a leitura inicial
        }
        // O monitor é um processo à parte: precisa morrer junto com o app.
        Runtime.getRuntime().addShutdownHook(Thread { process.destroy() })
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line -> parseColorSchemeChange(line)?.let { _isDark.value = it } }
        }
    }

    companion object {
        private const val PORTAL_DEST = "org.freedesktop.portal.Desktop"
        private const val PORTAL_PATH = "/org/freedesktop/portal/desktop"
        private val UINT32 = Regex("""uint32 (\d+)""")

        /** "0/1/2" do portal → escuro? (null se não reconhecido). */
        private fun colorSchemeIsDark(value: Int): Boolean? = when (value) {
            1 -> true
            0, 2 -> false
            else -> null
        }

        /** Resposta de `Settings.ReadOne`, ex.: "(<uint32 1>,)". Também aceita a de `Read`, "(<<uint32 1>>,)". */
        internal fun parsePortalReply(output: String): Boolean? =
            UINT32.find(output)?.groupValues?.get(1)?.toIntOrNull()?.let(::colorSchemeIsDark)

        /** Linha do `gdbus monitor`; só interessa o SettingChanged de appearance/color-scheme. */
        internal fun parseColorSchemeChange(line: String): Boolean? {
            if ("SettingChanged" !in line || "'org.freedesktop.appearance'" !in line || "'color-scheme'" !in line) return null
            return parsePortalReply(line)
        }

        /** Saída de `gsettings get org.gnome.desktop.interface color-scheme`, ex.: "'prefer-dark'". */
        internal fun parseGsettings(output: String): Boolean? = when (output.trim().trim('\'')) {
            "prefer-dark" -> true
            "prefer-light", "default" -> false
            else -> null
        }

        private fun readPortal(): Boolean? =
            run("gdbus", "call", "--session", "--dest", PORTAL_DEST, "--object-path", PORTAL_PATH,
                "--method", "org.freedesktop.portal.Settings.ReadOne", "org.freedesktop.appearance", "color-scheme")
                ?.let(::parsePortalReply)

        private fun readGsettings(): Boolean? =
            run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")?.let(::parseGsettings)

        /** Roda um comando curto e devolve a saída; null se não existir, falhar ou demorar demais. */
        private fun run(vararg command: String): String? = try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0) {
                process.inputStream.bufferedReader().readText()
            } else {
                process.destroy()
                null
            }
        } catch (e: IOException) {
            null
        }
    }
}
