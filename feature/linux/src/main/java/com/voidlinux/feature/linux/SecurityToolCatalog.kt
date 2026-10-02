package com.voidlinux.feature.linux

/**
 * Collections d'outils destinées au diagnostic et à l'apprentissage.
 *
 * Les collections évitent volontairement les outils de bruteforce, d'exploitation
 * ou de contournement de protections. Elles sont installées à la demande dans
 * le rootfs Kali déjà présent sur l'appareil.
 */
data class SecurityToolCollection(
    val id: String,
    val title: String,
    val description: String,
    val packages: List<String>
)

object SecurityToolCatalog {

    const val DEFAULT_COLLECTION_ID = "baseline"

    val collections = listOf(
        SecurityToolCollection(
            id = DEFAULT_COLLECTION_ID,
            title = "Essentiels de diagnostic",
            description = "Outils système et réseau pour diagnostiquer son propre appareil ou un laboratoire autorisé.",
            packages = listOf(
                "iproute2",
                "iputils-ping",
                "dnsutils",
                "whois",
                "curl",
                "wget",
                "openssl"
            )
        ),
        SecurityToolCollection(
            id = "web",
            title = "Web et développement",
            description = "HTTP, TLS, Git et Python pour le développement et l'analyse défensive.",
            packages = listOf("curl", "wget", "openssl", "git", "python3")
        ),
        SecurityToolCollection(
            id = "analysis",
            title = "Analyse locale",
            description = "Inspection de fichiers, binaires et données locales.",
            packages = listOf("file", "binutils", "jq", "unzip", "procps")
        )
    )

    fun byId(id: String): SecurityToolCollection? =
        collections.firstOrNull { it.id == id }
}
