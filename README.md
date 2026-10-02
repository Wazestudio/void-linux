# void-linux
Terminal Linux On Android 

<p align="center">
  <img src="void-linux-git.png" alt="Void-Linux" width="100%" />
</p>

<h1 align="center">Void-Linux</h1>

<p align="center">
  <a href="#-français">🇫🇷 Français</a> | <a href="#-english">🇬🇧 English</a>
</p>

<p align="center">
  <strong>Environnement Kali Linux rootless pour Android</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-10%2B-brightgreen?style=flat-square" />
  <img src="https://img.shields.io/badge/Kali-Linux-blue?style=flat-square" />
  <img src="https://img.shields.io/badge/Kotlin-1.9-purple?style=flat-square" />
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" />
  <img src="https://img.shields.io/github/actions/workflow/status/Wazestudio/void-linux/apk-build.yml?style=flat-square" />
</p>

---

## 🇫🇷 Français

### 🌌 Qu'est-ce que Void-Linux ?

**Void-Linux** fournit un rootfs Kali et un terminal Linux sur Android.
L'environnement utilise PRoot : ce n'est pas une machine virtuelle et il
partage le noyau Android. Les fonctions annexes restent expérimentales et leur
niveau de validation varie. Consulte le
[guide complet en français](docs/FONCTIONNEMENT.md) avant de les utiliser.

> Pour le fonctionnement détaillé, les téléchargements, les prérequis, les
> limites et l'état de validation des fonctionnalités, voir le
> [guide complet en français](docs/FONCTIONNEMENT.md).

- 🐉 Rootfs Kali minimal ARM64/ARMHF avec PRoot (sans root Android)
- 💻 Terminal intégré prévu pour une session Linux lorsque le moteur natif est présent
- 🧅 Navigateur utilisant le proxy SOCKS d'Orbot; Orbot doit être installé séparément
- 🧪 Fonctions annexes expérimentales; la localisation fictive est un outil de test développeur

Voir le guide pour les prérequis, les limites, les dépendances et les
vérifications encore nécessaires.

---

## ✨ Fonctionnalités

### 🐉 Environnement Linux

- Rootfs minimal Kali ARM64 ou ARMHF intégré à l'APK selon l'architecture du build
- Initialisation automatique du rootfs intégré dès le premier lancement de l'application
- Aucun root Android requis; l'isolation du rootfs repose sur PRoot
- Session shell interactive dans l'onglet Terminal
- Installation des paquets Kali avec `apt`
- Initialisation locale du rootfs; Internet requis pour APT et les collections optionnelles

Le rootfs Kali et le moteur PRoot Android sont deux éléments distincts. La CI produit
deux APK distincts : ARM64 (`arm64-v8a`) et ARM 32 bits (`armeabi-v7a`), chacun avec son
rootfs et son moteur PRoot correspondants. Choisis l'APK qui correspond à ton appareil.
L'archive compressée du rootfs est incluse dans l'APK; Android l'extrait vers le stockage
privé au premier lancement de l'application, sans téléchargement ni étape manuelle. L'espace
libre requis après extraction reste de plusieurs centaines de Mo, selon la version du rootfs.
Chaque image inclut aussi `curl`, `nmap`, `dig` et `whois`. Les autres collections sont
installables à la demande depuis l'onglet Linux.

Exemple, dans le terminal Kali :

```sh
apt update
apt install john
```

N'utilise les outils que sur tes systèmes ou dans des laboratoires pour lesquels tu as une autorisation.

### 💻 Terminal intégré

- Émulateur VT100/ANSI complet
- Support des couleurs 256 et true color
- Clavier étendu (ESC, TAB, CTRL, flèches)
- PTY natif pour un vrai shell

### 🪟 Windows (Wine + Box64)

- Exécution de programmes Windows `.exe`
- Box64 pour traduire x86_64 → ARM64
- Préfixe Wine isolé
- Sortie console en temps réel

