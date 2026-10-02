# void-linux
Terminal Linux On Android

<p align="center">
  <a href="README.md"><img src="https://img.shields.io/badge/Français-🇫🇷-green?style=for-the-badge" alt="Français" /></a>
  <a href="README.en.md"><img src="https://img.shields.io/badge/English-🇬🇧-blue?style=for-the-badge" alt="English" /></a>
</p>

<p align="center">
  <img src="void-linux-git.png" alt="Void-Linux" width="100%" />
</p>

<h1 align="center">Void-Linux</h1>
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

---

## 🌌 What is Void-Linux?

**Void-Linux** is an Android application organized into multiple modules,
including Kali/PRoot, the terminal, and Tor. Their level of completion and validation
varies; the features listed in the repository are not all guaranteed to be operational.
Please read the [complete guide in English](docs/FONCTIONNEMENT.md) before using them.

> For detailed operation, downloads, prerequisites, limitations, and validation status,
> see the [complete guide in English](docs/FONCTIONNEMENT.md).

- 🐉 Minimal Kali ARM64/ARMHF rootfs with PRoot (no Android root required)
- 💻 Integrated terminal designed for a Linux session when the native engine is available
- 🧅 Browser using Orbot SOCKS proxy; Orbot must be installed separately
- 🧪 Experimental screens and components for Windows, security, and localization

See the guide for prerequisites, limitations, dependencies, and verification steps still needed.

---

## ✨ Features

### 🐉 Linux Environment

- Minimal Kali ARM64 or ARMHF rootfs embedded in the APK according to build architecture
- Automatic initialization of the embedded rootfs on first app launch
- No Android root required; rootfs isolation is handled by PRoot
- Interactive shell session in the Terminal tab
- Installation of Kali packages with `apt`
- Local rootfs initialization; internet is required for APT and optional collections

The Kali rootfs and Android PRoot engine are separate components. The CI produces
two APKs: ARM64 (`arm64-v8a`) and 32-bit ARM (`armeabi-v7a`), each with its own rootfs
and corresponding PRoot engine. Choose the APK that matches your device.
The compressed rootfs archive is included in the APK; Android extracts it to private storage
on first launch, without any manual download or setup. The free space required after extraction
still amounts to several hundred MB depending on the rootfs version.
Each image also includes `curl`, `nmap`, `dig`, and `whois`. Other collections can be installed
on demand from the Linux tab.

Example in the Kali terminal:

```sh
apt update
apt install john
```

Only use these tools on systems or in labs where you are explicitly authorized.

### 💻 Integrated Terminal

- Full VT100/ANSI terminal emulator
- 256-color and true-color support
- Extended keyboard support (ESC, TAB, CTRL, arrow keys)
- Native PTY for a real shell

### 🪟 Windows (Wine + Box64)

- Run Windows `.exe` programs
- Box64 translates x86_64 → ARM64
- Isolated Wine prefix
- Real-time console output

### 🧅 Tor and Dark Web

- Detect Orbot and validate the real Tor proxy via the Tor control service
- HTTP(S) browser requests are forwarded through the local SOCKS proxy
- `.onion` navigation and built-in search engine
- Block network requests if the Tor proxy is unavailable
- Private session: third-party cookies are refused and cleared on close

### 🛡️ Security

- Download monitoring (`FileObserver`)
- APK analysis (SHA-256 signature + permissions)
- Battery drain detection
- Network monitoring by UID
- Immediate push notifications

### 📍 Location

- Experimental Android mock-location provider for developer testing (not anonymization)
- Custom position
- System hardening guide

---

## 📸 Overview

<p align="center">
  <img src="void-linux-git.png" alt="Void-Linux Preview" width="80%" />
</p>

---

## 📥 Installation

### Direct download

