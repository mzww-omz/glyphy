#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUTPUT=$($ROOT/scripts/run.sh "$ROOT/examples/hello.gly")
EXPECTED='Hello, Glyphy!
42'
if [ "$OUTPUT" != "$EXPECTED" ]; then
  printf 'expected:\n%s\n\ngot:\n%s\n' "$EXPECTED" "$OUTPUT" >&2
  exit 1
fi
printf 'ok - hello.gly\n'
