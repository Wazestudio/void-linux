package com.voidlinux.feature.tor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TorUiState(
    val orbotInstalled: Boolean = false,
    val torRunning: Boolean = false,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val pendingUrl: String? = null
)

class TorViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = TorManager(app)

    private val _uiState = MutableStateFlow(TorUiState())
    val uiState: StateFlow<TorUiState> = _uiState

    private var startJob: Job? = null

    fun refresh() {
        viewModelScope.launch {
            val installed = manager.isOrbotInstalled()
            val running = installed && manager.isTorRunning()

            _uiState.value = _uiState.value.copy(
                orbotInstalled = installed,
                torRunning = running,
                statusMessage = when {
                    !installed -> "Orbot n'est pas installé"
                    running -> "Tor actif et vérifié"
                    else -> "Proxy Tor injoignable — navigation désactivée"
                },
                errorMessage = null
            )
        }
    }

    fun startTor() {
        if (!manager.isOrbotInstalled()) {
            manager.promptInstall()
            return
        }
        if (!manager.requestStart()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Impossible d'ouvrir Orbot"
            )
            return
        }

        startJob?.cancel()
        _uiState.value = _uiState.value.copy(statusMessage = "Démarrage de Tor…")
        startJob = viewModelScope.launch {
            repeat(30) {
                delay(1_000)
                if (manager.isTorRunning()) {
                    _uiState.value = _uiState.value.copy(
                        torRunning = true,
                        statusMessage = "Tor actif et vérifié"
                    )
                    return@launch
                }
            }
            _uiState.value = _uiState.value.copy(
                torRunning = false,
                statusMessage = "Tor n'a pas démarré — vérifie Orbot"
            )
        }
    }

    fun stopTor() {
        if (manager.openOrbot()) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "Désactive Tor depuis l'interface d'Orbot"
            )
        } else {
            _uiState.value = _uiState.value.copy(errorMessage = "Impossible d'ouvrir Orbot")
        }
    }

    fun openOnion(url: String) {
        viewModelScope.launch {
            if (!manager.isTorRunning()) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Tor doit être actif pour charger un .onion"
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(pendingUrl = url)
        }
    }

    fun clearPendingUrl() {
        _uiState.value = _uiState.value.copy(pendingUrl = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

}