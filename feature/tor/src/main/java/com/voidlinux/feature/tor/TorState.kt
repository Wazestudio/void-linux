package com.voidlinux.feature.tor

sealed class TorState {

    /** Orbot n'est pas installé sur l'appareil */
    object OrbotMissing : TorState()

    /** Tor est arrêté */
    object Stopped : TorState()

    /** Tor est en cours de démarrage */
    data class Starting(val progress: Int = 0) : TorState()

    /** Tor est actif */
    object Running : TorState()

    /** Une erreur est survenue */
    data class Error(val message: String) : TorState()
}