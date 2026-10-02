# Void-Linux : fonctionnement, installation et limites

Ce document décrit ce que le code du dépôt prévoit, les services et fichiers
qu'il utilise, les données qu'il télécharge, comment installer des paquets et
ce qui n'est pas garanti. Il distingue le code présent d'un fonctionnement
validé : à la date de rédaction, le build complet de l'APK et un essai sur un
téléphone ARM64 n'ont pas encore été confirmés.

## 1. Résumé

Void-Linux est une application Android modulaire. La CI prépare des APK
distincts ARM64 et ARM 32 bits, chacun intégrant le rootfs Kali correspondant
et le moteur PRoot natif. Au premier lancement, l'application extrait
automatiquement le rootfs intégré dans son stockage privé, puis ouvre un shell
Linux avec PRoot, sans obtenir les privilèges root du système Android.

L'application comprend aussi un navigateur qui utilise le proxy SOCKS local
d'Orbot pour les requêtes HTTP(S), ainsi que des écrans et composants pour
d'autres fonctions. Leur présence dans le code ne signifie pas que toutes les
fonctions sont complètes, fiables ou testées sur appareil. L'état de chaque
groupe est détaillé plus bas.

**Ce n'est pas une machine virtuelle Linux complète.** Kali partage le noyau
Android et les ressources matérielles du téléphone. PRoot traduit certaines
opérations de processus et de fichiers; il ne fournit ni noyau Linux séparé,
ni root Android.

## 2. Compatibilité et prérequis

Les valeurs définies dans le projet sont :

| Élément | Valeur ou conséquence |
|---|---|
| Android minimal | API 29 (Android 10) |
| Android compilé pour | API 34 |
| ABI native empaquetée | `arm64-v8a` ou `armeabi-v7a`, selon l'APK |
| JDK et cible JVM | Java 17 |
| Android Gradle Plugin | 8.5.2 |
| Gradle utilisé par la CI | 8.7 |
| Kotlin | 1.9.24 |
| Rootfs Linux proposé | Kali NetHunter minimal ARM64 ou ARMHF |

Il faut un appareil ARM 32 ou 64 bits et plusieurs centaines de Mo libres pour
extraire le rootfs. Le rootfs de base est inclus dans l'APK, donc la première
initialisation ne requiert pas de téléchargement réseau; les dépôts APT et les
collections d'outils supplémentaires nécessitent Internet.

Le rootfs et le moteur PRoot sont deux composants différents :

1. La CI intègre l'archive rootfs Kali correspondant à l'ABI de l'APK.
2. Au premier lancement de l'application, le rootfs est extrait automatiquement
   l'archive dans ses données privées.
3. Le moteur PRoot et ses bibliothèques sont compilés pour la même ABI.

Le fichier `library/proot-engin/proot-engine/src/main/jnilibs/arm64-v8a/.gitkeep`
est un marqueur de dossier, pas un moteur PRoot utilisable. La CI génère les
bibliothèques natives avant de lancer Gradle.

## 3. Première installation et utilisation de Kali

### Depuis l'application

1. Installer et ouvrir l'APK de l'architecture correspondante.
2. Laisser l'initialisation automatique du rootfs intégré se terminer; sa
   progression s'affiche sur le tableau de bord.
3. Laisser l'extraction se terminer sans fermer l'application ni manquer
   d'espace.
4. Ouvrir le terminal et démarrer une session.
5. Installer les outils Linux voulus avec `apt`, selon leur disponibilité
   pour Kali et ARM64.

Le rootfs est placé dans le répertoire privé de l'application, sous
`files/proot/rootfs/kali`. Le répertoire personnel persistant utilisé par le
terminal est `files/proot/home` et il est monté dans Kali à `/root`. Les
répertoires privés ne sont normalement pas directement accessibles aux autres
applications Android. Désinstaller l'application supprime généralement ces
données privées; sauvegarde les fichiers importants séparément.

### Commandes de paquets

Dans le shell Kali :

```sh
apt update
apt search john
apt install john
```

