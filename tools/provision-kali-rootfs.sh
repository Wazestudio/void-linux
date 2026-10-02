#!/usr/bin/env bash
set -euo pipefail

ARCH="${1:?Usage: provision-kali-rootfs.sh arm64|armhf [assets-dir]}"
ASSETS_DIR="${2:-app/src/main/assets}"
case "$ARCH" in
  arm64) QEMU_NAME="qemu-aarch64" ;;
  armhf) QEMU_NAME="qemu-arm" ;;
  *) echo "Architecture Kali inconnue: $ARCH" >&2; exit 1 ;;
esac

archive="$ASSETS_DIR/kali-$ARCH.tar.xz"
if [[ ! -s "$archive" ]]; then
  echo "Archive Kali absente ou vide: $archive" >&2
  exit 1
fi
if ! command -v "$QEMU_NAME" >/dev/null 2>&1; then
  echo "Émulateur $QEMU_NAME absent; installe qemu-user-static et binfmt-support." >&2
  exit 1
fi

rootfs="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/kali-rootfs.XXXXXX")"
mounted=()
cleanup() {
  for mountpoint in "${mounted[@]:-}"; do
    sudo umount "$mountpoint" || true
  done
  sudo rm -rf "$rootfs"
}
trap cleanup EXIT

sudo tar -xJf "$archive" -C "$rootfs"
sudo cp "$(command -v "$QEMU_NAME")" "$rootfs/usr/bin/$QEMU_NAME"
sudo rm -f "$rootfs/etc/resolv.conf"
sudo cp /etc/resolv.conf "$rootfs/etc/resolv.conf"

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
