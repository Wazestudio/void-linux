package com.voidlinux.core_native

object NativeBridge {

    init {
        System.loadLibrary("voidlinux_jni")
    }

    // --- PTY ---

    external fun createPty(cols: Int, rows: Int): IntArray?

    external fun resizePty(fd: Int, cols: Int, rows: Int)

    external fun closePty(fd: Int)

    // --- Terminal ---

    external fun execInPty(
        masterFd: Int,
        slaveFd: Int,
        arguments: Array<String>,
        environment: Array<String>,
        workingDirectory: String
    ): Int

    external fun waitForProcess(pid: Int): Int

    external fun killProcess(pid: Int)

    external fun writeToPty(fd: Int, data: ByteArray): Int

    external fun readFromPty(fd: Int, maxBytes: Int): ByteArray?
}