#!/usr/bin/env bash
set -euo pipefail

ARCH="${1:?Usage: provision-kali-rootfs.sh arm64|armhf [assets-dir]}"
ASSETS_DIR="${2:-app/src/main/assets}"
case "$ARCH" in
  arm64) QEMU_NAMES=(qemu-aarch64-static qemu-aarch64) ;;
  armhf) QEMU_NAMES=(qemu-arm-static qemu-arm) ;;
  *) echo "Architecture Kali inconnue: $ARCH" >&2; exit 1 ;;
esac

archive="$ASSETS_DIR/kali-$ARCH.tar.xz"
if [[ ! -s "$archive" ]]; then
  echo "Archive Kali absente ou vide: $archive" >&2
  exit 1
fi
QEMU_PATH=""
for qemu_name in "${QEMU_NAMES[@]}"; do
  if QEMU_PATH="$(command -v "$qemu_name")"; then
    break
  fi
done
if [[ -z "$QEMU_PATH" ]]; then
  echo "Émulateur absent (essayés : ${QEMU_NAMES[*]}); installe qemu-user-static et binfmt-support." >&2
  exit 1
fi
QEMU_NAME="$(basename "$QEMU_PATH")"

rootfs="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/kali-rootfs.XXXXXX")"
mounted=()
cleanup() {
  if ((${#mounted[@]})); then
    for mountpoint in "${mounted[@]}"; do
      sudo umount "$mountpoint" || true
    done
  fi
  sudo rm -rf "$rootfs"
}
trap cleanup EXIT

sudo tar -xJf "$archive" -C "$rootfs"
if [[ ! -x "$rootfs/bin/bash" ]]; then
  echo "L'archive Kali n'a pas la structure attendue : /bin/bash est absent." >&2
  exit 1
fi
sudo mkdir -p "$rootfs/usr/bin" "$rootfs/etc"
sudo cp "$QEMU_PATH" "$rootfs/usr/bin/$QEMU_NAME"
sudo rm -f "$rootfs/etc/resolv.conf"
sudo cp /etc/resolv.conf "$rootfs/etc/resolv.conf"
if [[ ! -s "$rootfs/etc/resolv.conf" ]]; then
  echo "Impossible de configurer le DNS dans le rootfs Kali." >&2
  exit 1
fi

for directory in dev proc sys; do
  mkdir -p "$rootfs/$directory"
  sudo mount --bind "/$directory" "$rootfs/$directory"
  mounted+=("$rootfs/$directory")
done

echo "[+] Installation des outils de base autorisés dans le rootfs $ARCH"
sudo chroot "$rootfs" /bin/bash -lc \
  'set -e; export DEBIAN_FRONTEND=noninteractive; apt-get update; apt-get install -y --no-install-recommends curl nmap dnsutils whois; apt-get clean; rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/*.deb'

sudo rm -f "$rootfs/usr/bin/$QEMU_NAME"
temporary_archive="$archive.provisioned"
sudo tar --numeric-owner --xattrs --acls -cJf "$temporary_archive" -C "$rootfs" .
sudo chown "$(id -u):$(id -g)" "$temporary_archive"
mv "$temporary_archive" "$archive"
echo "[OK] Rootfs $ARCH provisionné avec curl, nmap, dig et whois."
