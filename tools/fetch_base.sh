#!/usr/bin/env bash
# Downloads the released module jars (the baseline the overlay modules in jars/ build on) into base/.
#   tools/fetch_base.sh [release tag]     default: upload-1
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TAG=${1:-upload-1}
mkdir -p "$ROOT/base"
TMP=$(mktemp -d)
curl -fsSL --retry 3 -o "$TMP/all.zip" "https://github.com/rsen91137-ctrl/heliosphere/releases/download/$TAG/All.Classfiy.Files.zip"
unzip -oq "$TMP/all.zip" -d "$ROOT/base"
rm -rf "$TMP"
ls "$ROOT/base"
