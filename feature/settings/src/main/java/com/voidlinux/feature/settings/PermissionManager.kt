package com.voidlinux.feature.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * État des permissions Android réellement contrôlables par l'application.
 *
 * INTERNET, ACCESS_NETWORK_STATE et FOREGROUND_SERVICE sont des permissions
 * normales/manifest et ne déclenchent pas de dialogue runtime.
 * Le stockage général n'est pas une permission runtime sur Android moderne :
 * l'application utilise le Storage Access Framework pour un dossier choisi
 * par l'utilisateur.
 */
class PermissionManager(private val context: Context) {

    data class PermissionStatus(
        val name: String,
        val granted: Boolean,
        val critical: Boolean
    )

    fun check(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            check(Manifest.permission.POST_NOTIFICATIONS)

    fun hasLocationPermission(): Boolean =
        check(Manifest.permission.ACCESS_FINE_LOCATION) ||
            check(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun listCriticalPermissions(): List<PermissionStatus> = listOf(
        PermissionStatus(
            name = "Notifications",
            granted = hasNotificationPermission(),
            critical = true
        ),
        PermissionStatus(
            name = "Localisation",
            granted = hasLocationPermission(),
            critical = false
        ),
        PermissionStatus(
            name = "Stockage privé de l'application",
            granted = true,
            critical = true
        )
    )

    companion object {
        fun runtimePermissions(): Array<String> = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }.toTypedArray()
    }
}
