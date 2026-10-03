package com.voidlinux.feature.linux

import android.content.Context
import com.voidlinux.core.common.Constants
import java.io.File

/**
 * Implémentation locale de ProotHost.
 */
class LinuxHost(private val context: Context) {

    val prefixDir: File by lazy {
        File(context.filesDir, Constants.DIR_PROOT).ensureDir()
    }

    val rootfsDir: File by lazy {
        File(prefixDir, Constants.DIR_ROOTFS).ensureDir()
    }

    val homeDir: File by lazy {
        File(prefixDir, Constants.DIR_HOME).ensureDir()
    }

    val tmpDir: File by lazy {
        File(context.filesDir, Constants.DIR_TMP).ensureDir()
    }

    val nativeLibsDir: File by lazy {
        File(context.applicationInfo.nativeLibraryDir)
    }

    val packageName: String
        get() = context.packageName

    private fun File.ensureDir(): File {
        if (!exists() && !mkdirs() && !isDirectory) {
            throw IllegalStateException("Impossible de créer ${absolutePath}")
        }
        return this
    }

    fun rootfsFor(distro: String): File = File(rootfsDir, distro)

    /**
     * Retourne le loader PRoot. Le nom officiel utilisé par la CI est
     * libproot_loader.so, mais une ancienne version du projet utilisait
     * libproot-loader.so. On accepte les deux pour éviter un terminal mort
     * avec un APK déjà construit.
     */
    fun prootLoaderFile(): File = listOf(
        File(nativeLibsDir, "libproot_loader.so"),
        File(nativeLibsDir, "libproot-loader.so")
    ).firstOrNull { it.isFile && it.length() > 0L }
        ?: File(nativeLibsDir, "libproot_loader.so")

    fun hasNativeBinaries(): Boolean {
        return listOf(
            File(nativeLibsDir, "libproot.so"),
            prootLoaderFile(),
            File(nativeLibsDir, "libtalloc.so"),
            File(nativeLibsDir, "libandroid-shmem.so")
        ).all { it.isFile && it.length() > 0L }
    }
}