`apt update` actualise les index depuis les dépôts configurés dans le rootfs.
`apt search` permet de vérifier le nom et la disponibilité d'un paquet avant
de l'installer. Pour retirer un paquet, la commande habituelle est
`apt remove nom-du-paquet`.

Il n'existe pas de catalogue d'applications garanti par Void-Linux : c'est
`apt` et la configuration des dépôts Kali du rootfs qui déterminent quels
paquets peuvent être téléchargés. La disponibilité dépend notamment de
l'architecture et de l'état des dépôts. Les commandes réseau peuvent aussi
être limitées par Android, le fabricant, les permissions ou le réseau utilisé.

Utilise les outils de sécurité uniquement sur tes propres systèmes ou dans un
laboratoire pour lequel tu as une autorisation explicite.

## 4. Ce qui se passe au démarrage du terminal

Le parcours prévu est le suivant :

1. Le code vérifie que Kali, Bash et les bibliothèques natives PRoot attendues
   existent.
2. Android crée un pseudo-terminal (PTY) natif.
3. La bibliothèque JNI lance le binaire PRoot avec des arguments distincts,
   son environnement et le répertoire de travail.
4. PRoot entre dans le rootfs Kali, présente l'identité invitée comme root et
   lance Bash en mode interactif.
5. La sortie du PTY est transmise à l'interface. Le clavier envoie les
   caractères au processus et le terminal peut signaler un redimensionnement.

Les montages prévus par la commande PRoot sont :

| Chemin dans Kali | Source ou rôle |
|---|---|
| `/dev` | `/dev` de l'hôte Android |
| `/proc` | `/proc` de l'hôte Android |
| `/sys` | `/sys` de l'hôte Android |
| `/root` | dossier home privé et persistant de l'application |
| `/tmp` de PRoot | dossier temporaire du cache privé de l'application, indiqué par `PROOT_TMP_DIR` |

Le contenu de `dev/` fourni dans l'archive Kali est ignoré à l'extraction,
puis le `/dev` Android est monté par PRoot. Ces montages donnent accès à une
vue de ressources de l'hôte; ils ne transforment pas le conteneur en système
isolé ou en machine virtuelle.

L'identité `root` est simulée à l'intérieur de PRoot. Elle ne permet pas de
modifier les partitions Android, charger des modules noyau ou contourner les
restrictions de l'OS. Les opérations qui exigent réellement ces capacités
échoueront généralement sans appareil rooté et support explicite.

## 5. Téléchargements et connexions réseau

### Image Kali intégrée et initialisation

La CI télécharge l'image officielle correspondante à l'ABI :

- ARM64 : `kali-nethunter-rootfs-minimal-arm64.tar.xz`;
- ARM 32 bits : `kali-nethunter-rootfs-minimal-armhf.tar.xz`.

Après vérification du SHA-256 officiel, la CI utilise QEMU et `chroot` pour
préinstaller `curl`, `nmap`, `dnsutils` et `whois`, puis intègre l'archive
compressée dans l'APK correspondant. Au premier lancement de l'application,
celle-ci extrait automatiquement cette archive dans son stockage privé et
vérifie le rootfs avant de le rendre disponible. Les collections `Réseau et DNS`,
`Web et développement` et `Analyse locale` de l'écran Linux sont installées
à la demande depuis APT et nécessitent Internet.

### Construction de PRoot pour l'APK

