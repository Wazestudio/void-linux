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
        File(context.cacheDir, Constants.DIR_TMP).ensureDir()
    }

    val nativeLibsDir: File by lazy {
        File(context.applicationInfo.nativeLibraryDir)
    }

    val packageName: String
        get() = context.packageName

    private fun File.ensureDir(): File {
        if (!exists()) mkdirs()
        return this
    }

    fun rootfsFor(distro: String): File = File(rootfsDir, distro)

    fun hasNativeBinaries(): Boolean {
        return listOf(
            "libproot.so",
            "libproot_loader.so",
            "libtalloc.so",
            "libandroid-shmem.so"
        ).all { File(nativeLibsDir, it).isFile }
    }
}
