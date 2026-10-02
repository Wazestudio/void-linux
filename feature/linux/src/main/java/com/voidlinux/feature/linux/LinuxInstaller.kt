package com.voidlinux.feature.linux

import android.app.NotificationManager
import android.content.Context
import android.system.ErrnoException
import android.system.Os
import androidx.core.app.NotificationCompat
import com.voidlinux.core.common.Constants
import com.voidlinux.core.common.VoidResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
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
        installationMutex.withLock {
            val targetDir = host.rootfsFor(distro.id)
            if (isInstalled(distro.id)) {
                prepareRuntimeFilesystem(targetDir)
                configureKaliLogin(targetDir)
                return@withLock VoidResult.Success(targetDir)
            }

            notifier.showStart(distro.displayName)
            val stagingDir = File(host.rootfsDir, "${distro.id}.installing")
            val backupDir = File(host.rootfsDir, "${distro.id}.backup")
            val (assetName, archiveName, downloadUrl) = rootfsSource(distro)
            val archive = File(host.tmpDir, archiveName)
            val partialArchive = File(host.tmpDir, "$archiveName.part")

            try {
                host.tmpDir.mkdirs()
                var bundledRootfs = false
                if (!archive.isFile || archive.length() == 0L) {
                    if (!partialArchive.isFile || partialArchive.length() == 0L) {
                        bundledRootfs = copyBundledRootfsIfAvailable(assetName, partialArchive)
                    }
                    if (bundledRootfs && !partialArchive.renameTo(archive)) {
                        throw IOException("Impossible de préparer le rootfs intégré")
                    }
                }

                if (!bundledRootfs) {
                    val expectedSha256 = fetchExpectedSha256(downloadUrl)
                    if (!archive.isFile || sha256(archive) != expectedSha256) {
                        archive.delete()
                        downloadAndVerify(downloadUrl, expectedSha256, partialArchive) { progress ->
                            val overall = progress / 2
                            notifier.update(distro.displayName, overall)
                            onProgress(overall)
                        }
                        if (!partialArchive.renameTo(archive)) {
                            throw IOException("Impossible de finaliser le téléchargement")
                        }
                    }
                }

                stagingDir.deleteRecursively()
                if (!stagingDir.mkdirs() && !stagingDir.isDirectory) {
                    throw IOException("Impossible de créer le répertoire temporaire du rootfs")
                }
                extractRootfs(archive, stagingDir) { progress ->
                    val overall = 50 + progress / 2
                    notifier.update(distro.displayName, overall)
                    onProgress(overall)
                }

                // Certaines archives sont directement à la racine, d'autres peuvent
                // contenir un unique dossier parent. Normaliser avant validation évite
                // le faux "aucun shell" alors que le shell existe réellement.
                normalizeRootfsLayout(stagingDir)
                validateKaliRootfs(stagingDir)

                if (backupDir.exists()) backupDir.deleteRecursively()
                if (targetDir.exists() && !targetDir.renameTo(backupDir)) {
                    throw IOException("Impossible de sauvegarder l'installation Linux existante")
                }
                if (!stagingDir.renameTo(targetDir)) {
                    if (backupDir.exists()) backupDir.renameTo(targetDir)
                    throw IOException("Impossible de finaliser l'installation Linux")
                }
                prepareRuntimeFilesystem(targetDir)
                configureKaliLogin(targetDir)
                backupDir.deleteRecursively()
                archive.delete()
                onProgress(100)
                notifier.showComplete(distro.displayName)
                VoidResult.Success(targetDir)
            } catch (e: Exception) {
                stagingDir.deleteRecursively()
                notifier.showError(distro.displayName, e.message ?: "Erreur inconnue")
                VoidResult.Error("Échec de l'installation Kali", e)
            }
        }
    }

    private fun rootfsSource(distro: DistroCatalog.Distro): Triple<String, String, String> {
        if (distro.id != Constants.DISTRO_KALI) {
            return Triple("", distro.archiveName, distro.url)
        }
        return when (BuildConfig.TARGET_ABI) {
            "armeabi-v7a" -> Triple(
                "kali-armhf.tar.xz",
                Constants.KALI_ROOTFS_ARMHF_NAME,
                Constants.KALI_ROOTFS_ARMHF_URL
            )
            else -> Triple(
                "kali-arm64.tar.xz",
                Constants.KALI_ROOTFS_ARM64_NAME,
                Constants.KALI_ROOTFS_ARM64_URL
            )
        }
    }

    private fun copyBundledRootfsIfAvailable(
        assetName: String,
        target: File
    ): Boolean {
        if (assetName.isEmpty()) return false
        return try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output, 64 * 1024)
                }
            }
            target.isFile && target.length() > 0L
        } catch (_: IOException) {
            target.delete()
            false
        }
    }

    private fun fetchExpectedSha256(url: String): String {
        val archiveName = URL(url).path.substringAfterLast('/')
        val checksumUrl = URL(URL(url), "SHA256SUMS").toString()
        val connection = openHttpsConnection(checksumUrl)
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Téléchargement des sommes de contrôle refusé : HTTP ${connection.responseCode}")
            }
            val sums = connection.inputStream.bufferedReader(Charsets.US_ASCII).use { it.readText() }
            val checksumLine = sums.lineSequence().firstOrNull { line ->
                val fields = line.trim().split(Regex("\\s+"), limit = 2)
                fields.size == 2 &&
                    fields[0].matches(Regex("[0-9a-fA-F]{64}")) &&
                    fields[1].removePrefix("*").substringAfterLast('/') == archiveName
            } ?: throw IOException("Somme SHA-256 introuvable pour $archiveName")
            return checksumLine.trim().split(Regex("\\s+"), limit = 2)[0].lowercase()
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadAndVerify(
        url: String,
        expectedSha256: String,
        target: File,
        onProgress: (Int) -> Unit
    ) {
        var lastError: IOException? = null
        repeat(MAX_DOWNLOAD_ATTEMPTS) { attempt ->
            try {
                downloadFile(url, target, onProgress)
                if (sha256(target) == expectedSha256) return
                target.delete()
                lastError = IOException("La somme SHA-256 du rootfs téléchargé ne correspond pas")
            } catch (e: IOException) {
                lastError = e
            }
            if (attempt < MAX_DOWNLOAD_ATTEMPTS - 1) {
                Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        throw IOException("Échec du téléchargement vérifié du rootfs Kali", lastError)
    }

    private fun downloadFile(
        url: String,
        target: File,
        onProgress: (Int) -> Unit
    ) {
        var downloaded = target.length().coerceAtLeast(0L)
        val connection = openHttpsConnection(url, downloaded.takeIf { it > 0L })
        try {
            val responseCode = connection.responseCode
            if (responseCode == HTTP_RANGE_NOT_SATISFIABLE) {
                target.delete()
                throw IOException("Le serveur a refusé la reprise du téléchargement")
            }
            if (responseCode !in 200..299) {
                throw IOException("Téléchargement refusé : HTTP $responseCode")
            }

            val append = responseCode == HttpURLConnection.HTTP_PARTIAL && downloaded > 0L
            if (responseCode == HttpURLConnection.HTTP_PARTIAL && !append) {
                target.delete()
                throw IOException("Réponse de reprise inattendue du serveur")
            }
            if (downloaded > 0L && !append) {
                // Le serveur a ignoré la demande Range : on repart proprement.
                target.delete()
                downloaded = 0L
            }
            val contentRange = if (append) {
                CONTENT_RANGE_REGEX.matchEntire(
                    connection.getHeaderField("Content-Range").orEmpty()
                )
            } else {
                null
            }
            if (append) {
                val rangeStart = contentRange?.groupValues?.get(1)?.toLongOrNull()
                val rangeEnd = contentRange?.groupValues?.get(2)?.toLongOrNull()
                val rangeLength = contentRange?.groupValues?.get(3)?.toLongOrNull()
                val validRange = rangeStart != null && rangeEnd != null &&
                    rangeLength != null && rangeStart == downloaded &&
                    rangeEnd >= rangeStart && rangeLength > rangeEnd
                if (!validRange) {
                    target.delete()
                    throw IOException("Réponse de reprise invalide : Content-Range incohérent")
                }
            }
            val rangeTotal = contentRange?.groupValues?.get(3)?.toLongOrNull()
            val total = rangeTotal ?: connection.contentLengthLong
            if (total > 0L) {
                onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
            }
            val availableSpace = android.os.StatFs(context.filesDir.absolutePath).availableBytes
            if (total > 0L &&
                availableSpace < total - downloaded + CountingInputStream.MINIMUM_FREE_SPACE_BYTES
            ) {
                throw IOException("Espace insuffisant pour télécharger le rootfs et extraire Kali")
            }

            connection.inputStream.use { input ->
                FileOutputStream(target, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (total > 0L) {
                            onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            if (downloaded == 0L || (total > 0L && downloaded != total)) {
                throw IOException("Téléchargement incomplet du rootfs Kali")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openHttpsConnection(
        url: String,
        rangeStart: Long? = null
    ): HttpsURLConnection {
        var currentUrl = URL(url)
        if (currentUrl.protocol != "https") {
            throw IOException("Le téléchargement du rootfs doit utiliser HTTPS")
        }

        repeat(MAX_REDIRECTS + 1) {
            val connection = (currentUrl.openConnection() as? HttpsURLConnection
                ?: throw IOException("Connexion HTTPS indisponible")).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Accept-Encoding", "identity")
                if (rangeStart != null) setRequestProperty("Range", "bytes=$rangeStart-")
                connect()
            }
            when (connection.responseCode) {
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HTTP_TEMPORARY_REDIRECT,
                HTTP_PERMANENT_REDIRECT -> {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Redirection sans destination depuis Kali")
                    val redirectUrl = URL(currentUrl, location)
                    connection.disconnect()
                    if (redirectUrl.protocol != "https") {
                        throw IOException("Le serveur a tenté un téléchargement non sécurisé")
                    }
                    currentUrl = redirectUrl
                }
                else -> return connection
            }
        }
        throw IOException("Trop de redirections pendant le téléchargement Kali")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
                    val guestPath = entry.name
                        .trimStart('.', '/')
                        .split('/')
                        .filter { it.isNotEmpty() && it != "." }
                    val isDeviceTree = guestPath.firstOrNull() == "dev" ||
                        guestPath.getOrNull(1) == "dev"
                    if (isDeviceTree) {
                        entry = tar.nextEntry
                        continue
                    }
                    val output = resolveEntry(targetDir, entry)
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
                try {
                    Os.link(linkTarget.absolutePath, link.absolutePath)
                } catch (e: ErrnoException) {
                    // Android peut refuser les hard-links selon le filesystem/SELinux.
                    // Un fichier indépendant conserve ici le contenu du rootfs sans
                    // empêcher l'installation complète de la distribution.
                    if (e.errno != android.system.OsConstants.EACCES &&
                        e.errno != android.system.OsConstants.EPERM
                    ) throw e
                    linkTarget.inputStream().buffered().use { input ->
                        FileOutputStream(link).use { output -> input.copyTo(output, 64 * 1024) }
                    }
                    applyMode(link, Os.stat(linkTarget.absolutePath).st_mode and 0x1ff)
                }
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
            try {
                Os.symlink(target, link.absolutePath)
            } catch (e: ErrnoException) {
                throw IOException(
                    "Android refuse le lien symbolique dans le stockage privé du rootfs " +
                        "(errno=${e.errno}). Vérifie que le rootfs est sous filesDir.",
                    e
                )
            }
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
        val resolved = rootPath.resolve(safePath).normalize()
        if (!resolved.startsWith(rootPath)) {
            throw IOException("Chemin hors du rootfs dans l'archive")
        }
        return resolved.toFile()
    }

    private fun normalizeRootfsLayout(rootfs: File) {
        if (looksLikeKaliRootfs(rootfs)) return

        val children = rootfs.listFiles()?.filter { it.name != "." && it.name != ".." } ?: emptyList()
        val candidate = children.singleOrNull { it.isDirectory && looksLikeKaliRootfs(it) }
            ?: children.firstOrNull { it.isDirectory && looksLikeRootfs(it) }
            ?: return

        val candidateChildren = candidate.listFiles() ?: emptyArray()
        for (child in candidateChildren) {
            val destination = File(rootfs, child.name)
            if (destination.exists() || java.nio.file.Files.isSymbolicLink(destination.toPath())) {
                // L'extracteur crée /dev comme répertoire de remplacement pour
                // éviter les nœuds de périphérique Android. Si l'archive contient
                // un wrapper (ex. rootfs/dev), ce répertoire vide ne doit pas être
                // considéré comme une collision réelle.
                val canReplaceEmptyDirectory = child.name == "dev" &&
                    destination.isDirectory &&
                    destination.list()?.isEmpty() == true &&
                    !java.nio.file.Files.isSymbolicLink(destination.toPath())
                if (canReplaceEmptyDirectory) {
                    if (!destination.delete()) {
                        throw IOException("Impossible de remplacer le répertoire /dev temporaire")
                    }
                } else {
                    throw IOException("Collision pendant la normalisation du rootfs : ${child.name}")
                }
            }
            if (!child.renameTo(destination)) {
                throw IOException("Impossible de déplacer ${child.name} vers la racine du rootfs")
            }
        }
        if (!candidate.delete()) {
            throw IOException("Impossible de finaliser la structure du rootfs")
        }
    }

    private fun looksLikeRootfs(rootfs: File): Boolean =
        File(rootfs, "etc/os-release").isFile &&
            shellCandidates(rootfs).any { it.isFile }

    private fun looksLikeKaliRootfs(rootfs: File): Boolean {
        val osRelease = File(rootfs, "etc/os-release")
        if (!osRelease.isFile || shellCandidates(rootfs).none { it.isFile }) return false
        val metadata = runCatching { osRelease.readText(Charsets.UTF_8) }.getOrDefault("")
        val isKali = metadata.lineSequence().any { line ->
            val normalized = line.trim()
            normalized == "ID=kali" ||
                normalized.startsWith("ID_LIKE=") && normalized.substringAfter('=').contains("kali", ignoreCase = true)
        }
        return isKali && aptCandidates(rootfs).any { it.isFile }
    }

    private fun shellCandidates(rootfs: File): List<File> = listOf(
        File(rootfs, "bin/bash"),
        File(rootfs, "usr/bin/bash"),
        File(rootfs, "bin/sh"),
        File(rootfs, "usr/bin/sh"),
        File(rootfs, "bin/dash"),
        File(rootfs, "usr/bin/dash")
    )

    private fun aptCandidates(rootfs: File): List<File> = listOf(
        File(rootfs, "usr/bin/apt-get"),
        File(rootfs, "usr/bin/apt"),
        File(rootfs, "bin/apt-get"),
        File(rootfs, "bin/apt")
    )

    private fun findUsableShell(rootfs: File): String =
        shellCandidates(rootfs).firstOrNull { it.isFile }?.relativeTo(rootfs)?.let { "/${it.path}" }
            ?: throw IOException("Aucun shell Linux utilisable trouvé dans le rootfs")

    private fun validateKaliRootfs(rootfs: File) {
        if (!File(rootfs, "etc/os-release").isFile) {
            throw IOException("Rootfs invalide : /etc/os-release est absent")
        }
        val metadata = runCatching { File(rootfs, "etc/os-release").readText(Charsets.UTF_8) }
            .getOrElse { throw IOException("Impossible de lire /etc/os-release", it) }
        val isKali = metadata.lineSequence().any { line ->
            val normalized = line.trim()
            normalized == "ID=kali" ||
                normalized.startsWith("ID_LIKE=") && normalized.substringAfter('=').contains("kali", ignoreCase = true)
        }
        if (!isKali) throw IOException("Le rootfs téléchargé n'est pas identifié comme Kali Linux")
        if (shellCandidates(rootfs).none { it.isFile }) {
            throw IOException("Aucun shell Linux utilisable trouvé dans le rootfs")
        }
        if (aptCandidates(rootfs).none { it.isFile }) {
            throw IOException("APT/apt-get est absent du rootfs Kali")
        }
    }

    private fun applyMode(file: File, mode: Int, isDirectory: Boolean = false) {
        val ownerAccess = if (isDirectory) 0x1c0 else 0x180
        Os.chmod(file.absolutePath, (mode and 0x1ff) or ownerAccess)
    }

    /**
     * Rend les répertoires utilisés par apt/dpkg explicitement accessibles dans
     * le sandbox de l'application. Les propriétaires Unix du tar restent ceux
     * de l'archive, mais sur Android les fichiers sont extraits avec l'UID de
     * l'application ; PRoot simule ensuite root à l'intérieur du rootfs.
     */
    private fun prepareRuntimeFilesystem(rootfs: File) {
        val writableDirectories = listOf(
            "tmp",
            "var/tmp",
            "var/cache/apt",
            "var/cache/apt/archives",
            "var/lib/apt",
            "var/lib/apt/lists",
            "var/lib/apt/lists/partial",
            "var/lib/dpkg",
            "run",
            "root"
        )
        writableDirectories.forEach { relative ->
            val directory = File(rootfs, relative)
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Impossible de créer le répertoire Linux : /$relative")
            }
            applyMode(directory, if (relative == "tmp" || relative == "var/tmp") 0x1ff else 0x1ed, true)
        }

        // Certains rootfs livrent resolv.conf comme un lien vers systemd-resolved,
        // service qui n'existe pas dans une session PRoot Android.
        val resolv = File(rootfs, "etc/resolv.conf")
        if (resolv.isDirectory) resolv.deleteRecursively()
        if (resolv.isFile || java.nio.file.Files.isSymbolicLink(resolv.toPath())) {
            if (!resolv.delete()) {
                throw IOException("Impossible de remplacer /etc/resolv.conf")
            }
        }
        resolv.parentFile?.mkdirs()
        resolv.writeText(
            "# DNS géré par l'environnement Linux embarqué\n" +
                "nameserver 1.1.1.1\n" +
                "nameserver 8.8.8.8\n"
        )
        applyMode(resolv, 0x1a4)

        val hosts = File(rootfs, "etc/hosts")
        if (!hosts.isFile) {
            hosts.writeText("127.0.0.1 localhost\n::1 localhost\n")
            applyMode(hosts, 0x1a4)
        }
    }

    private fun configureKaliLogin(rootfs: File) {
        val shell = findUsableShell(rootfs)
        val login = File(rootfs, "usr/local/sbin/void-kali-login")
        login.parentFile?.mkdirs()
        val command = if (shell.endsWith("/bash")) {
            "exec $shell --noprofile --norc -i"
        } else {
            "exec $shell -i"
        }
        login.writeText("#!/bin/sh\n$command\n")
        Os.chmod(login.absolutePath, 0x1ed)
    }

    fun isInstalled(distro: String): Boolean {
        val dir = host.rootfsFor(distro)
        return dir.isDirectory && runCatching { validateKaliRootfs(dir); true }.getOrDefault(false)
    }

    fun uninstall(distro: String): Boolean =
        host.rootfsFor(distro).deleteRecursively()

    suspend fun installPackages(
        packages: List<String>,
        onOutput: (String) -> Unit
    ): VoidResult<Unit> = withContext(Dispatchers.IO) {
        if (packages.isEmpty() || packages.any { !PACKAGE_NAME_REGEX.matches(it) }) {
            return@withContext VoidResult.Error(
                "Liste de paquets invalide",
                IllegalArgumentException("Les noms de paquets ne sont pas valides")
            )
        }
        val rootfs = host.rootfsFor(Constants.DISTRO_KALI)
        if (!isInstalled(Constants.DISTRO_KALI)) {
            return@withContext VoidResult.Error(
                "Kali doit être initialisé avant d'ajouter des outils",
                IOException("Rootfs Kali absent")
            )
        }
        val proot = File(host.nativeLibsDir, "libproot.so")
        val loader = File(host.nativeLibsDir, "libproot_loader.so")
        if (!proot.isFile || !loader.isFile) {
            return@withContext VoidResult.Error(
                "Moteur PRoot indisponible pour installer les outils",
                IOException("Runtime PRoot incomplet")
            )
        }

        val aptCommand = "set -e; " +
            "export HOME=/root TMPDIR=/tmp DEBIAN_FRONTEND=noninteractive; " +
            "mkdir -p /tmp /var/tmp /var/cache/apt/archives /var/lib/apt/lists/partial /var/lib/dpkg; " +
            "chmod 1777 /tmp /var/tmp; " +
            "dpkg --configure -a || true; " +
            "apt-get update; " +
            "apt-get install -y --no-install-recommends " + packages.joinToString(" ") + "; " +
            "apt-get clean"
        val command = listOf(
            proot.absolutePath,
            "--link2symlink",
            "-0",
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "${host.homeDir.absolutePath}:/root",
            "-w", "/root",
            "/usr/bin/env",
            "-i",
            "HOME=/root",
            "USER=root",
            "LOGNAME=root",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "DEBIAN_FRONTEND=noninteractive",
        ) + LinuxNetworkRoute.variables(context).map { (name, value) -> "$name=$value" } +
            listOf("/bin/bash", "-lc", aptCommand)

        var process: Process? = null
        try {
            val tempDir = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
            process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .apply {
                    environment()["LD_LIBRARY_PATH"] = host.nativeLibsDir.absolutePath
                    environment()["PROOT_LOADER"] = loader.absolutePath
                    environment()["PROOT_TMP_DIR"] = tempDir.absolutePath
                    environment().putAll(LinuxNetworkRoute.variables(context))
                }
                .start()
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach(onOutput)
            }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                VoidResult.Success(Unit)
            } else {
                VoidResult.Error(
                    "APT n'a pas pu installer les outils (code $exitCode)",
                    IOException("Processus APT terminé avec le code $exitCode")
                )
            }
        } catch (e: IOException) {
            process?.destroyForcibly()
            VoidResult.Error("Échec de l'installation des outils", e)
        } catch (e: InterruptedException) {
            process?.destroyForcibly()
            Thread.currentThread().interrupt()
            VoidResult.Error("Installation des outils interrompue", e)
        }
    }

    private companion object {
        val PACKAGE_NAME_REGEX = Regex("[a-z0-9][a-z0-9+.-]*")
        val installationMutex = Mutex()
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_DOWNLOAD_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1_000L
        const val MAX_REDIRECTS = 5
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val HTTP_TEMPORARY_REDIRECT = 307
        const val HTTP_PERMANENT_REDIRECT = 308
        val CONTENT_RANGE_REGEX = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
    }

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
            if (count > 0) bytesRead += count
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