Get the latest version from [**Releases**](https://github.com/Wazestudio/void-linux/releases).

### Build from source

```bash
# Clone
git clone https://github.com/Wazestudio/void-linux.git
cd void-linux

# Build PRoot from the official Termux sources
bash tools/build-proot.sh

# Generate mipmap icons
bash tools/generate-mipmaps.sh

# Build
gradle assembleDebug

# APK generated at:
# app/build/outputs/apk/debug/app-debug.apk
```

Building PRoot locally requires Docker, `dpkg-deb`, and `patchelf`.
On Windows, run these commands in WSL2 with Docker Desktop.

`assembleDebug` creates an Android debug-signed APK suitable for testing but not for
publishing updates to an official release. To generate the release key once, configure
`KEYSTORE_PASSWORD` and `KEY_PASSWORD` in the GitHub Actions `release-signing`
environment, then trigger the **Create Android Release Keystore** workflow manually.
Download the private artifact `void-linux-release-keystore` immediately and keep it safe.
An artifact can be downloaded by anyone with Actions artifact access; protect this environment
with branch/tag restrictions and approval. The workflow refuses to generate another key once
`KEYSTORE_BASE64` is configured.

After downloading the artifact, in PowerShell inside the JKS folder, encode it and copy the value
into your clipboard:

  `[Convert]::ToBase64String([IO.File]::ReadAllBytes(".\void-linux-release-keystore.jks")) | Set-Clipboard`, then paste the clipboard content into the `KEYSTORE_BASE64` secret and clear it with `Set-Clipboard -Value ""`;

Then configure the other secrets in the same environment used by the build workflow:

- `KEYSTORE_BASE64`: Base64-encoded keystore content;
- `ANDROID_KEYSTORE_PASSWORD`: same value as `KEYSTORE_PASSWORD`;
- `ANDROID_KEY_ALIAS`: `void-linux`;
- `ANDROID_KEY_PASSWORD`: same value as `KEY_PASSWORD`.

Always reuse the same key for subsequent versions so Android accepts updates. Do not commit it.
The build workflow refuses to compile/publish a release if secrets are missing and verifies the
signature before publication.

---

🏗️ Architecture

```
Void-Linux/
├── app/                    → Main application
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
│   ├── proot-engine/       → PRoot engine
│   ├── termux-bootstrap/   → Termux bootstrap
│   ├── wine/               → Wine binaries
│   └── natives/            → PRoot binaries
└── tools/                  → Build scripts
```

---

🔧 Requirements

Tool Version
Android SDK API 34
NDK r25c+
JDK 17
Gradle 8.7+
Kotlin 1.9.24

---

🚀 CI/CD

The project uses GitHub Actions to:

· ✅ Automatic builds on every push
· ✅ Mipmap icon generation
· ✅ Kali rootfs download
· ✅ Native binary compilation
· ✅ Automatic publication on `v*` tags

Create a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The APK will be automatically attached to the GitHub release. The CI includes PRoot and its
ARM64 libraries, then the terminal starts an interactive Kali session with PTY.

---

⚠️ Legal notice

Void-Linux is a tool intended for:

· ✅ Authorized penetration testing
· ✅ Security research
· ✅ Personal privacy protection
· ✅ Cybersecurity learning

It is strictly forbidden to use this tool for:

· ❌ Attacking systems without authorization
· ❌ Accessing protected data
· ❌ Bypassing legal security measures

The authors are not responsible for how it is used.
Use it ethically and legally.

---

🤝 Contributing

Contributions are welcome!

1. Fork the project
2. Create a branch (`git checkout -b feature/my-feature`)
3. Commit (`git commit -m 'Add my feature'`)
4. Push (`git push origin feature/my-feature`)
5. Open a Pull Request

---

📄 License

MIT License — see LICENSE for details.

---

🙏 Acknowledgements

· Kali Linux — Penetration testing distribution
· Termux — Android Linux environment
· Termux PRoot — Rootless emulation (GPL-2.0)
· libtalloc — Memory management library required by PRoot (GPL-3.0)
· libandroid-shmem — Android shared-memory compatibility (BSD-3-Clause)
· Wine — Windows compatibility layer
· Box64 — x86_64 emulator
· Tor Project — Network anonymity
· NetCipher — Tor integration for Android

---

<p align="center">
  <strong>Void-Linux</strong> — Where emptiness becomes power.
</p>

<p align="center">
  Made by Wazestudio for the cybersecurity community
</p>

---
