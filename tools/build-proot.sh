#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_ABI="${1:-arm64-v8a}"

case "$ANDROID_ABI" in
    arm64-v8a) TERMUX_ARCH="aarch64" ;;
    armeabi-v7a) TERMUX_ARCH="arm" ;;
    *) echo "Unsupported Android ABI: $ANDROID_ABI" >&2; exit 1 ;;
esac

OUTPUT_DIR="$ROOT_DIR/native/src/main/jniLibs/$ANDROID_ABI"
TERMUX_APT_BASE="https://packages.termux.dev/apt/termux-main"

command -v curl >/dev/null 2>&1 || { echo "curl is required." >&2; exit 1; }
command -v dpkg-deb >/dev/null 2>&1 || { echo "dpkg-deb is required." >&2; exit 1; }
command -v patchelf >/dev/null 2>&1 || { echo "patchelf is required." >&2; exit 1; }

WORK_DIR="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/void-linux-termux-runtime-$ANDROID_ABI"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR" "$OUTPUT_DIR"

PACKAGES_INDEX="$WORK_DIR/Packages"
echo "Fetching official Termux package index for $TERMUX_ARCH..." >&2
curl --fail --location --retry 5 --retry-delay 3 --connect-timeout 30 \
    -o "$PACKAGES_INDEX" \
    "$TERMUX_APT_BASE/dists/stable/main/binary-$TERMUX_ARCH/Packages"

find_package_metadata() {
    local package="$1"
    awk -v pkg="$package" '
        BEGIN { RS=""; FS="\n" }
        $0 ~ ("(^|\n)Package: " pkg "(\n|$)") {
            filename=""; sha256=""; arch="";
            for (i=1; i<=NF; i++) {
                if ($i ~ /^Architecture: /) { sub(/^Architecture: /, "", $i); arch=$i }
                if ($i ~ /^Filename: /) { sub(/^Filename: /, "", $i); filename=$i }
                if ($i ~ /^SHA256: /) { sub(/^SHA256: /, "", $i); sha256=$i }
            }
            if ((arch == "'"$TERMUX_ARCH"'" || arch == "all") && filename != "" && sha256 != "") {
                print filename "\t" sha256
                exit
            }
        }
    ' "$PACKAGES_INDEX"
}

download_package() {
    local package="$1"
    local metadata filename sha256 actual
    metadata="$(find_package_metadata "$package")"
    if [[ -z "$metadata" ]]; then
        echo "Package $package ($TERMUX_ARCH) not found in official Termux repository." >&2
        exit 1
    fi
    IFS=$'\t' read -r filename sha256 <<< "$metadata"
    local archive="$WORK_DIR/$(basename "$filename")"
    
    # CORRECTION CRITIQUE : Redirection du log vers >&2 pour préserver stdout clean
    echo "Downloading official Termux package: $package ($TERMUX_ARCH)" >&2
    
    curl --fail --location --retry 5 --retry-delay 3 --connect-timeout 30 \
        -o "$archive" "$TERMUX_APT_BASE/$filename"
    actual="$(sha256sum "$archive" | awk '{print $1}')"
    [[ "$actual" == "$sha256" ]] || {
        echo "SHA-256 mismatch for $package: expected $sha256, got $actual" >&2
        exit 1
    }
    printf '%s\n' "$archive"
}

# Collecte stricte des chemins des paquets téléchargés
for package in proot libtalloc libandroid-shmem; do
    download_package "$package"
done > "$WORK_DIR/downloaded.txt"

STAGE_DIR="$WORK_DIR/stage"
mkdir -p "$STAGE_DIR"
while IFS= read -r deb; do
    if [[ -f "$deb" ]]; then
        dpkg-deb -x "$deb" "$STAGE_DIR"
    fi
done < "$WORK_DIR/downloaded.txt"

TERMUX_PREFIX="$STAGE_DIR/data/data/com.termux/files/usr"
PROOT_BINARY="$TERMUX_PREFIX/bin/proot"

# Tolérance structurelle : le chargeur peut s'appeler 'loader' ou 'libproot-loader.so'
PROOT_LOADER="$(find "$TERMUX_PREFIX/libexec/proot" -type f \( -name 'loader' -o -name 'libproot-loader.so' \) -print -quit)"
TALLOC_LIBRARY="$(find "$TERMUX_PREFIX/lib" -type f -name 'libtalloc.so.*' -print -quit)"
SHMEM_LIBRARY="$(find "$TERMUX_PREFIX/lib" -type f -name 'libandroid-shmem.so*' -print -quit)"

for required in "$PROOT_BINARY" "$PROOT_LOADER" "$TALLOC_LIBRARY" "$SHMEM_LIBRARY"; do
    if [[ -z "$required" || ! -f "$required" ]]; then
        echo "Required file missing or empty from Termux packages structure." >&2
        exit 1
    fi
done

rm -f "$OUTPUT_DIR"/libproot.so "$OUTPUT_DIR"/libproot_loader.so \
      "$OUTPUT_DIR"/libtalloc.so "$OUTPUT_DIR"/libandroid-shmem.so

cp "$PROOT_BINARY" "$OUTPUT_DIR/libproot.so"
cp "$PROOT_LOADER" "$OUTPUT_DIR/libproot_loader.so"
cp "$TALLOC_LIBRARY" "$OUTPUT_DIR/libtalloc.so"
cp "$SHMEM_LIBRARY" "$OUTPUT_DIR/libandroid-shmem.so"

chmod 755 "$OUTPUT_DIR"/lib*.so
patchelf --set-soname libtalloc.so "$OUTPUT_DIR/libtalloc.so"
patchelf --set-soname libandroid-shmem.so "$OUTPUT_DIR/libandroid-shmem.so"
patchelf --set-rpath '$ORIGIN' "$OUTPUT_DIR/libproot.so"

while IFS= read -r dependency; do
    case "$dependency" in
        libtalloc.so*) patchelf --replace-needed "$dependency" libtalloc.so "$OUTPUT_DIR/libproot.so" ;;
        libandroid-shmem.so*) patchelf --replace-needed "$dependency" libandroid-shmem.so "$OUTPUT_DIR/libproot.so" ;;
    esac
done < <(patchelf --print-needed "$OUTPUT_DIR/libproot.so")

needed_libraries="$(patchelf --print-needed "$OUTPUT_DIR/libproot.so")"
for library in libtalloc.so libandroid-shmem.so; do
    grep -qx "$library" <<<"$needed_libraries" || {
        echo "PRoot does not link to packaged $library." >&2
        exit 1
    }
done

case "$ANDROID_ABI" in
    arm64-v8a)
        file "$OUTPUT_DIR/libproot.so" | grep -Eq 'ELF 64-bit.*ARM aarch64' || {
            echo "libproot.so is not an ARM64 ELF." >&2; exit 1;
        }
        ;;
    armeabi-v7a)
        file "$OUTPUT_DIR/libproot.so" | grep -Eq 'ELF 32-bit.*ARM' || {
            echo "libproot.so is not an ARM32 ELF." >&2; exit 1;
        }
        ;;
esac

echo "Built verified Termux PRoot runtime for $ANDROID_ABI in $OUTPUT_DIR"
