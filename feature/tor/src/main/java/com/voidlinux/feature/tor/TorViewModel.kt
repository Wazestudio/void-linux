package com.voidlinux.feature.tor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TorUiState(
    val orbotInstalled: Boolean = false,
    val torRunning: Boolean = false,
    val starting: Boolean = false,
    val progress: Int = 0,
    val statusMessage: String = "Tor arrêté",
    val errorMessage: String? = null
)

class TorViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = TorManager(app)

    private val _uiState = MutableStateFlow(TorUiState())
    val uiState: StateFlow<TorUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        val installed = manager.isOrbotInstalled()
        val running = installed && manager.isTorRunning()
        _uiState.value = _uiState.value.copy(
            orbotInstalled = installed,
            torRunning = running,
            starting = false,
            statusMessage = when {
                !installed -> "Orbot non installé"
                running -> "Tor actif ✅"
                else -> "Tor arrêté"
            },
            errorMessage = null
        )
    }

    fun startTor() {
        if (!manager.isOrbotInstalled()) {
            manager.promptInstall()
            return
        }
        manager.requestStart()
        _uiState.value = _uiState.value.copy(
            starting = true,
            statusMessage = "Ouverture d'Orbot…"
        )
    }

    fun stopTor() {
        manager.requestStop()
        _uiState.value = _uiState.value.copy(
            torRunning = false,
            starting = false,
            statusMessage = "Tor arrêté"
        )
    }

    /** À appeler quand l'utilisateur confirme que Tor tourne dans Orbot */
    fun confirmTorRunning() {
        manager.markRunning()
        _uiState.value = _uiState.value.copy(
            torRunning = true,
            starting = false,
            statusMessage = "Tor actif ✅"
        )
    }

    fun installOrbot() {
        manager.promptInstall()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        manager.unregisterReceiver()
    }
}