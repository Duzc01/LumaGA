#!/usr/bin/env bash
#
# Cross-compiles the `logic` crate into liblogic.so for every ABI the app ships
# and writes the results straight into the :logic module's jniLibs, where
# Logic.kt's System.loadLibrary("logic") picks them up.
#
# The .so files are checked in, so run this after changing anything under rust/
# and commit the refreshed binaries along with the source change.
#
# Requires: cargo-ndk, protoc, an Android NDK, plus perl and make (the Android
# builds compile OpenSSL from source, which dominates the build time).

set -euo pipefail

cd "$(dirname "$0")"

JNI_LIBS="../logic/src/main/jniLibs"
# Keep in sync with minSdk in app/build.gradle.kts (cargo-ndk defaults to 21).
PLATFORM=26
ABIS=(arm64-v8a x86_64 x86)

for tool in cargo protoc; do
  command -v "$tool" >/dev/null || {
    echo "error: $tool not found in PATH" >&2
    exit 1
  }
done
cargo ndk --version >/dev/null 2>&1 || {
  echo "error: cargo-ndk not installed; run: cargo install --locked cargo-ndk" >&2
  exit 1
}

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  # `|| true` so pipefail does not swallow the message below when there is no
  # ndk directory at all.
  ANDROID_NDK_HOME="$(ls -d "$sdk"/ndk/* 2>/dev/null | sort -V | tail -1 || true)"
  [[ -n "$ANDROID_NDK_HOME" ]] || {
    echo "error: no NDK under $sdk/ndk; set ANDROID_NDK_HOME" >&2
    exit 1
  }
  export ANDROID_NDK_HOME
fi
echo ">>> NDK: $ANDROID_NDK_HOME"

targets=()
for abi in "${ABIS[@]}"; do targets+=(-t "$abi"); done

cargo ndk "${targets[@]}" -P "$PLATFORM" -o "$JNI_LIBS" build --release --package logic

echo ">>> liblogic.so"
for abi in "${ABIS[@]}"; do
  so="$JNI_LIBS/$abi/liblogic.so"
  [[ -f "$so" ]] || {
    echo "error: $so was not produced" >&2
    exit 1
  }
  # Release builds land around 16-19 MB. A ~180 MB file means a debug profile
  # slipped in.
  echo "    $abi  $(du -h "$so" | cut -f1)"
done
