package com.voidlinux.feature.tor

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * Gère le verrouillage du navigateur (PIN ou schéma).
 */
class BrowserLock(private val context: Context) {

    enum class LockType { NONE, PIN, PATTERN }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "browser_lock",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var lockType: LockType
        get() = try {
            LockType.valueOf(prefs.getString(KEY_TYPE, LockType.NONE.name) ?: LockType.NONE.name)
        } catch (e: Exception) { LockType.NONE }
        set(value) { prefs.edit().putString(KEY_TYPE, value.name).apply() }

    fun setPin(pin: String) {
        prefs.edit()
            .putString(KEY_PIN, hash(pin))
            .putString(KEY_TYPE, LockType.PIN.name)
            .apply()
    }

    fun setPattern(pattern: String) {
        prefs.edit()
            .putString(KEY_PATTERN, hash(pattern))
            .putString(KEY_TYPE, LockType.PATTERN.name)
            .apply()
    }

    fun checkPin(pin: String): Boolean =
        hash(pin) == prefs.getString(KEY_PIN, null)

    fun checkPattern(pattern: String): Boolean =
        hash(pattern) == prefs.getString(KEY_PATTERN, null)

    fun disable() {
        prefs.edit().clear().apply()
    }

    fun isLockEnabled(): Boolean = lockType != LockType.NONE

    private fun hash(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_TYPE = "lock_type"
        private const val KEY_PIN = "lock_pin"
        private const val KEY_PATTERN = "lock_pattern"
    }
}