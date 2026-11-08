#!/usr/bin/env bash
# Resolves Fabric toolchain versions for Minecraft 1.21.11, then exports the
# Mojang-mapped Minecraft jar, its decompiled sources and all libraries so the
# mod can be compile-checked offline.
set -u
MC=1.21.11
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=$ROOT/out
mkdir -p "$OUT/logs" "$OUT/tools"
cd "$ROOT/tools/devlibs"

curl -s "https://meta.fabricmc.net/v2/versions/loader/$MC" > "$OUT/logs/loader-meta.json"
LOADER=$(python3 -c "import json;d=json.load(open('$OUT/logs/loader-meta.json'));print([x['loader']['version'] for x in d if x['loader'].get('stable')][0])")
curl -s https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml > "$OUT/logs/fabric-api-meta.xml"
FAPI=$(grep -o "<version>[^<]*+$MC</version>" "$OUT/logs/fabric-api-meta.xml" | sed 's/<[^>]*>//g' | tail -1)
curl -s https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml > "$OUT/logs/loom-meta.xml"
echo "loader=$LOADER fabric_api=$FAPI" | tee "$OUT/logs/resolved.txt"

ok=0
for combo in "1.14-SNAPSHOT:9.2.1" "1.13-SNAPSHOT:9.2.1" "1.15-SNAPSHOT:9.2.1" "1.12-SNAPSHOT:9.1.0" "1.11-SNAPSHOT:8.14.3"; do
  LOOM=${combo%%:*}; GV=${combo##*:}
  echo "=== trying loom $LOOM gradle $GV"
  if [ ! -d "/tmp/gradle-$GV" ]; then
    curl -sL "https://services.gradle.org/distributions/gradle-$GV-bin.zip" -o /tmp/g.zip && unzip -q /tmp/g.zip -d /tmp && rm /tmp/g.zip
  fi
  cat > gradle.properties <<EOF
org.gradle.jvmargs=-Xmx3G
minecraft_version=$MC
loader_version=$LOADER
fabric_version=$FAPI
loom_version=$LOOM
EOF
  if "/tmp/gradle-$GV/bin/gradle" --no-daemon --stacktrace genSources exportDevLibs > "$OUT/logs/attempt-$LOOM.log" 2>&1; then
    ok=1
    printf "minecraft_version=%s\nloader_version=%s\nfabric_version=%s\nloom_version=%s\ngradle_version=%s\n" "$MC" "$LOADER" "$FAPI" "$LOOM" "$GV" > "$OUT/versions.properties"
    break
  fi
done

if [ "$ok" = 1 ]; then
  find "$HOME/.gradle" "$ROOT/tools/devlibs/.gradle" -name "*minecraft*sources.jar" 2>/dev/null | while read -r f; do cp "$f" "$OUT/compile/"; done
  find "$HOME/.gradle" -name "vineflower*.jar" 2>/dev/null | head -1 | while read -r f; do cp "$f" "$OUT/tools/vineflower.jar"; done
  find "$HOME/.gradle" "$ROOT/tools/devlibs/.gradle" -name "*.jar" 2>/dev/null | grep -i minecraft | sed "s|$HOME||" > "$OUT/logs/mc-jars.txt"
  ls -la "$OUT/compile" "$OUT/runtime" > "$OUT/logs/listing.txt"
fi
echo "done ok=$ok"
