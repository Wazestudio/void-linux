package com.voidlinux.feature.linux

import android.content.Context
import com.voidlinux.core.common.Constants
import com.voidlinux.core.native.NativeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.StandardCharsets
import java.net.InetSocketAddress
import java.net.Socket

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
        val nativeLibraries = File(context.applicationInfo.nativeLibraryDir)
        val proot = File(nativeLibraries, "libproot.so")
        val missingRuntimeLibrary = listOf(
            "libproot_loader.so",
            "libtalloc.so",
            "libandroid-shmem.so"
        ).firstOrNull { !File(nativeLibraries, it).isFile }
        val shell = distro?.defaultShell ?: "/bin/bash"
        val loginShell = if (distroId == Constants.DISTRO_KALI) {
            "/usr/local/sbin/void-kali-login"
        } else {
            shell
        }
        if (distro == null || !rootfs.isDirectory ||
            !File(rootfs, shell.removePrefix("/")).isFile || !proot.isFile ||
            missingRuntimeLibrary != null
        ) {
            val message = when {
                distro == null -> "Distribution non prise en charge : $distroId"
                !rootfs.isDirectory -> "Kali n'est pas installé"
                !File(rootfs, shell.removePrefix("/")).isFile -> "Shell Kali introuvable : $shell"
                !proot.isFile -> "Binaire PRoot Android manquant dans l'APK"
                else -> "Bibliothèque PRoot absente de l'APK : $missingRuntimeLibrary"
            }
            onError(message)
            onExit(-1)
            return
        }

        val guestCommand = command ?: listOf(
            "/usr/bin/env",
            "-i",
            "HOME=/root",
            "USER=root",
            "LOGNAME=root",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "PS1=\\u@kali:\\w# ",
            "DEBIAN_FRONTEND=noninteractive",
            loginShell

        )
        val prootCommand = buildList {
            add(proot.absolutePath)
            addAll(
                listOf(
                    "--link2symlink",
                    "-0",
                    "-r", rootfs.absolutePath,
                    "-b", "/dev",
                    "-b", "/proc",
                    "-b", "/sys",
                    "-b", "${host.homeDir.absolutePath}:/root",
                    "-w", "/root"
                )
            )
            addAll(guestCommand)
        }

        var createdMaster = -1
        var createdSlave = -1
        try {
            val pty = NativeBridge.createPty(columns, rows)
                ?: throw IllegalStateException("Impossible de créer le pseudo-terminal")
            if (pty.size < 2) throw IllegalStateException("Descripteurs PTY invalides")

            val master = pty[0]
            val slave = pty[1]
            createdMaster = master
            createdSlave = slave
            val prootTemp = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
            val environment = System.getenv().toMutableMap().apply {
                put("LD_LIBRARY_PATH", context.applicationInfo.nativeLibraryDir)
                put(
                    "PROOT_LOADER",
                    File(context.applicationInfo.nativeLibraryDir, "libproot_loader.so").absolutePath
                )
                put("PROOT_TMP_DIR", prootTemp.absolutePath)
                applyNetworkRoute(this)
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
            onOutput("Kali Linux démarré (PRoot, sans privilèges root Android).\r\n")

            scope.launch {
                while (true) {
                    val bytes = NativeBridge.readFromPty(master, 4096) ?: break
                    onOutput(String(bytes, StandardCharsets.UTF_8))
                }
                val exitCode = NativeBridge.waitForProcess(pid)
                synchronized(this@LinuxSession) {
                    if (masterFd == master) masterFd = -1
                    if (processId == pid) processId = -1
                    running = false
                    NativeBridge.closePty(master)
                }
                if (!stopping) {
                    if (exitCode != 0) onError("Le processus Linux s'est arrêté (code $exitCode)")
                    onExit(exitCode)
                }
                scope.cancel()
            }
        } catch (e: Exception) {
            if (createdSlave >= 0) NativeBridge.closePty(createdSlave)
            if (createdMaster >= 0) NativeBridge.closePty(createdMaster)
            masterFd = -1
            if (processId > 0) {
                NativeBridge.killProcess(processId)
                processId = -1
            }
            running = false
            onError("Impossible de démarrer PRoot : ${e.message}")
            onExit(-1)
        } catch (e: LinkageError) {
            if (createdSlave >= 0) NativeBridge.closePty(createdSlave)
            if (createdMaster >= 0) NativeBridge.closePty(createdMaster)
            running = false
            onError("Le moteur natif du terminal est absent ou incompatible : ${e.message}")
            onExit(-1)
        }
    }

    /** Configure le proxy de la session Linux sans modifier les routes Android globales. */
    private fun applyNetworkRoute(environment: MutableMap<String, String>) {
        val prefs = context.getSharedPreferences("void_network", Context.MODE_PRIVATE)
        val route = prefs.getString(Constants.NETWORK_ROUTE_PREF, Constants.NETWORK_ROUTE_DIRECT)
            ?: Constants.NETWORK_ROUTE_DIRECT

        fun clearProxy() {
            listOf("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy")
                .forEach(environment::remove)
        }

        when (route) {
            Constants.NETWORK_ROUTE_DIRECT -> clearProxy()
            Constants.NETWORK_ROUTE_TOR -> {
                val port = Constants.TOR_SOCKS_PORT
                if (!isPortOpen("127.0.0.1", port)) {
                    throw IllegalStateException("Tor indisponible sur 127.0.0.1:$port")
                }
                setProxy(environment, "socks5h://127.0.0.1:$port")
            }
            Constants.NETWORK_ROUTE_SOCKS5 -> {
                val host = prefs.getString(Constants.NETWORK_PROXY_HOST_PREF, "127.0.0.1")
                    ?: "127.0.0.1"
                val port = prefs.getInt(Constants.NETWORK_PROXY_PORT_PREF, Constants.TOR_SOCKS_PORT)
                require(port in 1..65535) { "Port SOCKS5 invalide : $port" }
                if (!isPortOpen(host, port)) {
                    throw IllegalStateException("Proxy SOCKS5 indisponible sur $host:$port")
                }
                setProxy(environment, "socks5h://$host:$port")
            }
            else -> throw IllegalStateException("Mode réseau inconnu : $route")
        }
    }

    private fun setProxy(environment: MutableMap<String, String>, proxy: String) {
        environment["ALL_PROXY"] = proxy
        environment["all_proxy"] = proxy
        environment["HTTP_PROXY"] = proxy
        environment["HTTPS_PROXY"] = proxy
        environment["http_proxy"] = proxy
        environment["https_proxy"] = proxy
        environment["NO_PROXY"] = "127.0.0.1,localhost"
        environment["no_proxy"] = "127.0.0.1,localhost"
    }

    private fun isPortOpen(host: String, port: Int): Boolean {
        return try {
            Socket().use { it.connect(InetSocketAddress(host, port), 1500) }
            true
        } catch (_: Exception) {
            false
        }
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
        if (processId > 0) NativeBridge.killProcess(processId)
        if (masterFd < 0) scope.cancel()
    }

    @Synchronized
    fun isRunning(): Boolean = running
}
