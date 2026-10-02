package com.voidlinux.feature.linux

import android.content.Context
import com.voidlinux.core.common.Constants
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

internal object LinuxNetworkRoute {

    fun variables(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences("void_network", Context.MODE_PRIVATE)
        val route = prefs.getString(Constants.NETWORK_ROUTE_PREF, Constants.NETWORK_ROUTE_DIRECT)
            ?: Constants.NETWORK_ROUTE_DIRECT

        return when (route) {
            Constants.NETWORK_ROUTE_DIRECT -> emptyMap()
            Constants.NETWORK_ROUTE_TOR -> {
                val port = Constants.TOR_SOCKS_PORT
                requireProxyAvailable("127.0.0.1", port, "Tor")
                proxyVariables("socks5h://127.0.0.1:$port")
            }
            Constants.NETWORK_ROUTE_SOCKS5 -> {
                val host = prefs.getString(Constants.NETWORK_PROXY_HOST_PREF, "127.0.0.1")
                    ?: "127.0.0.1"
                val port = prefs.getInt(Constants.NETWORK_PROXY_PORT_PREF, Constants.TOR_SOCKS_PORT)
                if (port !in 1..65535) throw IOException("Port SOCKS5 invalide : $port")
                requireProxyAvailable(host, port, "Proxy SOCKS5")
                proxyVariables("socks5h://$host:$port")
            }
            else -> throw IOException("Mode réseau inconnu : $route")
        }
    }

    private fun proxyVariables(proxy: String) = mapOf(
        "ALL_PROXY" to proxy,
        "all_proxy" to proxy,
        "HTTP_PROXY" to proxy,
        "HTTPS_PROXY" to proxy,
        "http_proxy" to proxy,
        "https_proxy" to proxy,
        "NO_PROXY" to "127.0.0.1,localhost",
        "no_proxy" to "127.0.0.1,localhost"
    )

    private fun requireProxyAvailable(host: String, port: Int, name: String) {
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), 1500) }
        } catch (e: IOException) {
            throw IOException("$name indisponible sur $host:$port", e)
        }
    }
}
