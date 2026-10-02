package com.voidlinux.feature.linux

import android.content.Context
import com.voidlinux.core.common.Constants
import com.voidlinux.core.common.VoidResult
import java.io.File

class LinuxRepository(context: Context) {

    private val appContext = context.applicationContext
    private val host = LinuxHost(appContext)
    private val installer = LinuxInstaller(appContext, host)
    private val preferences = appContext.getSharedPreferences(
        Constants.PREF_LINUX,
        Context.MODE_PRIVATE
    )

    suspend fun installDistro(
        distroId: String = Constants.DISTRO_KALI,
        onProgress: (Int) -> Unit = {}
    ): VoidResult<File> {
        val distro = DistroCatalog.byId(distroId)
            ?: return VoidResult.Error("Distribution inconnue : $distroId")
        preferences.edit().remove(Constants.PREF_AUTO_INITIALIZATION_DISABLED).apply()
        return installer.install(distro, onProgress)
    }

    suspend fun installToolCollection(
        collectionId: String,
        onOutput: (String) -> Unit
    ): VoidResult<Unit> {
        val collection = SecurityToolCatalog.byId(collectionId)
            ?: return VoidResult.Error("Collection d'outils inconnue : $collectionId")
        return installer.installPackages(collection.packages, onOutput)
    }

    fun isInstalled(distroId: String = Constants.DISTRO_KALI): Boolean =
        installer.isInstalled(distroId)

    fun uninstall(distroId: String = Constants.DISTRO_KALI): Boolean {
        val uninstalled = installer.uninstall(distroId)
        if (uninstalled) {
            preferences.edit()
                .putBoolean(Constants.PREF_AUTO_INITIALIZATION_DISABLED, true)
                .apply()
        }
        return uninstalled
    }

    fun isAutoInitializationDisabled(): Boolean =
        preferences.getBoolean(Constants.PREF_AUTO_INITIALIZATION_DISABLED, false)

    fun getDistroInfo(distroId: String): DistroCatalog.Distro? =
        DistroCatalog.byId(distroId)

    fun hasNativeSupport(): Boolean = host.hasNativeBinaries()
}
