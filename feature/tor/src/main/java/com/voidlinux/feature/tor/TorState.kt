package com.voidlinux.feature.tor

sealed class TorState {
    object Stopped : TorState()
    data class Starting(val progress: Int = 0) : TorState()
    object Running : TorState()
    data class Error(val message: String) : TorState()
}