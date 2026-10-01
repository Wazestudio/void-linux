package com.voidlinux.feature.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.voidlinux.feature.linux.LinuxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TerminalUiState(
    val linuxReady: Boolean = false,
    val sessionActive: Boolean = false,
    val cols: Int = 80,
    val rows: Int = 24,
    val errorMessage: String? = null
)

class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    private val linuxRepo = LinuxRepository(app)

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState

    private var session: TerminalSession? = null

    fun checkLinuxReady() {
        val ready = linuxRepo.isInstalled()
        _uiState.value = _uiState.value.copy(linuxReady = ready)
    }

    fun startSession(
        buffer: TerminalBuffer,
        onOutput: (String) -> Unit
    ) {
        if (!linuxRepo.isInstalled()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Kali Linux n'est pas installé. Installe-le depuis l'onglet Linux."
            )
            return
        }

        session?.stop()
        val terminal = TerminalSession(
            getApplication(),
            buffer,
            onOutput,
            { message ->
                _uiState.value = _uiState.value.copy(errorMessage = message)
            }
        ) { exitCode ->
            _uiState.value = _uiState.value.copy(sessionActive = false)
            if (exitCode != 0 && _uiState.value.errorMessage == null) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "La session Kali s'est arrêtée (code $exitCode)."
                )
            }
        }
        session = terminal
        terminal.start()
        _uiState.value = _uiState.value.copy(
            sessionActive = terminal.isRunning,
            errorMessage = if (terminal.isRunning) null else _uiState.value.errorMessage
        )
    }

    fun writeInput(data: String) {
        session?.write(data)
    }

    fun resize(cols: Int, rows: Int) {
        session?.resize(cols, rows)
        _uiState.value = _uiState.value.copy(cols = cols, rows = rows)
    }

    fun stopSession() {
        session?.stop()
        session = null
        _uiState.value = _uiState.value.copy(sessionActive = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
    }
}