package com.voidlinux.feature.linux

data class SecurityToolCollection(
    val id: String,
    val title: String,
    val description: String,
    val packages: List<String>
)

object SecurityToolCatalog {
    val collections = listOf(
        SecurityToolCollection(
            id = "network",
            title = "Réseau et DNS",
            description = "Diagnostic réseau, inventaire et outils DNS.",
            packages = listOf("nmap", "dnsutils", "whois", "netcat-openbsd")
        ),
        SecurityToolCollection(
            id = "web",
            title = "Web et développement",
            description = "Outils HTTP, certificats TLS, Git et Python.",
            packages = listOf("curl", "wget", "openssl", "git", "python3")
        ),
        SecurityToolCollection(
            id = "analysis",
            title = "Analyse locale",
            description = "Inspection de fichiers, binaires et données.",
            packages = listOf("file", "binutils", "jq", "unzip")
        )
    )

    fun byId(id: String): SecurityToolCollection? =
        collections.firstOrNull { it.id == id }
}