Le script `tools/build-proot.sh` récupère le dépôt GitHub
[termux/termux-packages](https://github.com/termux/termux-packages) à la
révision épinglée dans le script. Il demande à l'environnement Docker de
Termux de construire les paquets `proot`, `libtalloc` et
`libandroid-shmem`, extrait leurs fichiers `.deb`, puis prépare les fichiers
natifs attendus par l'application en `arm64-v8a` ou `armeabi-v7a` :

- `libproot.so` — exécutable PRoot renommé pour être empaqueté comme
  bibliothèque native;
- `libproot_loader.so` — loader utilisé par PRoot;
- `libtalloc.so` et `libandroid-shmem.so` — bibliothèques partagées nécessaires.

`patchelf` ajuste les noms de bibliothèques et le chemin de recherche afin que
PRoot trouve ses dépendances empaquetées. Cela ne signifie pas que l'intégralité
des paquets Linux est contenue dans l'APK : le moteur, ses bibliothèques et
l'archive du rootfs Kali minimal sont embarqués. L'application extrait le
rootfs dans son stockage privé au premier lancement; les collections APT
supplémentaires sont téléchargées sur l'appareil à la demande.

### Installation des paquets Kali

Les paquets demandés dans le terminal sont téléchargés par `apt` depuis les
dépôts configurés par Kali dans le rootfs. Ils ne sont pas téléchargés depuis
les serveurs de Void-Linux.

### Tor et navigateur

Orbot est une application Android séparée; elle n'est pas embarquée dans
Void-Linux. Le code cherche le paquet Android `org.torproject.android`, ouvre
son interface pour demander à l'utilisateur de démarrer Tor, puis teste le
proxy SOCKS local `127.0.0.1:9050` en appelant :

`https://check.torproject.org/api/ip`

Le navigateur intégré transmet ses requêtes HTTP(S) GET au proxy SOCKS local.
Un échec de connexion au proxy donne une réponse d'erreur au lieu de faire
volontairement un repli réseau direct. Les méthodes autres que GET ne sont pas
prises en charge par l'intercepteur. Les cookies tiers sont refusés; à la
fermeture de l'activité du navigateur, l'historique, le cache, les cookies et
les données Web sont effacés. À l'ouverture, le navigateur charge le contrôle
Tor; une saisie qui n'est pas reconnue comme URL est recherchée avec
DuckDuckGo. Le bouton d'arrêt ouvre Orbot : l'utilisateur doit désactiver Tor
dans l'application Orbot, Void-Linux ne l'arrête pas directement.

**Le terminal Kali n'est pas automatiquement routé par Tor.** Le fait que le
navigateur utilise Orbot ne configure pas `apt`, `curl`, `nmap` ou les autres
programmes Linux pour utiliser Tor. Ne suppose pas non plus que l'application
fournit une garantie d'anonymat : le comportement dépend du navigateur, des
requêtes, d'Orbot et de l'appareil. Pour une utilisation où l'anonymat est
critique, appuie-toi sur la documentation et le modèle de sécurité du Tor
Project plutôt que sur une promesse de cette application.

## 6. Technologies

| Technologie | Rôle dans le projet |
|---|---|
| Android SDK / AndroidX | Application et composants Android |
| Kotlin | Écrans, logique des fonctions et accès aux API Android |
| Gradle, Android Gradle Plugin | Compilation et assemblage des modules Android |
| Android NDK et CMake | Compilation de la bibliothèque JNI native |
| JNI et C | PTY, lecture/écriture du terminal et lancement/attente des processus |
| PRoot de Termux | Exécuter le rootfs avec une identité root simulée, sans root Android |
| Kali NetHunter rootfs | Distribution invitée, système de fichiers minimal ARM64 ou ARMHF |
| Apache Commons Compress | Lecture des entrées TAR et gestion des métadonnées d'archive |
| XZ for Java | Décompression de l'archive `.tar.xz` sur Android |
| Bash et apt | Shell interactif et gestionnaire de paquets à l'intérieur de Kali |
| Orbot / Tor | Proxy SOCKS local utilisé par le navigateur intégré |
| Android WebView | Affichage des pages dans le navigateur |
| GitHub Actions | Automatisation du build APK et publication sur les tags de release |
| Docker | Environnement de construction des paquets PRoot Termux |
| `patchelf`, `dpkg-deb` | Ajustement des bibliothèques ELF et extraction des paquets Debian |

Les versions des dépendances Android sont déclarées dans
`gradle/libs.versions.toml`. La CI de build est décrite dans
`.github/workflows/apk-build.yml`.

## 7. Fonctions du dépôt et niveau de validation

| Fonction | Ce que le code prévoit | Réserve |
|---|---|---|
| Kali Linux ARM64/ARMHF | Rootfs correspondant intégré à l'APK, extraction automatique et validation des fichiers de base | Le build APK et l'installation réelle sur appareil restent à valider |
| Terminal Linux | PTY natif et démarrage de Bash via PRoot | Le fonctionnement interactif et les opérations `apt` doivent être testés sur un téléphone compatible |
| Navigateur Tor | Orbot externe, test du proxy et requêtes HTTP(S) via SOCKS | Ce n'est pas un VPN global; les requêtes non GET sont refusées par l'intercepteur |
| Windows / Wine / Box64 | Écrans et classes de logique existent | La présence et la compatibilité des binaires requis ne sont pas établies par ce guide; ne pas considérer l'exécution de `.exe` comme garantie |
| Surveillance de sécurité | Des composants de scan/monitoring et leurs écrans existent | La visibilité Android est limitée; ce n'est ni un antivirus certifié ni une surveillance exhaustive |
| Fausse localisation | Écrans et composants de localisation de test existent | Android exige une application de localisation fictive choisie dans les options développeur; ce n'est pas un dispositif d'anonymisation |
| Guide de durcissement | Ouvre des écrans de réglages Android et affiche des conseils | L'application ne peut pas appliquer à elle seule tous les réglages système |

Les noms et écrans de fonctionnalités ne remplacent pas un test réel. Les
fonctions autres que Kali, terminal et Tor peuvent dépendre de binaires, de
permissions, des options Android ou de composants matériels qui ne sont pas
inclus dans ce document comme étant opérationnels.

## 8. Compilation depuis les sources

### Environnement utilisé par la CI

La workflow GitHub Actions configure JDK 17, Android SDK API 34, Gradle 8.7,
installe `patchelf`, construit le runtime PRoot ARM64 puis exécute :

```sh
gradle assembleDebug assembleRelease --stacktrace --no-daemon
```

Les APK sont attendus sous `app/build/outputs/apk/`. Lorsqu'un tag de version
commençant par `v` est poussé, la workflow prévoit de publier les APK en
release GitHub.

### Construction locale

Sur Linux ou WSL2 configuré pour Docker, installer les outils requis (JDK 17,
Android SDK/NDK, Gradle 8.7, Docker, `dpkg-deb`, `patchelf`, Git et Bash), puis :

```sh
git clone https://github.com/Wazestudio/void-linux.git
cd void-linux
bash tools/build-proot.sh
bash tools/generate-mipmaps.sh
gradle assembleDebug
```

L'APK debug devrait être produit dans
`app/build/outputs/apk/debug/`. Le script de génération des icônes est
facultatif si le script n'est pas présent. Pour Windows, le script shell de
construction PRoot doit être exécuté dans WSL2 avec Docker correctement
configuré; PowerShell seul ne remplace pas cet environnement.

À la date de rédaction, le dépôt ne contient pas de `gradlew`/`gradlew.bat`.
La commande `gradle` doit donc être disponible dans l'environnement local.
Un build sur un poste sans Java/Gradle/Android SDK ne permet pas de conclure que
l'APK est compilable.

## 9. Limites pratiques de Linux sur Android

- **Pas de noyau invité :** les appels au noyau restent ceux d'Android.
- **Pas de root Android :** les privilèges simulés dans PRoot restent soumis
  aux permissions de l'application.
- **Architecture :** seuls les appareils ARM 32 bits et ARM64 sont ciblés,
  via deux APKs séparés. x86/x86_64 n'est pas pris en charge.
- **Outils incomplets :** des paquets peuvent s'installer mais ne pas
  fonctionner s'ils ont besoin de capacités noyau, de modules, d'accès brut au
  réseau, de pilotes ou de matériel non exposés par Android.
- **Interface graphique :** un terminal n'inclut pas à lui seul un serveur
  graphique X11/Wayland ni un bureau Linux.
- **Stockage :** rootfs, paquets installés, téléchargements et fichiers
  personnels consomment le stockage privé de l'application.
- **Réseau :** les règles Android, le réseau du téléphone, les dépôts et les
  restrictions du fabricant s'appliquent.
- **Tor :** le routage du navigateur n'est pas le routage de tout Android ou de
  tout Kali.

## 10. Sécurité et usage responsable

Un rootfs est un ensemble conséquent d'exécutables téléchargés puis lancés
localement. Installe l'application depuis une source de confiance et utilise
des réseaux et systèmes pour lesquels tu as l'autorisation. Les outils comme
John the Ripper ou Nmap sont légitimes pour l'audit autorisé, l'apprentissage
et les laboratoires; leur utilisation contre des comptes ou systèmes sans
permission peut être illégale et nuire à autrui.

Pour une vérification plus robuste des téléchargements, le projet devrait
ajouter une validation d'empreinte publiée par une source de confiance. Les
tests recommandés avant de considérer une version comme utilisable sont :

1. compiler l'APK debug et release dans la CI;
2. installer l'APK sur un appareil ou émulateur ARM64 Android 10+;
3. télécharger Kali, démarrer/arrêter le terminal et tester saisie, Ctrl-C et
   redimensionnement;
4. exécuter `apt update`, installer un paquet simple, fermer puis rouvrir
   l'application et vérifier la persistance de `/root`;
5. tester le navigateur avec Orbot arrêté puis démarré, y compris un site
   `.onion`, et confirmer le comportement en échec du proxy.

## 11. Sources officielles et documentation technique

### Ce projet

- [Dépôt Void-Linux](https://github.com/Wazestudio/void-linux)
- [Workflow de build APK](../.github/workflows/apk-build.yml)
- [Script de construction du runtime PRoot](../tools/build-proot.sh)
- [Catalogue de distributions Kali](../feature/linux/src/main/java/com/voidlinux/feature/linux/DistroCatalog.kt)
- [Installateur du rootfs](../feature/linux/src/main/java/com/voidlinux/feature/linux/LinuxInstaller.kt)
- [Construction de la session Linux](../feature/linux/src/main/java/com/voidlinux/feature/linux/LinuxSession.kt)
- [Code JNI et PTY](../native/src/main/cpp/terminal_jni.c)
- [Création du PTY](../native/src/main/cpp/pty_helper.c)
- [Client réseau du navigateur Tor](../feature/tor/src/main/java/com/voidlinux/feature/tor/TorWebViewClient.kt)
- [Gestion d'Orbot et test Tor](../feature/tor/src/main/java/com/voidlinux/feature/tor/TorManager.kt)
- [Versions des dépendances](../gradle/libs.versions.toml)

### Linux, Kali et PRoot

- [Kali NetHunter Rootless](https://www.kali.org/docs/nethunter/nethunter-rootless/)
- [Téléchargements Kali](https://www.kali.org/get-kali/)
- [Gestion des paquets Kali](https://www.kali.org/docs/general-use/kali-linux-sources-list-repositories/)
- [Manuel de `apt`](https://manpages.debian.org/apt/apt.8.en.html)
- [Projet PRoot](https://proot-me.github.io/)
- [Dépôt Termux Packages](https://github.com/termux/termux-packages)
- [Guide de contribution aux paquets Termux](https://github.com/termux/termux-packages/wiki/Building-packages)

### Android et outils de build

- [Documentation Android](https://developer.android.com/docs)
- [Android NDK](https://developer.android.com/ndk)
- [CMake avec Android Studio/Gradle](https://developer.android.com/studio/projects/configure-cmake)
- [JNI (documentation Java)](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/)
- [Documentation Gradle](https://docs.gradle.org/current/userguide/userguide.html)
- [Kotlin](https://kotlinlang.org/docs/home.html)
- [Android WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview)
- [Network Security Configuration Android](https://developer.android.com/privacy-and-security/security-config)
- [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/)
- [XZ for Java](https://tukaani.org/xz/java.html)
- [GitHub Actions](https://docs.github.com/actions)
- [Docker](https://docs.docker.com/)

### Tor

- [Tor Project : Orbot](https://orbot.app/)
- [Dépôt officiel Orbot](https://github.com/guardianproject/orbot)
- [Documentation Tor](https://community.torproject.org/)
- [Vérification du navigateur Tor](https://check.torproject.org/)
- [API de vérification utilisée par le code](https://check.torproject.org/api/ip)

Les versions, URLs d'archives, scripts et comportements peuvent changer.
Consulte les sources du dépôt et les documentations officielles avant de
reproduire une compilation ou de tirer une conclusion sur la sécurité.
