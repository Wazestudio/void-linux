#!/bin/bash
# Télécharge un rootfs Kali et vérifie son SHA-256 avant de le conserver.
# Usage: fetch-kali-rootfs.sh <arm64|armhf> <output_dir> [expected_sha256]

set -euo pipefail

ARCH="${1:-arm64}"
OUTPUT_DIR="${2:-app/src/main/assets}"
case "$ARCH" in
    arm64)
        URL="https://kali.download/nethunter-images/current/rootfs/kali-nethunter-rootfs-minimal-arm64.tar.xz"
        FILENAME="kali-arm64.tar.xz"
        DEFAULT_SHA256="d6403a5da175df325611d23af4b92330856059c45454eced7f4cdf3ca6df2e4e"
        ;;
    armhf)
        URL="https://kali.download/nethunter-images/current/rootfs/kali-nethunter-rootfs-minimal-armhf.tar.xz"
        FILENAME="kali-armhf.tar.xz"
        DEFAULT_SHA256="bb770de8c99178aae2a4aca0e29f9fa7f9dbdf3fadd1da6d0d3724e030a6fd91"
        ;;
    *)
        echo "[!] Architecture inconnue : $ARCH" >&2
        exit 1
        ;;
esac

EXPECTED_SHA256="${3:-${KALI_ROOTFS_SHA256:-$DEFAULT_SHA256}}"

if [[ ! "$EXPECTED_SHA256" =~ ^[0-9a-fA-F]{64}$ ]]; then
    echo "[!] SHA-256 invalide : 64 caractères hexadécimaux attendus." >&2
    exit 1
fi

mkdir -p "$OUTPUT_DIR"
DEST="$OUTPUT_DIR/$FILENAME"
TMP="$DEST.part"

if [[ -f "$DEST" ]]; then
    ACTUAL="$(sha256sum "$DEST" | awk '{print $1}')"
    if [[ "$ACTUAL" == "${EXPECTED_SHA256,,}" ]]; then
        echo "[✓] $FILENAME existe et son SHA-256 correspond."
        exit 0
    fi
    echo "[!] $FILENAME existe mais son SHA-256 ne correspond pas. Suppression." >&2
    rm -f "$DEST"
fi

rm -f "$TMP"
echo "[*] Téléchargement de $FILENAME depuis Kali…"
curl --proto '=https' --tlsv1.2 --fail --location --show-error --silent \
    --retry 3 --retry-all-errors --output "$TMP" "$URL"

ACTUAL="$(sha256sum "$TMP" | awk '{print $1}')"
if [[ "$ACTUAL" != "${EXPECTED_SHA256,,}" ]]; then
    echo "[!] Échec de vérification SHA-256." >&2
    echo "    Attendu : ${EXPECTED_SHA256,,}" >&2
    echo "    Reçu    : $ACTUAL" >&2
    rm -f "$TMP"
    exit 1
fi

mv "$TMP" "$DEST"
echo "[✓] Rootfs téléchargé et vérifié : $DEST"
echo "[i] Taille : $(du -h "$DEST" | cut -f1)"
