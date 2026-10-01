package com.voidlinux.feature.linux

import com.voidlinux.core.common.Constants

/**
 * Catalogue des distributions Linux supportées.
 */
object DistroCatalog {

    data class Distro(
        val id: String,
        val displayName: String,
        val url: String,
        val archiveName: String,
        val defaultShell: String = "/bin/bash",
        val defaultUser: String = "root"
    )

    val KALI = Distro(
        id = Constants.DISTRO_KALI,
        displayName = "Kali Linux",
        url = Constants.KALI_ROOTFS_ARM64_URL,
        archiveName = Constants.KALI_ROOTFS_ARM64_NAME,
        defaultShell = "/bin/bash"
    )

    val all = listOf(KALI)

    fun byId(id: String): Distro? = all.firstOrNull { it.id == id }
}