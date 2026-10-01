package com.voidlinux.feature.terminal

import android.content.Context
import com.voidlinux.core.common.Constants
import com.voidlinux.feature.linux.LinuxSession

class TerminalSession(
    context: Context,
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
