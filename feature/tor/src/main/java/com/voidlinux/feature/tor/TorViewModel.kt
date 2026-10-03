package com.voidlinux.feature.tor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TorUiState(
    val running: Boolean = false,
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
        manager.registerReceiver()
        refresh()
    }

    fun refresh() {
        val running = manager.isTorRunning()
        _uiState.value = _uiState.value.copy(
            running = running,
            starting = false,
            statusMessage = if (running) "Tor actif ✅" else "Tor arrêté",
            errorMessage = null
        )
    }

    fun startTor() {
        manager.requestStart()
        _uiState.value = _uiState.value.copy(
            starting = true,
            statusMessage = "Démarrage de Tor…"
        )
    }

    fun stopTor() {
        manager.requestStop()
        _uiState.value = _uiState.value.copy(
            running = false,
            starting = false,
            statusMessage = "Tor arrêté"
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        manager.unregisterReceiver()
    }
}