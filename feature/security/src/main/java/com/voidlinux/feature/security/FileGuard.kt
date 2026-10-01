package com.voidlinux.feature.security

import android.content.Context
import android.os.FileObserver
import com.voidlinux.core.common.Logger
import java.io.File

/** Surveille un répertoire et déclenche une analyse prudente des nouveaux fichiers. */
class FileGuard(
    private val context: Context,
    private val watchDir: File,
    private val onEvent: (SecurityEvent) -> Unit
) {

    private var observer: FileObserver? = null
    private val scanner = ApkScanner(context)

    fun start() {
        if (!watchDir.exists() && !watchDir.mkdirs()) {
            Logger.e("Impossible de créer ${watchDir.absolutePath}", IllegalStateException("mkdirs failed"))
            return
        }
        if (!watchDir.isDirectory) return

        observer?.stopWatching()
        observer = object : FileObserver(watchDir, FileObserver.CREATE or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                path ?: return
                val file = File(watchDir, path)
                if (file.isFile) handleFile(file)
            }
        }.also { it.startWatching() }

        Logger.d("FileGuard démarré sur ${watchDir.absolutePath}")
    }

    fun stop() {
        observer?.stopWatching()
        observer = null
        Logger.d("FileGuard arrêté")
    }

    private fun handleFile(file: File) {
        when (file.extension.lowercase()) {
            "apk" -> handleApk(file)
            "sh", "bin", "dex", "so" -> handleExecutable(file)
            "zip", "tar", "gz", "xz" -> handleArchive(file)
        }
    }

    private fun handleApk(file: File) {
        val info = scanner.scan(file) ?: run {
            onEvent(
                SecurityEvent(
                    type = SecurityEvent.EventType.APK_DETECTED,
                    severity = SecurityEvent.Severity.MEDIUM,
                    title = "APK non analysable",
                    description = "${file.name} n'a pas pu être analysé. Ne pas l'interpréter automatiquement comme malveillant.",
                    filePath = file.absolutePath
                )
            )
            return
        }

        val sensitive = scanner.findSensitivePermissions(info.permissions)
        if (info.trusted) return

        onEvent(
            SecurityEvent(
                type = SecurityEvent.EventType.APK_UNTRUSTED,
                severity = if (sensitive.isNotEmpty()) SecurityEvent.Severity.MEDIUM else SecurityEvent.Severity.LOW,
                title = "APK à vérifier",
                description = buildString {
                    append("${file.name}\nPackage : ${info.packageName}\n")
                    append("Signature inconnue ou non présente dans la liste de confiance.\n")
                    if (sensitive.isNotEmpty()) {
                        append("Permissions sensibles : ")
                        append(sensitive.joinToString { it.substringAfterLast('.') })
                    }
                },
                packageName = info.packageName,
                filePath = file.absolutePath
            )
        )
    }

    private fun handleExecutable(file: File) {
        onEvent(
            SecurityEvent(
                type = SecurityEvent.EventType.FILE_SUSPICIOUS,
                severity = SecurityEvent.Severity.MEDIUM,
                title = "Fichier exécutable détecté",
                description = "${file.name} (${file.length()} octets)",
                filePath = file.absolutePath
            )
        )
    }

    private fun handleArchive(file: File) {
        onEvent(
            SecurityEvent(
                type = SecurityEvent.EventType.FILE_SUSPICIOUS,
                severity = SecurityEvent.Severity.LOW,
                title = "Archive détectée",
                description = "${file.name} (${file.length()} octets)",
                filePath = file.absolutePath
            )
        )
    }
}
