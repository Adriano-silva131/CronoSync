package com.adriano.cronosync.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.adriano.cronosync.sync.data.SyncSession
import kotlin.concurrent.Volatile

class ConnectionWatcher(
    private val context: Context,
    private val session: SyncSession,
) {
    // O sistema avisa a rede atual logo ao registrar: isso não é uma troca de rede.
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
