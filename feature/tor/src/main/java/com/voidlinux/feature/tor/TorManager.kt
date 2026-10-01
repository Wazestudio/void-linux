package com.voidlinux.feature.tor

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import com.voidlinux.core.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Gère le démarrage d'Orbot via Intent (sans NetCipher).
 */
class TorManager(private val context: Context) {

    private val _state = MutableStateFlow<TorState>(TorState.Stopped)
    val state: StateFlow<TorState> = _state

    private val orbotPackage = "org.torproject.android"

    fun isOrbotInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    orbotPackage,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(orbotPackage, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    suspend fun isTorRunning(): Boolean = withContext(Dispatchers.IO) {
        if (!isOrbotInstalled()) return@withContext false

        val connection = try {
            (URL("https://check.torproject.org/api/ip").openConnection(
                Proxy(
                    Proxy.Type.SOCKS,
                    java.net.InetSocketAddress("127.0.0.1", getSocksPort())
                )
            ) as HttpsURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
            }
        } catch (e: IOException) {
            Logger.w("Proxy Tor SOCKS indisponible: ${e.message}")
            return@withContext false
        }

        try {
            if (connection.responseCode != HttpsURLConnection.HTTP_OK) {
                return@withContext false
            }
            val response = connection.inputStream.bufferedReader().use { it.readLine().orEmpty() }
            IS_TOR_RESPONSE.containsMatchIn(response)
        } catch (e: IOException) {
            Logger.w("Vérification du proxy Tor impossible: ${e.message}")
            false
        } finally {
            connection.disconnect()
        }
    }

    fun requestStart(): Boolean {
        if (!isOrbotInstalled()) {
            _state.value = TorState.OrbotMissing
            return false
        }
        return openOrbot(TorState.Starting)
    }

    fun openOrbot(): Boolean = openOrbot(_state.value)

    private fun openOrbot(state: TorState): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(orbotPackage)
        if (intent == null) {
            _state.value = TorState.Error("Impossible d'ouvrir Orbot")
            return false
        }

        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            _state.value = state
            true
        } catch (e: ActivityNotFoundException) {
            Logger.e("Orbot n'a pas d'activité de lancement", e)
            _state.value = TorState.Error("Impossible d'ouvrir Orbot")
            false
        } catch (e: SecurityException) {
            Logger.e("Ouverture d'Orbot refusée", e)
            _state.value = TorState.Error("L'ouverture d'Orbot a été refusée")
            false
        }
    }

    fun promptInstall() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://f-droid.org/packages/org.torproject.android/")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Logger.e("Aucune application ne peut ouvrir la page d'Orbot", e)
            _state.value = TorState.Error("Impossible d'ouvrir la page d'installation")
        }
    }

    fun getSocksPort(): Int = 9050

    companion object {
        private val IS_TOR_RESPONSE = Regex("\"IsTor\"\\s*:\\s*true", RegexOption.IGNORE_CASE)
    }
}
