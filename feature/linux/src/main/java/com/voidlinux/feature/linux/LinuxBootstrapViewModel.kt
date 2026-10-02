package com.voidlinux.feature.linux

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voidlinux.core.common.VoidResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LinuxBootstrapState(
    val ready: Boolean = false,
    val initializing: Boolean = false,
    val progress: Int = 0,
    val errorMessage: String? = null,
    val suppressed: Boolean = false
)

class LinuxBootstrapViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = LinuxRepository(app)
    private val _state = MutableStateFlow(LinuxBootstrapState())
    val state: StateFlow<LinuxBootstrapState> = _state

    fun ensureInitialized() {
        if (repository.isAutoInitializationDisabled()) {
            _state.value = LinuxBootstrapState(suppressed = true)
            return
        }
        if (repository.isInstalled()) {
            _state.value = LinuxBootstrapState(ready = true, progress = 100)
            return
        }
        if (_state.value.initializing) return

        viewModelScope.launch {
            _state.value = LinuxBootstrapState(initializing = true)
            when (val result = repository.installDistro { progress ->
                _state.value = _state.value.copy(progress = progress)
            }) {
                is VoidResult.Success -> {
                    _state.value = LinuxBootstrapState(ready = true, progress = 100)
                }
                is VoidResult.Error -> {
                    _state.value = LinuxBootstrapState(errorMessage = result.message)
                }
                else -> {
                    _state.value = LinuxBootstrapState(
                        errorMessage = "Initialisation de Kali interrompue"
                    )
                }
            }
        }
    }
}
