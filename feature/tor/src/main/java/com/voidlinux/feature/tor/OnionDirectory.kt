package com.voidlinux.feature.tor

/**
 * Annuaire des services .onion publics et vérifiés.
 * Sources : Tor Project, dépôts GitHub communautaires (evenblad3/onion-links,
 * adityaax/darkweb-directory) et sites officiels clearnet.
 *
 * ⚠️ Void-Linux ne contrôle pas le contenu de ces sites.
 * Usage éducatif et recherche uniquement.
 */
object OnionDirectory {

    data class OnionLink(
        val name: String,
        val url: String,
        val description: String,
        val category: Category
    )

    enum class Category(val label: String) {
        SEARCH("Moteurs de recherche"),
        PRIVACY("Confidentialité"),
        NEWS("Actualités & Journalisme"),
        OFFICIAL("Sites officiels"),
        TECH("Technologie & Open Source"),
        MAIL("Messagerie sécurisée"),
        SECURITY("Sécurité & Recherche"),
        WHISTLEBLOWING("Lanceurs d'alerte"),
        LIBRARY("Bibliothèques"),
        OTHER("Autres")
    }

    val all: List<OnionLink> = listOf(

        // ---- Moteurs de recherche ----
        OnionLink(
            name = "DuckDuckGo",
            url = "https://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion",
            description = "Moteur de recherche respectueux de la vie privée",
            category = Category.SEARCH
        ),
        OnionLink(
            name = "Ahmia",
            url = "http://juhanurmihxlp77nkq76byazcldy2hlmovfu2epvl5ankdibsot4csyd.onion",
            description = "Moteur de recherche .onion filtré (contenu illégal exclu)",
            category = Category.SEARCH
        ),
        OnionLink(
            name = "Torch",
            url = "http://torchdeedp3i2jigzjdmfpn5ttjhthh5wbmda2rr3jvqjg5p77c54dqd.onion",
            description = "Moteur de recherche historique du dark web",
            category = Category.SEARCH
        ),
        OnionLink(
            name = "Haystack",
            url = "http://haystak5njsmn2hqkewecpaxetahtwhsbsa64jom2k22z5afxhnpxfid.onion",
            description = "Recherche d'index et de contenu .onion",
            category = Category.SEARCH
        ),

        // ---- Confidentialité ----
        OnionLink(
            name = "ProtonMail",
            url = "https://protonmailrmez3lotccipshtkleegetolb73fuirgj7r4o4vfu7ozyd.onion",
            description = "Email chiffré de bout en bout (Proton)",
            category = Category.PRIVACY
        ),
        OnionLink(
            name = "Proton VPN",
            url = "https://protonvpn.com",
            description = "Service VPN (clearnet) - documentation via onion",
            category = Category.PRIVACY
        ),
        OnionLink(
            name = "Privacy Guides",
            url = "https://privacyguides.org",
            description = "Guides de confidentialité et sécurité",
            category = Category.PRIVACY
        ),
        OnionLink(
            name = "Riseup",
            url = "http://vww6ybal4bd7szmgncyruucpgfkqahzddi37ktceo3ah7ngmcopnpyyd.onion",
            description = "Services de communication pour activistes",
            category = Category.PRIVACY
        ),

        // ---- Journalisme / News ----
        OnionLink(
            name = "BBC News",
            url = "https://www.bbcnewsd73hkzno2ini43t4gblxvycyac5aw4gnv7t2rccijh7745uqd.onion",
            description = "Version Tor du site BBC News",
            category = Category.NEWS
        ),
        OnionLink(
            name = "The New York Times",
            url = "https://www.nytimesn7cgmftshazwhfgzm37qxb44r64ytbb2dj3x62d2lljsciiyd.onion",
            description = "Version Tor du New York Times",
            category = Category.NEWS
        ),
        OnionLink(
            name = "Deutsche Welle",
            url = "https://dwnewsvdyyiamwnp.onion",
            description = "Version Tor de Deutsche Welle",
            category = Category.NEWS
        ),
        OnionLink(
            name = "ProPublica",
            url = "https://www.propub3r6espa33w.onion",
            description = "Journalisme d'investigation à but non lucratif",
            category = Category.NEWS
        ),
        OnionLink(
            name = "BBC - Onion",
            url = "https://bbcnewsd73hkzno2ini43t4gblxvycyac5aw4gnv7t2rccijh7745uqd.onion",
            description = "Accès Tor aux nouvelles BBC",
            category = Category.NEWS
        ),

        // ---- Sites officiels ----
        OnionLink(
            name = "CIA",
            url = "http://ciadotgov4sjwlzihbbgxnqg3xiyrg7so2r2o3lt5wz5ypk4sxyjstad.onion",
            description = "Site officiel de la CIA (version Tor)",
            category = Category.OFFICIAL
        ),
        OnionLink(
            name = "Tor Project",
            url = "http://2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid.onion",
            description = "Site officiel du Tor Project",
            category = Category.OFFICIAL
        ),
        OnionLink(
            name = "Debian",
            url = "http://5ekxbftvqg26ohl5.onion",
            description = "Dépôt Debian via Tor",
            category = Category.OFFICIAL
        ),
        OnionLink(
            name = "Qubes OS",
            url = "http://qubesosfasa4zl44o4tws22di6kepyzfeqv3tg4e3ztknltfxqrymdad.onion",
            description = "Site officiel de Qubes OS",
            category = Category.OFFICIAL
        ),
        OnionLink(
            name = "Freedom of the Press Foundation",
            url = "http://freedom.press",
            description = "Organisation de défense des journalistes",
            category = Category.OFFICIAL
        ),

        // ---- Tech / Open Source ----
        OnionLink(
            name = "GitHub (miroir)",
            url = "https://github.com",
            description = "Pas de version .onion officielle, à utiliser via Tor",
            category = Category.TECH
        ),
        OnionLink(
            name = "Internet Archive",
            url = "http://archivebyd3rzt3ehjpm4c3jk855yo38ecpnl7nfxwpq3ngsiwwy2ihsid.onion",
            description = "Bibliothèque numérique et archive du web",
            category = Category.TECH
        ),
        OnionLink(
            name = "Whonix",
            url = "http://dds6qkxpwdeubwucdiaord2xgbbeyds33m2stm4uyn6pdm5ow4vvrqd.onion",
            description = "Distribution Linux axée sur l'anonymat",
            category = Category.TECH
        ),
        OnionLink(
            name = "Tails",
            url = "http://tailsbo4yubxqtz.onion",
            description = "Système d'exploitation amnésique pour la vie privée",
            category = Category.TECH
        ),

        // ---- Messagerie sécurisée ----
        OnionLink(
            name = "SecureDrop",
            url = "https://securedrop.org",
            description = "Plateforme de communication sécurisée pour journalistes",
            category = Category.MAIL
        ),
        OnionLink(
            name = "Briar",
            url = "https://briarproject.org",
            description = "Messagerie P2P chiffrée",
            category = Category.MAIL
        ),
        OnionLink(
            name = "Session",
            url = "https://getsession.org",
            description = "Messagerie chiffrée sans numéro de téléphone",
            category = Category.MAIL
        ),

        // ---- Sécurité & Recherche ----
        OnionLink(
            name = "Hidden Wiki (miroir)",
            url = "http://zqktlwiuavvvqqt4ybvgvi7tyo4hjl5xgfuvpdf6otjiycgwqbym2qad.onion",
            description = "Annuaire .onion (⚠️ contenu non modéré)",
            category = Category.SECURITY
        ),
        OnionLink(
            name = "Dark.fail",
            url = "http://darkfailenbsdla5mal2mxn2uz66od5vtzd5qozslagrfzachha3f3id.onion",
            description = "Vérification d'authenticité de sites .onion",
            category = Category.SECURITY
        ),
        OnionLink(
            name = "OnionScan",
            url = "https://onionscan.org",
            description = "Outil d'analyse de sécurité .onion",
            category = Category.SECURITY
        ),

        // ---- Lanceurs d'alerte ----
        OnionLink(
            name = "SecureDrop (list)",
            url = "https://securedrop.org/directory/",
            description = "Liste de tous les SecureDrop actifs",
            category = Category.WHISTLEBLOWING
        ),
        OnionLink(
            name = "The Guardian SecureDrop",
            url = "https://www.theguardian.com/securedrop",
            description = "Canal sécurisé du Guardian",
            category = Category.WHISTLEBLOWING
        ),
        OnionLink(
            name = "Washington Post SecureDrop",
            url = "https://www.washingtonpost.com/securedrop/",
            description = "Canal sécurisé du Washington Post",
            category = Category.WHISTLEBLOWING
        ),

        // ---- Bibliothèques ----
        OnionLink(
            name = "Internet Archive",
            url = "http://archivebyd3rzt3ehjpm4c3jk855yo38ecpnl7nfxwpq3ngsiwwy2ihsid.onion",
            description = "Archive du web et documents",
            category = Category.LIBRARY
        ),
        OnionLink(
            name = "Sci-Hub",
            url = "https://sci-hub.se",
            description = "Accès aux articles scientifiques",
            category = Category.LIBRARY
        ),
        OnionLink(
            name = "Z-Library",
            url = "https://z-lib.io",
            description = "Bibliothèque de livres électroniques",
            category = Category.LIBRARY
        ),

        // ---- Autres ----
        OnionLink(
            name = "Tor Metrics",
            url = "https://metrics.torproject.org",
            description = "Statistiques du réseau Tor",
            category = Category.OTHER
        ),
        OnionLink(
            name = "Check Tor Project",
            url = "https://check.torproject.org",
            description = "Vérifie si tu es connecté via Tor",
            category = Category.OTHER
        ),
        OnionLink(
            name = "Tor Forum",
            url = "http://zh5dcm5b64pzgx5r.onion",
            description = "Forum officiel de la communauté Tor",
            category = Category.OTHER
        )
    )

    fun byCategory(category: Category): List<OnionLink> =
        all.filter { it.category == category }

    fun search(query: String): List<OnionLink> =
        all.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
        }
}