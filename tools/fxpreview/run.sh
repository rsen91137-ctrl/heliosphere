#!/usr/bin/env bash
# Offline preview of the weapon effects (no Minecraft needed).
#   tools/fxpreview/run.sh nuke out.png [daylight 0..1] [camera distance] [t1,t2,...]
#   tools/fxpreview/run.sh atlas out.png
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=${TMPDIR:-/tmp}/fxpreview-classes
mkdir -p "$OUT"
javac -nowarn -d "$OUT" "$ROOT"/jars/01-core/src/main/java/com/heliosphere/core/fx/sim/*.java "$ROOT"/tools/fxpreview/FxPreview.java
java -Djava.awt.headless=true -cp "$OUT" FxPreview "$@"
