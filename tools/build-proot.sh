#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/void-linux-termux-packages"
OUTPUT_DIR="$ROOT_DIR/native/src/main/jniLibs/arm64-v8a"
TERMUX_PACKAGES_REF="2d31765cdab30bbf92f87c495bef6b963df168e5"

if ! command -v docker >/dev/null 2>&1; then
    echo "Docker is required to build the official Termux PRoot package." >&2
    exit 1
fi
if ! command -v dpkg-deb >/dev/null 2>&1 || ! command -v patchelf >/dev/null 2>&1; then
    echo "dpkg-deb and patchelf are required." >&2
    exit 1
fi

rm -rf "$WORK_DIR"
git init --quiet "$WORK_DIR"
git -C "$WORK_DIR" remote add origin https://github.com/termux/termux-packages.git
git -C "$WORK_DIR" fetch --quiet --depth 1 origin "$TERMUX_PACKAGES_REF"
git -C "$WORK_DIR" checkout --quiet FETCH_HEAD

cd "$WORK_DIR"
./scripts/run-docker.sh ./build-package.sh -I -C -a aarch64 proot libtalloc libandroid-shmem

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
for package in proot libtalloc libandroid-shmem; do
    deb="$(find_deb "${package}_*_aarch64.deb")"
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

echo "Built Termux PRoot for arm64-v8a in $OUTPUT_DIR"