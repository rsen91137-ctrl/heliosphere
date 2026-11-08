#!/usr/bin/env bash
# Builds the client and dedicated-server bundles from build/dist and smoke-tests the server.
set -u
VER=0.1.0
OUT=out/jars
FAPI=$(ls $OUT/fabric-api-*.jar | head -1)
LOADER=$(grep '^loader_version=' gradle.properties | cut -d= -f2)
INSTALLER=$(curl -fsSL --retry 5 https://meta.fabricmc.net/v2/versions/installer | python3 -c "import json,sys; print([v['version'] for v in json.load(sys.stdin) if v['stable']][0])")
echo "loader $LOADER installer $INSTALLER"

# ---------------------------------------------------------------- client
C=/tmp/pkg/client; rm -rf /tmp/pkg; mkdir -p $C/mods $C/resourcepacks
cp build/dist/*.jar "$FAPI" $C/mods/
cp $OUT/Heliosphere-HD-Resources-32x.zip $C/resourcepacks/ 2>/dev/null || true
cat > $C/安装说明.txt <<'TXT'
Heliosphere 客户端（你和朋友每人都装）
1. 安装 Minecraft 1.21.11 与 Fabric Loader（https://fabricmc.net/use/installer/ ，选 1.21.11）。
2. 把 mods 文件夹里的全部 jar 放进 .minecraft/mods（官方启动器）或对应版本的 mods 文件夹（PCL/HMCL 开启版本隔离时）。
3. 把 resourcepacks 里的 zip 放进 .minecraft/resourcepacks，游戏里 选项→资源包 启用（可选，高清 32x 贴图）。
4. 单人：新建世界 → 更多世界选项 → 世界类型「太阳系」。
5. 联机：连接朋友开的 Heliosphere 服务端即可，客户端 mods 必须与服务端完全一致。
TXT
(cd $C && zip -qr "$OLDPWD/$OUT/Heliosphere-Client-$VER.zip" .)

# ---------------------------------------------------------------- server
S=/tmp/pkg/server; mkdir -p $S/mods
cp build/dist/*.jar "$FAPI" $S/mods/
curl -fsSL --retry 5 -o $S/fabric-server-launch.jar "https://meta.fabricmc.net/v2/versions/loader/1.21.11/$LOADER/$INSTALLER/server/jar"
cat > $S/server.properties <<'TXT'
# Heliosphere：太阳系预设（地球为 1/100 球面，含月球、火星等维度）
level-type=heliosphere\:solar_system
motd=Heliosphere 太阳系服务器
view-distance=10
simulation-distance=8
spawn-protection=0
max-players=10
online-mode=true
TXT
echo "eula=false" > $S/eula.txt
printf '@echo off\r\njava -Xms2G -Xmx6G -jar fabric-server-launch.jar nogui\r\npause\r\n' > $S/start.bat
printf '#!/bin/sh\ncd "$(dirname "$0")"\nexec java -Xms2G -Xmx6G -jar fabric-server-launch.jar nogui\n' > $S/start.sh; chmod +x $S/start.sh
cat > $S/开服说明.txt <<'TXT'
Heliosphere 服务端
1. 需要 Java 21（https://adoptium.net/ 下载 Temurin 21）。
2. 打开 eula.txt，阅读 Minecraft EULA（https://aka.ms/MinecraftEULA）后把 eula=false 改为 eula=true。
3. Windows 双击 start.bat；Linux/macOS 运行 ./start.sh。首次启动会自动下载原版 1.21.11 服务端（需联网）。
4. server.properties 已设好 level-type=heliosphere:solar_system。若已生成过 world 文件夹，想换成太阳系必须先删掉 world 文件夹。
5. 内存不够就把启动脚本里的 -Xmx6G 改小（至少 3G）。
6. 朋友连接：同一局域网用你的内网 IP，外网需要端口转发 25565 或使用内网穿透工具。
7. 客户端 mods 必须与服务端 mods 文件夹里的 jar 完全一致（资源包只装在客户端）。
TXT
(cd $S && zip -qr "$OLDPWD/$OUT/Heliosphere-Server-$VER.zip" .)

# ---------------------------------------------------------------- smoke test
T=/tmp/srvtest; rm -rf $T; cp -r $S $T; echo "eula=true" > $T/eula.txt
cd $T
rm -f in; mkfifo in
(java -Xmx3G -jar fabric-server-launch.jar nogui < in > server-run.log 2>&1; echo "exit $?" >> server-run.log) &
exec 3>in
ok=0
for i in $(seq 1 120); do
  if grep -q 'Done (' server-run.log; then ok=1; break; fi
  if grep -q '^exit ' server-run.log; then break; fi
  sleep 5
done
sleep 20
echo "stop" >&3
for i in $(seq 1 30); do grep -q '^exit ' server-run.log && break; sleep 2; done
exec 3>&-
cd - >/dev/null
cp $T/server-run.log out/logs/server-run.log 2>/dev/null || true
[ -f $T/logs/latest.log ] && cp $T/logs/latest.log out/logs/server-latest.log
echo "server smoke test ok=$ok" | tee out/logs/server-smoke.txt
grep -E 'ERROR|Exception' $T/server-run.log | head -40 >> out/logs/server-smoke.txt || true
exit 0
