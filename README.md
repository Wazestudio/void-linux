# void-linux
Terminal Linux On Android 

<p align="center">
  <img src="void-linux-git.png" alt="Void-Linux" width="100%" />
</p>

<h1 align="center">Void-Linux</h1>
<p align="center">
  <strong>Environnement hacker tout-en-un sur Android</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-10%2B-brightgreen?style=flat-square" />
  <img src="https://img.shields.io/badge/Kali-Linux-blue?style=flat-square" />
  <img src="https://img.shields.io/badge/Kotlin-1.9-purple?style=flat-square" />
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" />
  <img src="https://img.shields.io/github/actions/workflow/status/Wazestudio/void-linux/apk-build.yml?style=flat-square" />
</p>

---

## 🌌 Qu'est-ce que Void-Linux ?

**Void-Linux** est une application Android organisée en plusieurs modules,
notamment pour Kali/PRoot, le terminal et Tor. Leur niveau de finition et de
validation varie; les fonctions listées dans le dépôt ne sont pas toutes
garanties comme opérationnelles. Consulte le
[guide complet en français](docs/FONCTIONNEMENT.md) avant de les utiliser.

> Pour le fonctionnement détaillé, les téléchargements, les prérequis, les
> limites et l'état de validation des fonctionnalités, voir le
> [guide complet en français](docs/FONCTIONNEMENT.md).

- 🐉 Rootfs Kali minimal ARM64 installable avec PRoot (sans root Android)
- 💻 Terminal intégré prévu pour une session Linux lorsque le moteur natif est présent
- 🧅 Navigateur utilisant le proxy SOCKS d'Orbot; Orbot doit être installé séparément
- 🧪 Écrans et composants expérimentaux pour Windows, sécurité et localisation

Voir le guide pour les prérequis, les limites, les dépendances et les
vérifications encore nécessaires.

---

## ✨ Fonctionnalités

### 🐉 Environnement Linux

- Téléchargement puis extraction du rootfs minimal Kali ARM64 dans le stockage privé de l'application
- Aucun root Android requis; l'isolation du rootfs repose sur PRoot
- Session shell interactive dans l'onglet Terminal
- Installation des paquets Kali avec `apt`
- L'installation du rootfs nécessite plusieurs Go d'espace libre et une connexion Internet

Le rootfs Kali et le moteur PRoot Android sont deux éléments distincts. La CI construit PRoot
et ses bibliothèques à partir des paquets officiels Termux avant de créer l'APK. Le support
fourni actuellement est limité à ARM64.

Exemple, dans le terminal Kali une fois PRoot fourni et le rootfs installé:

```sh
apt update
apt install john nmap
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

### 📍 Localisation

- Fausse position GPS avec presets mondiaux
- Position personnalisée
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