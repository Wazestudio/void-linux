package com.voidlinux.feature.linux

import android.app.NotificationManager
import android.content.Context
import android.system.Os
import androidx.core.app.NotificationCompat
import com.voidlinux.core.common.Constants
import com.voidlinux.core.common.VoidResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.max

class LinuxInstaller(
    private val context: Context,
    private val host: LinuxHost
) {

    private val notifier = InstallationNotifier(context)

    suspend fun install(
        distro: DistroCatalog.Distro,
        onProgress: (Int) -> Unit = {}
    ): VoidResult<File> = withContext(Dispatchers.IO) {
        notifier.showStart(distro.displayName)
        val stagingDir = File(host.rootfsDir, "${distro.id}.installing")
        val backupDir = File(host.rootfsDir, "${distro.id}.backup")
        val archive = File(host.tmpDir, distro.archiveName)
        val partialArchive = File(host.tmpDir, "${distro.archiveName}.part")

        try {
            host.tmpDir.mkdirs()
            if (!archive.isFile || archive.length() == 0L) {
                if (!copyBundledRootfsIfAvailable(distro, partialArchive)) {
                    downloadFile(distro.url, partialArchive) { progress ->
                    notifier.update(distro.displayName, progress / 2)
                    onProgress(progress / 2)
                    }
                    if (!partialArchive.renameTo(archive)) {
                        throw IOException("Impossible de finaliser le téléchargement")
                    }
                } else if (!partialArchive.renameTo(archive)) {
                    throw IOException("Impossible de préparer le rootfs intégré")
                }
            }

            stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            extractRootfs(archive, stagingDir) { progress ->
                val overall = 50 + progress / 2
                notifier.update(distro.displayName, overall)
                onProgress(overall)
            }

            if (!File(stagingDir, "bin/bash").exists() ||
                !File(stagingDir, "usr/bin/apt-get").exists()
            ) {
                throw IOException("L'archive téléchargée ne contient pas un rootfs Kali valide")
            }

            if (backupDir.exists()) backupDir.deleteRecursively()
            val targetDir = host.rootfsFor(distro.id)
            if (targetDir.exists() && !targetDir.renameTo(backupDir)) {
                throw IOException("Impossible de sauvegarder l'installation Linux existante")
            }
            if (!stagingDir.renameTo(targetDir)) {
                if (backupDir.exists()) backupDir.renameTo(targetDir)
                throw IOException("Impossible de finaliser l'installation Linux")
            }
            installKaliToolBootstrap(targetDir)
            backupDir.deleteRecursively()
            archive.delete()
            onProgress(100)
            notifier.showComplete(distro.displayName)
            VoidResult.Success(targetDir)
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            partialArchive.delete()
            archive.delete()
            notifier.showError(distro.displayName, e.message ?: "Erreur inconnue")
            VoidResult.Error("Échec de l'installation Kali", e)
        }
    }

    private fun copyBundledRootfsIfAvailable(
        distro: DistroCatalog.Distro,
        target: File
    ): Boolean {
        if (distro.id != Constants.DISTRO_KALI) return false
        val assetName = "kali-arm64.tar.xz"
        return try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output, 64 * 1024)
                }
            }
            target.isFile && target.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    private fun downloadFile(
        url: String,
        target: File,
        onProgress: (Int) -> Unit
    ) {
        require(url.startsWith("https://")) { "Le téléchargement du rootfs doit utiliser HTTPS" }
        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }

        try {
            connection.connect()
            if (connection.url.protocol != "https") {
                throw IOException("Le serveur a tenté un téléchargement non sécurisé")
            }
            if (connection.responseCode !in 200..299) {
                throw IOException("Téléchargement refusé : HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            val requiredSpace = if (total in 1L..(Long.MAX_VALUE / 8)) {
                total * 8
            } else {
                Long.MAX_VALUE
            }
            val availableSpace = android.os.StatFs(context.filesDir.absolutePath).availableBytes
            if (availableSpace < requiredSpace) {
                throw IOException("Espace insuffisant : prévois environ 8 fois la taille de l'archive")
            }

            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (total > 0) onProgress(((downloaded * 100) / total).toInt())
                    }
                }
            }
            if (downloaded == 0L || (total > 0 && downloaded != total)) {
                throw IOException("Téléchargement incomplet du rootfs Kali")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractRootfs(
        archive: File,
        targetDir: File,
        onProgress: (Int) -> Unit
    ) {
        val archiveLength = archive.length().coerceAtLeast(1L)
        val availableSpace = android.os.StatFs(context.filesDir.absolutePath).availableBytes
        val maximumRootfsBytes = availableSpace - CountingInputStream.MINIMUM_FREE_SPACE_BYTES
        if (maximumRootfsBytes <= 0L) {
            throw IOException("Espace insuffisant pour extraire Kali")
        }
        var extractedBytes = 0L
        var completedEntries = 0
        val directoryModes = mutableListOf<Pair<File, Int>>()
        val symbolicLinks = mutableListOf<Pair<File, String>>()
        val hardLinks = mutableListOf<Pair<File, String>>()
        val countingStream = CountingInputStream(archive.inputStream())

        XZCompressorInputStream(countingStream).use { xz ->
            TarArchiveInputStream(xz).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val guestPath = entry.name.trimStart('.', '/')
                    val output = resolveEntry(targetDir, entry)
                    if (guestPath == "dev" || guestPath.startsWith("dev/")) {
                        entry = tar.nextEntry
                        continue
                    }
                    when {
                        entry.isDirectory -> {
                            output.mkdirs()
                            directoryModes += output to entry.mode
                        }
                        entry.isSymbolicLink -> {
                            symbolicLinks += output to entry.linkName
                        }
                        entry.isLink -> {
                            hardLinks += output to entry.linkName
                        }
                        entry.isFile -> {
                            output.parentFile?.mkdirs()
                            FileOutputStream(output).use { outputStream ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = tar.read(buffer)
                                    if (count < 0) break
                                    if (count > maximumRootfsBytes - extractedBytes) {
                                        throw IOException("Le rootfs Kali dépasse l'espace libre disponible")
                                    }
                                    outputStream.write(buffer, 0, count)
                                    extractedBytes += count
                                }
                            }
                            applyMode(output, entry.mode)
                        }
                        else -> throw IOException("Type d'entrée rootfs non pris en charge : ${entry.name}")
                    }

                    completedEntries++
                    onProgress(
                        max(
                            ((countingStream.bytesRead * 100) / archiveLength).toInt(),
                            (completedEntries / 100).coerceAtMost(20)
                        ).coerceIn(0, 100)
                    )
                    entry = tar.nextEntry
                }
            }
        }

        directoryModes.asReversed().forEach { (directory, mode) ->
            applyMode(directory, mode, isDirectory = true)
        }
        File(targetDir, "dev").mkdirs()
        val pendingHardLinks = hardLinks.toMutableList()
        while (pendingHardLinks.isNotEmpty()) {
            var linkedAny = false
            val iterator = pendingHardLinks.iterator()
            while (iterator.hasNext()) {
                val (link, target) = iterator.next()
                val linkTarget = resolveArchivePath(targetDir, target)
                if (!linkTarget.isFile) continue
                link.parentFile?.mkdirs()
                Os.link(linkTarget.absolutePath, link.absolutePath)
                iterator.remove()
                linkedAny = true
            }
            if (!linkedAny) {
                throw IOException("Cible de lien physique absente dans l'archive")
            }
        }
        val createdLinks = mutableSetOf<String>()
        symbolicLinks.forEach { (link, target) ->
            var parent = link.parentFile
            while (parent != null && parent.toPath().startsWith(targetDir.toPath())) {
                if (parent.absolutePath in createdLinks) {
                    throw IOException("Lien symbolique parent non pris en charge dans le rootfs")
                }
                parent = parent.parentFile
            }
            if (link.exists()) {
                if (!link.isDirectory || link.list()?.isNotEmpty() == true || !link.delete()) {
                    throw IOException("Collision de chemin de lien symbolique dans le rootfs")
                }
            }
            link.parentFile?.mkdirs()
            Os.symlink(target, link.absolutePath)
            createdLinks += link.absolutePath
        }
    }

    private fun resolveEntry(root: File, entry: TarArchiveEntry): File {
        if (entry.name.startsWith('/')) throw IOException("Chemin absolu interdit dans l'archive")
        var normalized = entry.name.trimEnd('/')
        while (normalized.startsWith("./")) normalized = normalized.removePrefix("./")
        if (normalized.isEmpty() || normalized == ".") {
            if (entry.isDirectory) return root
            throw IOException("Entrée rootfs invalide")
        }
        return resolveArchivePath(root, entry.name)
    }

    private fun resolveArchivePath(root: File, path: String): File {
        var safePath = path.trimEnd('/')
        while (safePath.startsWith("./")) safePath = safePath.removePrefix("./")
        if (safePath == ".") return root

        val segments = safePath.split('/')
        if (safePath.isEmpty() || segments.any { it == ".." || it.isEmpty() }) {
            throw IOException("Chemin invalide dans l'archive")
        }
        safePath = segments.filterNot { it == "." }.joinToString("/")
        val rootPath = root.canonicalFile.toPath()
        val resolved = root.toPath().resolve(safePath).normalize()
        if (!resolved.startsWith(rootPath)) {
            throw IOException("Chemin hors du rootfs dans l'archive")
        }
        return resolved.toFile()
    }

    private fun applyMode(file: File, mode: Int, isDirectory: Boolean = false) {
        val ownerAccess = if (isDirectory) 0x1c0 else 0x180
        Os.chmod(file.absolutePath, (mode and 0x1ff) or ownerAccess)
    }

    private fun installKaliToolBootstrap(rootfs: File) {
        val script = File(rootfs, "usr/local/sbin/void-kali-tools")
        script.parentFile?.mkdirs()
        script.writeText("""#!/bin/bash
set -e
export DEBIAN_FRONTEND=noninteractive
mkdir -p /var/lib/void-linux
if [ -f /var/lib/void-linux/.tools-ready ]; then
    exit 0
fi
printf '%s\n' '[Void-Linux] Initialisation des paquets Kali...'
apt-get update
apt-get install -y --no-install-recommends ca-certificates gnupg
apt-get install -y kali-linux-default tor
touch /var/lib/void-linux/.tools-ready
printf '%s\n' '[Void-Linux] Paquets Kali installés.'
""".trimIndent())
        Os.chmod(script.absolutePath, 0x1ed)

        val login = File(rootfs, "usr/local/sbin/void-kali-login")
        login.writeText("""#!/bin/bash
set -e
/usr/local/sbin/void-kali-tools
exec /bin/bash --noprofile --norc -i
""".trimIndent())
        Os.chmod(login.absolutePath, 0x1ed)
    }

    fun isInstalled(distro: String): Boolean {
        val dir = host.rootfsFor(distro)
        return File(dir, "bin/bash").isFile && File(dir, "usr/bin/apt-get").isFile
    }

    fun uninstall(distro: String): Boolean =
        host.rootfsFor(distro).deleteRecursively()

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var bytesRead = 0L
            private set

        companion object {
            const val MINIMUM_FREE_SPACE_BYTES = 256L * 1024 * 1024
        }

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) bytesRead++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0)             bytesRead += count
            return count
        }
    }
}

private class InstallationNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun showStart(distroName: String) {
        val notif = NotificationCompat.Builder(context, Constants.CHANNEL_INSTALL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Installation de $distroName")
            .setContentText("Téléchargement du rootfs…")
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()
        manager.notify(Constants.NOTIF_ID_INSTALL, notif)
    }

    fun update(distroName: String, progress: Int) {
        val notif = NotificationCompat.Builder(context, Constants.CHANNEL_INSTALL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Installation de $distroName")
            .setContentText("$progress %")
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()
        manager.notify(Constants.NOTIF_ID_INSTALL, notif)
    }

    fun showComplete(distroName: String) {
        val notif = NotificationCompat.Builder(context, Constants.CHANNEL_INSTALL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("$distroName installé")
            .setContentText("Kali et apt sont prêts")
            .setAutoCancel(true)
            .build()
        manager.notify(Constants.NOTIF_ID_INSTALL, notif)
    }

    fun showError(distroName: String, message: String) {
        val notif = NotificationCompat.Builder(context, Constants.CHANNEL_SECURITY)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Échec de l'installation")
            .setContentText("$distroName : $message")
            .setAutoCancel(true)
            .build()
        manager.notify(Constants.NOTIF_ID_INSTALL, notif)
    }
}
