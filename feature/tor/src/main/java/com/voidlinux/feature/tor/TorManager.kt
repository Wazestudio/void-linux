package com.voidlinux.feature.tor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.voidlinux.core.common.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.torproject.jni.TorService

class TorManager(private val context: Context) {

    private val _state = MutableStateFlow<TorState>(TorState.Stopped)
    val state: StateFlow<TorState> = _state

    private var receiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                TorService.ACTION_STATUS -> {
                    val status = intent.getStringExtra(TorService.EXTRA_STATUS)
                    val progress = intent.getIntExtra("EXTRA_BOOTSTRAP_PROGRESS", 0)
                    Logger.d("Tor status: $status, progress: $progress")
                    when (status) {
                        TorService.STATUS_ON -> _state.value = TorState.Running
                        TorService.STATUS_OFF -> _state.value = TorState.Stopped
                        TorService.STATUS_STARTING -> _state.value = TorState.Starting(progress)
                        TorService.STATUS_STOPPING -> _state.value = TorState.Stopped
                    }
                }
            }
        }
    }

    fun isTorRunning(): Boolean {
        return _state.value is TorState.Running
    }

    fun getSocksPort(): Int = 9050

    fun refreshState() {
        if (_state.value is TorState.Running) return
        _state.value = TorState.Stopped
    }

    fun requestStart() {
        if (_state.value is TorState.Starting || _state.value is TorState.Running) return

        _state.value = TorState.Starting(0)
        try {
            val intent = Intent(context, TorService::class.java)
            ContextCompat.startForegroundService(context, intent)
            Logger.d("TorService démarré")
        } catch (e: Exception) {
            Logger.e("Erreur démarrage Tor", e)
            _state.value = TorState.Error("Impossible de démarrer Tor : ${e.message}")
        }
    }

    fun requestStop() {
        try {
            context.stopService(Intent(context, TorService::class.java))
            _state.value = TorState.Stopped
        } catch (e: Exception) {
            Logger.e("Erreur arrêt Tor", e)
        }
    }

    fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(TorService.ACTION_STATUS)
        ContextCompat.registerReceiver(
            context,
            statusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    fun unregisterReceiver() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(statusReceiver)
        } catch (_: Exception) { }
        receiverRegistered = false
    }
}