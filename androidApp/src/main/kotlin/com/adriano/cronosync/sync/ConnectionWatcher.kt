package com.adriano.cronosync.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlin.concurrent.Volatile

/**
 * Avisa a [SyncSession] dos momentos em que vale tentar reconectar JÁ, em vez de esperar o
 * intervalo crescente de tentativas (até 15 s):
 * - a rede padrão do aparelho mudou (Wi-Fi → dados, ou voltou de "sem rede"): a conexão antiga
 *   provavelmente morreu, então derrubamos e reconectamos pela rede nova;
 * - o app voltou para a frente: quem abriu o app quer ver o estado atualizado agora.
 */
class ConnectionWatcher(
    private val context: Context,
    private val session: SyncSession,
) {
    /** Última rede vista. O sistema avisa a rede atual logo ao registrar — isso não é "troca". */
    @Volatile
    private var currentNetwork: Network? = null

    fun start() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val previous = currentNetwork
                    currentNetwork = network
                    if (previous != null && previous != network) session.onNetworkChanged()
                    // Voltou de "sem rede nenhuma": não há conexão viva para derrubar, só tentar já.
                    if (previous == null) session.reconnectNow()
                }

                override fun onLost(network: Network) {
                    if (network == currentNetwork) currentNetwork = null
                }
            },
        )

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = session.reconnectNow()
            },
        )
    }
}
