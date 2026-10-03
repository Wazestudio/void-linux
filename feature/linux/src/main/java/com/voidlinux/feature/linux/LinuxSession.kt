package com.voidlinux.feature.linux

import android.content.Context
import com.voidlinux.core.common.Constants
import com.voidlinux.core_native.NativeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

class LinuxSession(
    private val context: Context,
    private val distroId: String,
    private val onOutput: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onExit: (Int) -> Unit
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val host = LinuxHost(context)
    private var masterFd = -1
    private var processId = -1
    private var columns = 80
    private var rows = 24
    @Volatile
    private var stopping = false
    private var running = false

    @Synchronized
    fun start(command: List<String>? = null) {
        if (running) return

        val distro = DistroCatalog.byId(distroId)
        val rootfs = host.rootfsFor(distroId)
        val nativeLibraries = host.nativeLibsDir
        val proot = File(nativeLibraries, "libproot.so")
        val loader = host.prootLoaderFile()
        val requiredLibraries = listOf(
            "libproot.so",
            "libproot_loader.so",
            "libtalloc.so",
            "libandroid-shmem.so"
        )
        val missingRuntimeLibrary = requiredLibraries.firstOrNull {
            if (it == "libproot_loader.so") {
                !loader.isFile || loader.length() == 0L
            } else {
                !File(nativeLibraries, it).isFile || File(nativeLibraries, it).length() == 0L
            }
        }

        val loginShell = distro?.defaultShell ?: "/bin/bash"
        if (distro == null || !rootfs.isDirectory ||
            !File(rootfs, loginShell.removePrefix("/")).isFile ||
            !proot.isFile || !loader.isFile || missingRuntimeLibrary != null
        ) {
            val message = when {
                distro == null -> "Distribution non prise en charge : $distroId"
                !rootfs.isDirectory -> "Kali n'est pas installé"
                !File(rootfs, loginShell.removePrefix("/")).isFile ->
                    "Shell Kali introuvable : $loginShell"
                !proot.isFile -> "Binaire PRoot Android manquant dans l'APK"
                !loader.isFile -> "Loader PRoot Android manquant dans l'APK"
                else -> "Bibliothèque PRoot absente de l'APK : $missingRuntimeLibrary"
            }
            onError(message)
            onExit(-1)
            return
        }

        var createdMaster = -1
        var createdSlave = -1
        try {
            val networkVariables = LinuxNetworkRoute.variables(context)
            val shellCommand = command ?: listOf(
                loginShell,
                "-i"
            )

            val prootCommand = buildList {
                add(proot.absolutePath)
                addAll(
                    listOf(
                        "--link2symlink",
                        "-0",
                        "-r", rootfs.absolutePath,
                        "-w", "/root",
                        "-b", "/dev",
                        "-b", "/proc",
                        "-b", "/sys",
                        "-b", "${host.homeDir.absolutePath}:/root",
                        "-l", loader.absolutePath
                    )
                )
                add("--")
                addAll(
                    listOf(
                        "/usr/bin/env",
                        "-i",
                        "HOME=/root",
                        "USER=root",
                        "LOGNAME=root",
                        "SHELL=$loginShell",
                        "TERM=xterm-256color",
                        "LANG=C.UTF-8",
                        "LC_ALL=C.UTF-8",
                        "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                        "PS1=\\u@kali:\\w# "
                    )
                )
                networkVariables.forEach { (name, value) -> add("$name=$value") }
                addAll(shellCommand)
            }

            val pty = NativeBridge.createPty(columns, rows)
                ?: throw IllegalStateException("Impossible de créer le pseudo-terminal")
            if (pty.size < 2 || pty[0] < 0 || pty[1] < 0) {
                throw IllegalStateException("Descripteurs PTY invalides")
            }

            val master = pty[0]
            val slave = pty[1]
            createdMaster = master
            createdSlave = slave

            val prootTemp = File(host.tmpDir, "proot-tmp").apply {
                if (!exists() && !mkdirs()) {
                    throw IllegalStateException("Impossible de créer le répertoire temporaire PRoot")
                }
                setReadable(true, true)
                setWritable(true, true)
                setExecutable(true, true)
            }

            val environment = System.getenv().toMutableMap().apply {
                put("LD_LIBRARY_PATH", nativeLibraries.absolutePath)
                put("PROOT_LOADER", loader.absolutePath)
                put("PROOT_TMP_DIR", prootTemp.absolutePath)
                putAll(networkVariables)
            }

            val pid = NativeBridge.execInPty(
                master,
                slave,
                prootCommand.toTypedArray(),
                environment.map { (key, value) -> "$key=$value" }.toTypedArray(),
                host.homeDir.absolutePath
            )

            NativeBridge.closePty(slave)
            createdSlave = -1

            if (pid < 0) {
                NativeBridge.closePty(master)
                createdMaster = -1
                throw IllegalStateException("Impossible de lancer le processus PRoot")
            }

            masterFd = master
            createdMaster = -1
            processId = pid
            stopping = false
            running = true
            onOutput("Kali Linux démarré.\r\n")

            scope.launch {
                val decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)

                try {
                    while (true) {
                        val bytes = NativeBridge.readFromPty(master, 16 * 1024) ?: break
                        if (bytes.isNotEmpty()) {
                            val text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                            if (text.isNotEmpty()) onOutput(text)
                        }
                    }
                } finally {
                    val exitCode = NativeBridge.waitForProcess(pid)
                    synchronized(this@LinuxSession) {
                        if (masterFd == master) masterFd = -1
                        if (processId == pid) processId = -1
                        running = false
                        NativeBridge.closePty(master)
                    }
                    if (!stopping) {
                        if (exitCode != 0) {
                            onError("Le processus Linux s'est arrêté (code $exitCode)")
                        }
                        onExit(exitCode)
                    }
                }
            }
        } catch (e: LinkageError) {
            cleanupFailedStart(createdMaster, createdSlave)
            onError("Le moteur natif du terminal est absent ou incompatible : ${e.message}")
            onExit(-1)
        } catch (e: Exception) {
            cleanupFailedStart(createdMaster, createdSlave)
            onError("Impossible de démarrer PRoot : ${e.message ?: "erreur inconnue"}")
            onExit(-1)
        }
    }

    @Synchronized
    private fun cleanupFailedStart(createdMaster: Int, createdSlave: Int) {
        if (createdSlave >= 0) NativeBridge.closePty(createdSlave)
        if (createdMaster >= 0) NativeBridge.closePty(createdMaster)
        if (processId > 0) NativeBridge.killProcess(processId)
        masterFd = -1
        processId = -1
        running = false
    }

    @Synchronized
    fun write(data: String) {
        if (!running || masterFd < 0) return
        try {
            val bytes = data.replace("\n", "\r").toByteArray(StandardCharsets.UTF_8)
            var offset = 0
            while (offset < bytes.size) {
                val written = NativeBridge.writeToPty(
                    masterFd,
                    bytes.copyOfRange(offset, bytes.size)
                )
                if (written <= 0) throw IllegalStateException("PTY indisponible")
                offset += written
            }
        } catch (e: Exception) {
            onError("Erreur d'écriture dans le terminal : ${e.message}")
        }
    }

    @Synchronized
    fun resize(cols: Int, rows: Int) {
        columns = cols.coerceAtLeast(1)
        this.rows = rows.coerceAtLeast(1)
        if (masterFd >= 0) NativeBridge.resizePty(masterFd, columns, this.rows)
    }

    @Synchronized
    fun stop() {
        stopping = true
        running = false

        val pid = processId
        val fd = masterFd
        processId = -1
        masterFd = -1

        if (pid > 0) NativeBridge.killProcess(pid)
        if (fd >= 0) NativeBridge.closePty(fd)
    }

    @Synchronized
    fun isRunning(): Boolean = running
}
