#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/void-linux-termux-packages"
TERMUX_PACKAGES_REF="2d31765cdab30bbf92f87c495bef6b963df168e5"

ANDROID_ABI="${1:-arm64-v8a}"
case "$ANDROID_ABI" in
    arm64-v8a)
        TERMUX_ARCH="aarch64"
        ;;
    armeabi-v7a)
        TERMUX_ARCH="arm"
        ;;
    *)
        echo "Unsupported Android ABI: $ANDROID_ABI" >&2
        exit 1
        ;;
esac
OUTPUT_DIR="$ROOT_DIR/native/src/main/jniLibs/$ANDROID_ABI"

# Vraies adresses officielles
TERMUX_PACKAGES_REPO="https://github.com/termux/termux-packages"
TERMUX_APT_BASE="https://packages.termux.dev/apt/termux-main"

if ! command -v docker >/dev/null 2>&1; then
    echo "Docker is required to build the official Termux PRoot package." >&2
    exit 1
fi
if ! command -v dpkg-deb >/dev/null 2>&1 || ! command -v patchelf >/dev/null 2>&1 || ! command -v curl >/dev/null 2>&1; then
    echo "dpkg-deb, patchelf, and curl are required." >&2
    exit 1
fi

# 1. Clonage propre et alignement sur le commit spécifique
rm -rf "$WORK_DIR"
echo "Clonage du dépôt termux-packages..."
git clone --quiet --filter=blob:none "$TERMUX_PACKAGES_REPO" "$WORK_DIR"
echo "Alignement sur le commit spécifié..."
git -C "$WORK_DIR" checkout --quiet "$TERMUX_PACKAGES_REF"

cd "$WORK_DIR"

export TERMUX_PACKAGES_URL="$TERMUX_PACKAGES_REPO"
export TERMUX_PACKAGES_REVISION="master"

# 2. Compilation de PRoot dans Docker
echo "Démarrage de la compilation PRoot dans Docker..."
./scripts/run-docker.sh ./build-package.sh -f -C -a "$TERMUX_ARCH" proot

# 3. Téléchargement des dépendances officielles
# On lit l'index officiel Termux pour trouver le nom exact du fichier (pas de version devinée).
echo "Téléchargement des dépendances pré-compilées..."
PACKAGES_INDEX="$WORK_DIR/Packages.index"
curl -fsSL -o "$PACKAGES_INDEX" "$TERMUX_APT_BASE/dists/stable/main/binary-$TERMUX_ARCH/Packages"

download_termux_deb() {
    local package="$1"
    local filename
    filename="$(awk -v pkg="$package" '
        BEGIN { RS=""; FS="\n" }
        {
            found = 0
            for (i = 1; i <= NF; i++) if ($i == "Package: " pkg) found = 1
            if (found) {
                for (i = 1; i <= NF; i++) {
                    if ($i ~ /^Filename: /) {
                        sub(/^Filename: /, "", $i)
                        print $i
                        exit
                    }
                }
            }
        }' "$PACKAGES_INDEX")"
    if [[ -z "$filename" ]]; then
        echo "Package not found in Termux index: $package" >&2
        exit 1
    fi
    echo "  -> $package : $filename"
    curl -fsSL -o "$WORK_DIR/$(basename "$filename")" "$TERMUX_APT_BASE/$filename"
}

download_termux_deb libandroid-shmem
download_termux_deb libtalloc

find_deb() {
    local pattern="$1"
    local result
    result="$(find "$WORK_DIR" -type f -name "$pattern" -print -quit)"
    if [[ -z "$result" ]]; then
        echo "Termux build output not found: $pattern" >&2
        exit 1
    fi
    printf '%s\n' "$result"
}

STAGE_DIR="$WORK_DIR/proot-runtime"
mkdir -p "$STAGE_DIR"

# Extraction de PRoot (généré par Docker) et des librairies (téléchargées)
for package in proot libtalloc libandroid-shmem; do
    deb="$(find_deb "${package}_*_${TERMUX_ARCH}.deb")"
    dpkg-deb -x "$deb" "$STAGE_DIR"
done

TERMUX_PREFIX="$STAGE_DIR/data/data/com.termux/files/usr"
PROOT_BINARY="$TERMUX_PREFIX/bin/proot"
PROOT_LOADER="$TERMUX_PREFIX/libexec/proot/loader"
TALLOC_LIBRARY="$(find "$TERMUX_PREFIX/lib" -type f -name 'libtalloc.so.*' -print -quit)"
SHMEM_LIBRARY="$(find "$TERMUX_PREFIX/lib" -type f -name 'libandroid-shmem.so*' -print -quit)"

for required in "$PROOT_BINARY" "$PROOT_LOADER" "$TALLOC_LIBRARY" "$SHMEM_LIBRARY"; do
    if [[ ! -f "$required" ]]; then
        echo "Required file missing from Termux packages: $required" >&2
        exit 1
    fi
done

mkdir -p "$OUTPUT_DIR"
rm -f \
    "$OUTPUT_DIR/libproot.so" \
    "$OUTPUT_DIR/libproot_loader.so" \
    "$OUTPUT_DIR/libtalloc.so" \
    "$OUTPUT_DIR/libandroid-shmem.so"

cp "$PROOT_BINARY" "$OUTPUT_DIR/libproot.so"
cp "$PROOT_LOADER" "$OUTPUT_DIR/libproot_loader.so"
cp "$TALLOC_LIBRARY" "$OUTPUT_DIR/libtalloc.so"
cp "$SHMEM_LIBRARY" "$OUTPUT_DIR/libandroid-shmem.so"

chmod 755 \
    "$OUTPUT_DIR/libproot.so" \
    "$OUTPUT_DIR/libproot_loader.so" \
    "$OUTPUT_DIR/libtalloc.so" \
    "$OUTPUT_DIR/libandroid-shmem.so"

patchelf --set-soname libtalloc.so "$OUTPUT_DIR/libtalloc.so"
patchelf --set-soname libandroid-shmem.so "$OUTPUT_DIR/libandroid-shmem.so"
patchelf --set-rpath '$ORIGIN' "$OUTPUT_DIR/libproot.so"

while IFS= read -r dependency; do
    case "$dependency" in
        libtalloc.so*)
            patchelf --replace-needed "$dependency" libtalloc.so "$OUTPUT_DIR/libproot.so"
            ;;
        libandroid-shmem.so*)
            patchelf --replace-needed "$dependency" libandroid-shmem.so "$OUTPUT_DIR/libproot.so"
            ;;
    esac
done < <(patchelf --print-needed "$OUTPUT_DIR/libproot.so")

needed_libraries="$(patchelf --print-needed "$OUTPUT_DIR/libproot.so")"
for library in libtalloc.so libandroid-shmem.so; do
    if ! grep -qx "$library" <<<"$needed_libraries"; then
        echo "The PRoot binary does not link to the packaged $library." >&2
        exit 1
    fi
done

echo "Built Termux PRoot for $ANDROID_ABI in $OUTPUT_DIR"
