#!/usr/bin/env bash
set -euo pipefail

ARCH="${1:-arm64}"
OUTPUT_DIR="${2:-app/src/main/assets}"
BASE_URL="https://kali.download/nethunter-images/current/rootfs"

case "$ARCH" in
  arm64)
    FILENAME="kali-nethunter-rootfs-minimal-arm64.tar.xz"
    OUTPUT_NAME="kali-arm64.tar.xz"
    ;;
  armhf)
    FILENAME="kali-nethunter-rootfs-minimal-armhf.tar.xz"
    OUTPUT_NAME="kali-armhf.tar.xz"
    ;;
  *) echo "Architecture inconnue: $ARCH" >&2; exit 1 ;;
esac

mkdir -p "$OUTPUT_DIR"
archive="$OUTPUT_DIR/$OUTPUT_NAME"
sums="$OUTPUT_DIR/SHA256SUMS"

echo "[+] Downloading official Kali NetHunter rootfs: $FILENAME"
curl -fL --retry 5 --retry-delay 3 --connect-timeout 30 --max-time 3600 \
  -o "$archive.tmp" "$BASE_URL/$FILENAME"
mv "$archive.tmp" "$archive"

curl -fL --retry 5 --retry-delay 3 --connect-timeout 30 --max-time 120 \
  -o "$sums.tmp" "$BASE_URL/SHA256SUMS"
mv "$sums.tmp" "$sums"

expected="$(awk -v f="$FILENAME" '$2 == f || $2 == "*"f {print $1; exit}' "$sums")"
if [[ -z "$expected" ]]; then
  echo "[!] SHA256 for $FILENAME not found" >&2
  exit 1
fi
actual="$(sha256sum "$archive" | awk '{print $1}')"
if [[ "$actual" != "$expected" ]]; then
  echo "[!] SHA256 mismatch for $FILENAME" >&2
  echo "expected: $expected" >&2
  echo "actual:   $actual" >&2
  exit 1
fi

rm -f "$sums"
echo "[OK] Verified $OUTPUT_NAME ($actual)"
