#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/out"
rm -rf "$OUT"
mkdir -p "$OUT"
find "$ROOT/src/main/java" -name '*.java' -print | sort | xargs javac --release 21 -d "$OUT"
exec java -cp "$OUT" dev.glyphy.Main "$@"
