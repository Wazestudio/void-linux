package com.voidlinux.feature.terminal

import android.content.Context
import com.voidlinux.core.common.Constants
import com.voidlinux.feature.linux.LinuxSession

class TerminalSession(
    private val context: Context,
    private val buffer: TerminalBuffer,
    onOutput: (String) -> Unit,
    onError: (String) -> Unit,
    onExit: (Int) -> Unit
) {

    private val linuxSession = LinuxSession(
        context = context,
        distroId = Constants.DISTRO_KALI,
        onOutput = onOutput,
        onError = onError,
        onExit = onExit
    )

    val isRunning: Boolean
        get() = linuxSession.isRunning()

    /** Définit le routage de la prochaine session Linux. */
    fun setNetworkRoute(route: String, proxyHost: String? = null, proxyPort: Int? = null) {
        require(route in setOf(
            Constants.NETWORK_ROUTE_DIRECT,
            Constants.NETWORK_ROUTE_TOR,
            Constants.NETWORK_ROUTE_SOCKS5
        ))
        val editor = context.getSharedPreferences("void_network", Context.MODE_PRIVATE).edit()
            .putString(Constants.NETWORK_ROUTE_PREF, route)
        if (proxyHost != null) editor.putString(Constants.NETWORK_PROXY_HOST_PREF, proxyHost)
        if (proxyPort != null) editor.putInt(Constants.NETWORK_PROXY_PORT_PREF, proxyPort)
        editor.apply()
    }

    fun start() {
        linuxSession.resize(buffer.cols, buffer.rows)
        linuxSession.start()
    }

    fun write(data: String) = linuxSession.write(data)

    fun resize(cols: Int, rows: Int) {
        buffer.resize(cols, rows)
        linuxSession.resize(cols, rows)
    }

    fun stop() = linuxSession.stop()
}
