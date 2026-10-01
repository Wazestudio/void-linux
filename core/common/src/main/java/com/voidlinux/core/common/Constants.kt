package com.voidlinux.core.common

object Constants {

    // Canaux de notification
    const val CHANNEL_SECURITY = "void_security"
    const val CHANNEL_SYSTEM = "void_system"
    const val CHANNEL_INSTALL = "void_install"

    // IDs de notification
    const val NOTIF_ID_SECURITY = 1001
    const val NOTIF_ID_INSTALL = 1002
    const val NOTIF_ID_TOR = 1003
    const val NOTIF_ID_FOREGROUND = 1004

    // Répertoires internes
    const val DIR_PROOT = "proot"
    const val DIR_ROOTFS = "rootfs"
    const val DIR_HOME = "home"
    const val DIR_TMP = "tmp"
    const val DIR_WINE = "wine"
    const val DIR_LOGS = "logs"

    // Distributions Linux supportées
    const val DISTRO_KALI = "kali"
    const val DISTRO_DEBIAN = "debian"
    const val DISTRO_UBUNTU = "ubuntu"
    const val DISTRO_ALPINE = "alpine"

    // URLs rootfs (Kali NetHunter)
    const val KALI_ROOTFS_ARM64_URL =
        "https://kali.download/nethunter-images/current/rootfs/kali-nethunter-rootfs-minimal-arm64.tar.xz"
    const val KALI_ROOTFS_ARM64_SHA256_URL =
        "https://kali.download/nethunter-images/current/rootfs/SHA256SUMS"
    const val KALI_ROOTFS_ARMHF_URL =
        "https://kali.download/nethunter-images/current/rootfs/kali-nethunter-rootfs-minimal-armhf.tar.xz"

    const val KALI_ROOTFS_ARM64_NAME = "kali-arm64.tar.xz"
    const val KALI_ROOTFS_ARMHF_NAME = "kali-armhf.tar.xz"

    // Outils Kali installés au premier démarrage du terminal
    const val KALI_TOOLS_MARKER = "/var/lib/void-linux/.tools-ready"
    const val KALI_TOOLS_SCRIPT = "/usr/local/sbin/void-kali-tools"

    // Tor
    const val TOR_SOCKS_PORT = 9050
    const val TOR_HTTP_PORT = 8118

    // Routage réseau de la session Linux.
    const val NETWORK_ROUTE_PREF = "network_route"
    const val NETWORK_PROXY_HOST_PREF = "network_proxy_host"
    const val NETWORK_PROXY_PORT_PREF = "network_proxy_port"
    const val NETWORK_ROUTE_DIRECT = "direct"
    const val NETWORK_ROUTE_TOR = "tor"
    const val NETWORK_ROUTE_SOCKS5 = "socks5"

    // Terminal
    const val TERMINAL_DEFAULT_COLS = 80
    const val TERMINAL_DEFAULT_ROWS = 24

    // Sécurité
    const val SCAN_INTERVAL_MS = 30_000L
    const val BATTERY_DRAIN_THRESHOLD = 15
    const val NETWORK_USAGE_THRESHOLD_MB = 500L

    // Service foreground
    const val FOREGROUND_SERVICE_ID = 2001
}