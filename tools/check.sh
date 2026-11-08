#!/usr/bin/env bash
# Offline compile check against the exported 1.21.11 dev libraries (../devlibs).
# Usage: tools/check.sh [module ...]   (default: all, in dependency order)
ROOT=$(cd "$(dirname "$0")/.." && pwd)
LIBS=${DEVLIBS:-$ROOT/../devlibs/compile}
OUT=/tmp/hcheck
CP=$(ls "$LIBS"/*.jar | grep -v sources | tr '\n' ':')
MODS=${*:-core sphereworld industry planets rocketry guide testmod}
status=0
for m in $MODS; do
  rm -rf "$OUT/$m"; mkdir -p "$OUT/$m"
  files=$(find "$ROOT/$m/src/main/java" -name '*.java')
  [ -z "$files" ] && continue
  DEP=$(for d in core sphereworld industry planets rocketry guide; do [ -d "$OUT/$d" ] && printf "%s:" "$OUT/$d"; done)
  if javac -J-Xmx2g -proc:none -nowarn -Xmaxerrs 400 -encoding UTF-8 --release 21 -cp "$CP$DEP" -d "$OUT/$m" $files 2> "$OUT/$m.log"; then
    echo "[ok] $m"
  else
    echo "[FAIL] $m: $(grep -c 'error:' "$OUT/$m.log") errors (see $OUT/$m.log)"; status=1
  fi
done
exit $status
