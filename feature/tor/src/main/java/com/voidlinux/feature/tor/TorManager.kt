package com.voidlinux.feature.tor

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.voidlinux.core.common.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class TorManager(private val context: Context) {

    private val _state = MutableStateFlow<TorState>(TorState.Stopped)
    val state: StateFlow<TorState> = _state

    private val ORBOT_PACKAGE = "org.torproject.android"
    private val ACTION_START = "org.torproject.android.intent.action.START"
    private val ACTION_STOP = "org.torproject.android.intent.action.STOP"
    private val ORBOT_DOWNLOAD_URL =
        "https://guardianproject.info/releases/orbot-latest.apk"
    private val ORBOT_APK_NAME = "orbot-latest.apk"

    private var downloadId: Long = -1L
    private var receiverRegistered = false

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (id != downloadId) return
            installOrbot()
        }
    }

    // ---- Détection Orbot ----

    /**
     * Vérifie si Orbot est installé de façon fiable.
     * Combine plusieurs méthodes pour éviter les faux négatifs.
     */
    fun isOrbotInstalled(): Boolean {
        // Méthode 1 : via PackageManager (la plus fiable)
        if (isPackageInstalled(ORBOT_PACKAGE)) return true

        // Méthode 2 : variantes de package connues
        val variants = listOf(
            "org.torproject.android",
            "org.torproject.android.debug"
        )
        return variants.any { isPackageInstalled(it) }
    }

    /**
     * Vérifie si un package est installé.
     */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Logger.e("Erreur vérification package $packageName", e)
            false
        }
    }

    /**
     * Retourne la version d'Orbot si installé, sinon null.
     */
    fun getOrbotVersion(): String? {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    ORBOT_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(ORBOT_PACKAGE, 0)
            }
            info.versionName
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Vérifie si Orbot est installé ET que sa version est récente.
     */
    fun isOrbotInstalledAndRecent(): Boolean {
        val version = getOrbotVersion() ?: return false
        // Orbot 16.x minimum recommandé pour l'API Intent moderne
        return try {
            val major = version.substringBefore('.').toIntOrNull() ?: 0
            major >= 16
        } catch (e: Exception) {
            true
        }
    }

    fun isTorRunning(): Boolean {
        return isOrbotInstalled() && _state.value is TorState.Running
    }

    fun refreshState() {
        _state.value = when {
            !isOrbotInstalled() -> TorState.OrbotMissing
            _state.value is TorState.Running -> TorState.Running
            else -> TorState.Stopped
        }
    }

    // ---- Contrôle Tor ----

    fun requestStart() {
        if (!isOrbotInstalled()) {
            _state.value = TorState.OrbotMissing
            return
        }
        try {
            val intent = Intent(ACTION_START).apply {
                setPackage(ORBOT_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            _state.value = TorState.Starting(0)
            Logger.d("Demande de démarrage Tor envoyée à Orbot")
        } catch (e: Exception) {
            Logger.e("Erreur démarrage Orbot", e)
            _state.value = TorState.Error("Impossible de démarrer Orbot : ${e.message}")
        }
    }

    fun requestStop() {
        try {
            val intent = Intent(ACTION_STOP).apply {
                setPackage(ORBOT_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.e("Erreur arrêt Orbot", e)
        }
        _state.value = TorState.Stopped
    }

    fun markRunning() {
        _state.value = TorState.Running
    }

    fun getSocksPort(): Int = 9050

    // ---- Téléchargement + Installation ----

    fun downloadAndInstallOrbot() {
        if (!canInstallPackages()) {
            openInstallPermissionSettings()
            _state.value = TorState.Error(
                "Autorise Void-Linux à installer des applications, puis réessaie."
            )
            return
        }

        try {
            val destination = File(
                context.getExternalFilesDir(null),
                ORBOT_APK_NAME
            )
            if (destination.exists()) destination.delete()

            val request = DownloadManager.Request(Uri.parse(ORBOT_DOWNLOAD_URL))
                .setTitle("Orbot")
                .setDescription("Téléchargement de l'APK Orbot")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    ORBOT_APK_NAME
                )
                .setMimeType("application/vnd.android.package-archive")

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)
            registerReceiver()
            _state.value = TorState.Starting(0)
            Logger.d("Téléchargement Orbot lancé (id=$downloadId)")
        } catch (e: Exception) {
            Logger.e("Erreur téléchargement Orbot", e)
            _state.value = TorState.Error("Impossible de télécharger Orbot : ${e.message}")
        }
    }

    private fun installOrbot() {
        try {
            val apk = File(context.getExternalFilesDir(null), ORBOT_APK_NAME)
            if (!apk.exists()) {
                _state.value = TorState.Error("APK Orbot introuvable après téléchargement")
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Logger.d("Installation Orbot déclenchée")
        } catch (e: Exception) {
            Logger.e("Erreur installation Orbot", e)
            _state.value = TorState.Error("Impossible de lancer l'installation : ${e.message}")
        }
    }

    private fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun openInstallPermissionSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.e("Impossible d'ouvrir les paramètres d'installation", e)
        }
    }

    // ---- Receiver DownloadManager ----

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            context,
            downloadReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    fun unregisterReceiver() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(downloadReceiver)
        } catch (_: Exception) { }
        receiverRegistered = false
    }
}