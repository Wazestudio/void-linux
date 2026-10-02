package com.voidlinux.feature.linux

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidlinux.core.common.Constants
import com.voidlinux.core.common.VoidResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LinuxUiState(
    val distroId: String = Constants.DISTRO_KALI,
    val distroName: String = "Kali Linux",
    val installed: Boolean = false,
    val installing: Boolean = false,
    val progress: Int = 0,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val nativeReady: Boolean = false,
    val installingTools: Boolean = false,
    val toolOutput: String = ""
)

class LinuxViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LinuxRepository(app)

    private val _uiState = MutableStateFlow(LinuxUiState())
    val uiState: StateFlow<LinuxUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        val distroId = _uiState.value.distroId
        val distro = repo.getDistroInfo(distroId)
        val installed = repo.isInstalled(distroId)
        val nativeReady = repo.hasNativeSupport()

        _uiState.value = _uiState.value.copy(
            distroName = distro?.displayName ?: distroId,
            installed = installed,
            nativeReady = nativeReady,
            statusMessage = when {
                installed && nativeReady -> "${distro?.displayName} installé — prêt"
                installed -> "Kali est installé, mais le moteur PRoot Android manque"
                !nativeReady -> "${distro?.displayName} peut être téléchargé; le moteur PRoot manque"
                else -> "${distro?.displayName} non installé"
            },
            errorMessage = null
        )
    }

    fun selectDistro(distroId: String) {
        _uiState.value = _uiState.value.copy(distroId = distroId)
        refresh()
    }

    fun updateBootstrapState(state: LinuxBootstrapState) {
        if (_uiState.value.distroId != Constants.DISTRO_KALI) return
        _uiState.value = when {
            state.initializing -> _uiState.value.copy(
                installing = true,
                progress = state.progress,
                statusMessage = "Préparation automatique de Kali…"
            )
            state.ready -> _uiState.value.copy(
                installed = true,
                installing = false,
                progress = 100,
                statusMessage = "Kali installé — prêt"
            )
            state.errorMessage != null -> _uiState.value.copy(
                installing = false,
                errorMessage = state.errorMessage,
                statusMessage = "Échec de la préparation automatique"
            )
            else -> _uiState.value
        }
    }

    fun install() {
        if (_uiState.value.installing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                installing = true,
                progress = 0,
                errorMessage = null,
                statusMessage = "Installation en cours…"
            )

            val result = repo.installDistro(_uiState.value.distroId) { progress ->
                _uiState.value = _uiState.value.copy(progress = progress)
            }

            _uiState.value = when (result) {
                is VoidResult.Success -> _uiState.value.copy(
                    installing = false,
                    installed = true,
                    progress = 100,
                    statusMessage = if (_uiState.value.nativeReady) {
                        "${_uiState.value.distroName} installé — prêt"
                    } else {
                        "Kali téléchargé; le moteur PRoot Android manque encore"
                    }
                )
                is VoidResult.Error -> _uiState.value.copy(
                    installing = false,
                    errorMessage = result.message,
                    statusMessage = "Échec de l'installation"
                )
                else -> _uiState.value.copy(installing = false)
            }
        }
    }

    fun uninstall() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.uninstall(_uiState.value.distroId) }
            _uiState.value = _uiState.value.copy(
                installed = !ok,
                statusMessage = if (ok) "Désinstallé" else "Échec"
            )
            refresh()
        }
    }

    fun installToolCollection(collectionId: String) {
        if (_uiState.value.installingTools) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                installingTools = true,
                toolOutput = "",
                errorMessage = null,
                statusMessage = "Installation des outils en cours…"
            )
            val result = repo.installToolCollection(collectionId) { line ->
                val output = (_uiState.value.toolOutput + line + "\n")
                    .takeLast(MAX_TOOL_OUTPUT_CHARS)
                _uiState.value = _uiState.value.copy(toolOutput = output)
            }
            _uiState.value = when (result) {
                is VoidResult.Success -> _uiState.value.copy(
                    installingTools = false,
                    statusMessage = "Collection installée — utilise les outils dans le terminal"
                )
                is VoidResult.Error -> _uiState.value.copy(
                    installingTools = false,
                    errorMessage = result.message,
                    statusMessage = "Échec de l'installation des outils"
                )
                else -> _uiState.value.copy(installingTools = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private companion object {
        const val MAX_TOOL_OUTPUT_CHARS = 8_000
    }
}
