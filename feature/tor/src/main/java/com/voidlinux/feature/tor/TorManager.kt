package com.voidlinux.feature.tor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.voidlinux.core.common.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TorManager(private val context: Context) {

    private val _state = MutableStateFlow<TorState>(TorState.Stopped)
    val state: StateFlow<TorState> = _state

    private val ORBOT_PACKAGE = "org.torproject.android"
    private val ACTION_START = "org.torproject.android.intent.action.START"
    private val ACTION_STOP = "org.torproject.android.intent.action.STOP"
    private val ACTION_STATUS = "org.torproject.android.intent.action.STATUS"

    fun isOrbotInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(ORBOT_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isTorRunning(): Boolean {
        // Sans broadcast d'Orbot, on considère Tor actif si Orbot est installé
        // et que l'utilisateur l'a lancé manuellement.
        return isOrbotInstalled() && _state.value is TorState.Running
    }

    fun refreshState() {
        _state.value = when {
            !isOrbotInstalled() -> TorState.OrbotMissing
            _state.value is TorState.Running -> TorState.Running
            else -> TorState.Stopped
        }
    }

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

    fun promptInstall() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://f-droid.org/packages/org.torproject.android/")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _state.value = TorState.Error("Impossible d'ouvrir F-Droid")
        }
    }

    fun getSocksPort(): Int = 9050

    fun registerReceiver() { /* pas de receiver avec Intent direct */ }

    fun unregisterReceiver() { /* rien à faire */ }

    /** Marque Tor comme actif (appelé quand l'utilisateur confirme manuellement) */
    fun markRunning() {
        _state.value = TorState.Running
    }
}