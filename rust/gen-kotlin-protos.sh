#!/usr/bin/env bash
#
# Regenerates the protobuf Java/Kotlin gencode that the :logic module checks in
# under logic/src/main/java. Run this after editing anything in protos/; the
# Rust side regenerates its own bindings on every cargo build.
#
# Requires: protoc.

set -euo pipefail

cd "$(dirname "$0")"

OUT="../logic/src/main/java"
# The checked-in gencode came from protoc 35.x, which matches the protobuf
# runtime pinned in gradle/libs.versions.toml. Another major version rewrites
# every generated file with a different gencode header.
EXPECTED_MAJOR=35

command -v protoc >/dev/null || {
  echo "error: protoc not found in PATH" >&2
  exit 1
}

version="$(protoc --version | awk '{print $2}')"
echo ">>> protoc $version"
if [[ "${version%%.*}" != "$EXPECTED_MAJOR" ]]; then
  echo "warning: expected protoc ${EXPECTED_MAJOR}.x; ${version} will rewrite all generated files" >&2
fi

protoc --java_out="$OUT" --kotlin_out="$OUT" -I protos protos/*.proto

echo ">>> regenerated into $OUT/com/bugenzhao/mnga/protos"