### 🧅 Tor et Dark Web

- Détection d'Orbot et vérification réelle du proxy Tor via le service de contrôle Tor
- Requêtes HTTP(S) du navigateur transmises par le proxy SOCKS local
- Navigation `.onion` et moteur de recherche intégré
- Blocage des requêtes réseau si le proxy Tor est indisponible
- Session privée : cookies tiers refusés et cookies effacés à la fermeture

### 🛡️ Sécurité

- Surveillance des téléchargements (`FileObserver`)
- Analyse des APK (signature SHA-256 + permissions)
- Détection de drain de batterie
- Surveillance réseau par UID
- Notifications push immédiates

### 📍 Localisation de test

- Fournisseur de position fictive Android nécessitant les options développeur
- Fonction de test, sans effet d'anonymisation ou de protection
- Guide de durcissement système

---

## 📸 Aperçu

<p align="center">
  <img src="void-linux-git.png" alt="Void-Linux Preview" width="80%" />
</p>

---

## 📥 Installation

### Téléchargement direct

Récupère la dernière version depuis
[**Releases**](https://github.com/Wazestudio/void-linux/releases).

### Compilation depuis les sources

```bash
# Clone
git clone https://github.com/Wazestudio/void-linux.git
cd void-linux

# Construit PRoot ARM64 depuis les sources Termux officielles
bash tools/build-proot.sh

# Génère les icônes mipmap
bash tools/generate-mipmaps.sh

# Build
gradle assembleDebug

# APK généré dans :
# app/build/outputs/apk/debug/app-debug.apk
```

La compilation locale de PRoot requiert Docker, `dpkg-deb` et `patchelf`.
Sur Windows, exécute ces commandes dans WSL2 avec Docker Desktop.

`assembleDebug` génère un APK signé avec la clé de débogage locale Android,
adapté aux essais mais pas aux mises à jour d'une version publiée. Pour générer
la clé release une seule fois, configure `KEYSTORE_PASSWORD` et `KEY_PASSWORD`
dans l'environnement GitHub Actions `release-signing`, puis lance manuellement
le workflow **Create Android Release Keystore**. Télécharge immédiatement
l'artefact privé `void-linux-release-keystore` et garde le fichier en lieu sûr.
Un artefact peut être téléchargé par les personnes ayant accès aux artefacts
Actions : protège cet environnement avec restrictions de branches/tags et
approbation. Le workflow refuse de générer une autre clé une fois
`KEYSTORE_BASE64` configuré.

Après téléchargement de l'artefact, sous PowerShell dans le dossier du fichier
JKS, encode-le et copie la valeur dans le presse-papiers :

  `[Convert]::ToBase64String([IO.File]::ReadAllBytes(".\void-linux-release-keystore.jks")) |
  Set-Clipboard`, puis colle le presse-papiers dans le secret
  `KEYSTORE_BASE64` et vide-le avec `Set-Clipboard -Value ""`;

Configure ensuite dans le même environnement les secrets utilisés par le
workflow de build :

- `KEYSTORE_BASE64` : contenu du keystore encodé en Base64;
- `ANDROID_KEYSTORE_PASSWORD` : même valeur que `KEYSTORE_PASSWORD`;
- `ANDROID_KEY_ALIAS` : `void-linux`;
- `ANDROID_KEY_PASSWORD` : même valeur que `KEY_PASSWORD`.

Réutilise toujours cette même clé pour les versions suivantes afin qu'Android
accepte les mises à jour. Ne la committe pas. Le workflow de build refuse de
compiler/publier une release si les secrets manquent et vérifie la signature
avant publication.

---

🏗️ Architecture

```
Void-Linux/
├── app/                    → Application principale
├── core/
│   ├── common/             → Utilitaires partagés
│   ├── designsystem/       → Thème Material 3
│   ├── data/               → Préférences + SQLite
│   └── native/             → Code C (proot loader, PTY)
├── feature/
│   ├── terminal/           → Émulateur terminal
│   ├── linux/              → Installation Kali
│   ├── windows/            → Wine + Box64
│   ├── tor/                → Réseau Tor
│   ├── security/           → Surveillance
│   ├── location/           → Fausse position
│   └── settings/           → Durcissement
├── library/
│   ├── proot-engine/       → Moteur proot
│   ├── termux-bootstrap/   → Bootstrap Termux
│   ├── wine/               → Binaires Wine
│   └── natives/            → Binaires proot
└── tools/                  → Scripts de build
```

---

🔧 Prérequis

Outil Version
Android SDK API 34
NDK r25c+
JDK 17
Gradle 8.7+
Kotlin 1.9.24

---

🚀 CI/CD

Le projet utilise GitHub Actions pour :

· ✅ Build automatique à chaque push
· ✅ Génération des icônes mipmap
· ✅ Téléchargement du rootfs Kali
· ✅ Compilation des binaires natifs
· ✅ Publication automatique sur les tags v*

Créer une release

```bash
git tag v1.0.0
git push origin v1.0.0
```

L'APK sera automatiquement attaché à la release GitHub. La CI y inclut PRoot et ses bibliothèques
ARM64, puis le terminal démarre une session Kali interactive avec PTY.

---

⚠️ Avertissement légal

Void-Linux est un outil destiné à :

· ✅ Tests d'intrusion autorisés
· ✅ Recherche en sécurité
· ✅ Protection de la vie privée personnelle
· ✅ Apprentissage de la cybersécurité

Il est strictement interdit d'utiliser cet outil pour :

· ❌ Attaquer des systèmes sans autorisation
· ❌ Accéder à des données protégées
· ❌ Contourner des mesures de sécurité légales

Les auteurs ne sont pas responsables de l'usage qui en est fait.
Utilise-le de manière éthique et légale.

---

🤝 Contribution

Les contributions sont les bienvenues !

1. Fork le projet
2. Crée une branche (git checkout -b feature/ma-fonctionnalite)
3. Commit (git commit -m 'Ajout de ma fonctionnalité')
4. Push (git push origin feature/ma-fonctionnalite)
5. Ouvre une Pull Request

---

📄 Licence

MIT License — voir LICENSE pour plus de détails.

---

🙏 Remerciements

· Kali Linux — Distribution de pentesting
· Termux — Environnement Linux Android
· Termux PRoot — Émulation de root sans root (GPL-2.0)
· libtalloc — Bibliothèque de gestion mémoire requise par PRoot (GPL-3.0)
· libandroid-shmem — Compatibilité mémoire partagée Android (BSD-3-Clause)
· Wine — Couche de compatibilité Windows
· Box64 — Émulateur x86_64
· Tor Project — Anonymat réseau
· NetCipher — Intégration Tor Android

---

<p align="center">
  <strong>Void-Linux</strong> — Là où le vide devient puissance.
</p>

<p align="center">
  Fait Wazestudio pour la communauté cybersécurité
</p>

---

## 🇬🇧 English

<p align="center">
  <strong>Rootless Kali Linux environment for Android</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-10%2B-brightgreen?style=flat-square" />
  <img src="https://img.shields.io/badge/Kali-Linux-blue?style=flat-square" />
  <img src="https://img.shields.io/badge/Kotlin-1.9-purple?style=flat-square" />
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" />
  <img src="https://img.shields.io/github/actions/workflow/status/Wazestudio/void-linux/apk-build.yml?style=flat-square" />
</p>

### 🌌 What is Void-Linux?

**Void-Linux** is an Android application organized into multiple modules,
including Kali/PRoot, terminal, and Tor. Their level of completion and
validation varies; the functions listed in the repository are not all
guaranteed to be operational. Check the
[complete English guide](docs/FONCTIONNEMENT.md) before using them.

> For detailed functionality, downloads, prerequisites, limitations, and
> feature validation status, see the
> [complete guide](docs/FONCTIONNEMENT.md).

- 🐉 Minimal Kali rootfs ARM64/ARMHF with PRoot (no Android root required)
- 💻 Integrated terminal designed for a Linux session when the native engine is present
- 🧅 Browser using Orbot's SOCKS proxy; Orbot must be installed separately
- 🧪 Experimental screens and components for Windows, security, and localization

See the guide for prerequisites, limitations, dependencies, and
additional checks needed.

---

## ✨ Features

### 🐉 Linux Environment

- Minimal Kali ARM64 or ARMHF rootfs integrated into the APK based on build architecture
- Automatic rootfs initialization on first app launch
- No Android root required; rootfs isolation relies on PRoot
- Interactive shell session in the Terminal tab
- Install Kali packages with `apt`
- Local rootfs initialization; Internet required for APT and optional collections

The Kali rootfs and Android PRoot engine are two separate components. CI produces
two distinct APKs: ARM64 (`arm64-v8a`) and 32-bit ARM (`armeabi-v7a`), each with
its corresponding rootfs and PRoot engine. Choose the APK that matches your device.
The compressed rootfs archive is included in the APK; Android extracts it to
private storage on first app launch, without downloading or manual steps. Required
free space after extraction remains several hundred MB depending on rootfs version.
Each image also includes `curl`, `nmap`, `dig`, and `whois`. Other collections are
installable on demand from the Linux tab.

Example, in the Kali terminal:

```sh
apt update
apt install john
```

Only use tools on your systems or in labs where you have authorization.

### 💻 Integrated Terminal

- Complete VT100/ANSI emulator
- 256 and true color support
- Extended keyboard (ESC, TAB, CTRL, arrows)
- Native PTY for a real shell

### 🪟 Windows (Wine + Box64)

- Run Windows `.exe` programs
- Box64 to translate x86_64 → ARM64
- Isolated Wine prefix
- Real-time console output

### 🧅 Tor and Dark Web

- Orbot detection and real Tor proxy verification via Tor control service
- Browser HTTP(S) requests routed through local SOCKS proxy
- `.onion` navigation and integrated search engine
- Block network requests if Tor proxy is unavailable
- Private session: third-party cookies refused and cookies cleared on close

### 🛡️ Security

- Download monitoring (`FileObserver`)
- APK analysis (SHA-256 signature + permissions)
- Battery drain detection
- Network monitoring by UID
- Immediate push notifications

### 📍 Localization

- Fake GPS location with worldwide presets
- Custom position
- System hardening guide

---

## 📸 Preview

<p align="center">
  <img src="void-linux-git.png" alt="Void-Linux Preview" width="80%" />
</p>

---

## 📥 Installation

### Direct Download

Get the latest version from
[**Releases**](https://github.com/Wazestudio/void-linux/releases).

### Build from Source

```bash
# Clone
git clone https://github.com/Wazestudio/void-linux.git
cd void-linux

# Build PRoot ARM64 from official Termux sources
bash tools/build-proot.sh

# Generate mipmap icons
bash tools/generate-mipmaps.sh

# Build
gradle assembleDebug

# Generated APK in:
# app/build/outputs/apk/debug/app-debug.apk
```

Local PRoot compilation requires Docker, `dpkg-deb`, and `patchelf`.
On Windows, run these commands in WSL2 with Docker Desktop.

`assembleDebug` generates an APK signed with the local Android debug key,
suitable for testing but not for updates to a published version. To generate
the release key once, configure `KEYSTORE_PASSWORD` and `KEY_PASSWORD`
in the GitHub Actions `release-signing` environment, then manually launch
the **Create Android Release Keystore** workflow. Immediately download
the private `void-linux-release-keystore` artifact and keep the file secure.
An artifact can be downloaded by people with access to Actions artifacts:
protect this environment with branch/tag restrictions and approval. The workflow
refuses to generate another key once `KEYSTORE_BASE64` is configured.

After downloading the artifact, in PowerShell in the JKS file folder,
encode it and copy the value to clipboard:

  `[Convert]::ToBase64String([IO.File]::ReadAllBytes(".\void-linux-release-keystore.jks")) |
  Set-Clipboard`, then paste the clipboard into the
  `KEYSTORE_BASE64` secret and clear it with `Set-Clipboard -Value ""`;

Then configure in the same environment the secrets used by the
build workflow:

- `KEYSTORE_BASE64`: Base64-encoded keystore content;
- `ANDROID_KEYSTORE_PASSWORD`: same value as `KEYSTORE_PASSWORD`;
- `ANDROID_KEY_ALIAS`: `void-linux`;
- `ANDROID_KEY_PASSWORD`: same value as `KEY_PASSWORD`.

Always reuse this same key for subsequent releases so Android accepts updates.
Do not commit it. The build workflow refuses to compile/publish a release if secrets
are missing and verifies the signature before publication.

---

🏗️ Architecture

```
Void-Linux/
├── app/                    → Main Application
├── core/
│   ├── common/             → Shared utilities
│   ├── designsystem/       → Material 3 theme
│   ├── data/               → Preferences + SQLite
│   └── native/             → C code (proot loader, PTY)
├── feature/
│   ├── terminal/           → Terminal emulator
│   ├── linux/              → Kali installation
│   ├── windows/            → Wine + Box64
│   ├── tor/                → Tor network
│   ├── security/           → Monitoring
│   ├── location/           → Fake location
│   └── settings/           → Hardening
├── library/
│   ├── proot-engine/       → Proot engine
│   ├── termux-bootstrap/   → Termux bootstrap
│   ├── wine/               → Wine binaries
│   └── natives/            → Proot binaries
└── tools/                  → Build scripts
```

---

🔧 Requirements

Outil Version
Android SDK API 34
NDK r25c+
JDK 17
Gradle 8.7+
Kotlin 1.9.24

---

🚀 CI/CD

The project uses GitHub Actions for:

· ✅ Automatic build on every push
· ✅ Mipmap icon generation
· ✅ Kali rootfs download
· ✅ Native binary compilation
· ✅ Automatic publication on v* tags

Creating a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

The APK will be automatically attached to the GitHub release. CI includes PRoot and
ARM64 libraries, then the terminal starts an interactive Kali session with PTY.

---

⚠️ Legal Warning

Void-Linux is a tool intended for:

· ✅ Authorized penetration testing
· ✅ Security research
· ✅ Personal privacy protection
· ✅ Cybersecurity learning

It is strictly forbidden to use this tool for:

· ❌ Attacking systems without authorization
· ❌ Accessing protected data
· ❌ Circumventing legal security measures

The authors are not responsible for how it is used.
Use it ethically and legally.

---

🤝 Contributing

Contributions are welcome!

1. Fork the project
2. Create a branch (git checkout -b feature/my-feature)
3. Commit (git commit -m 'Add my feature')
4. Push (git push origin feature/my-feature)
5. Open a Pull Request

---

📄 License

MIT License — see LICENSE for details.

---

🙏 Acknowledgments

· Kali Linux — Penetration testing distribution
· Termux — Android Linux environment
· Termux PRoot — Root emulation without root (GPL-2.0)
· libtalloc — Memory management library required by PRoot (GPL-3.0)
· libandroid-shmem — Android shared memory compatibility (BSD-3-Clause)
· Wine — Windows compatibility layer
· Box64 — x86_64 emulator
· Tor Project — Network anonymity
· NetCipher — Android Tor integration

---

<p align="center">
  <strong>Void-Linux</strong> — Where the void becomes power.
</p>

<p align="center">
  Made by Wazestudio for the cybersecurity community
</p>

---
