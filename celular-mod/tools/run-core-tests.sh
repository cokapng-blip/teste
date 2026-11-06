#!/usr/bin/env bash
# Compila e executa os testes do núcleo (Java puro, sem Minecraft nem Gradle).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${TMPDIR:-/tmp}/celular-core-build"
rm -rf "$OUT" && mkdir -p "$OUT"
javac -encoding UTF-8 --release 21 -Xlint:all -d "$OUT" \
  $(find src/main/java/com/celularmod/core src/main/java/com/celularmod/browser src/test/java -name '*.java')
java -Dhttp.nonProxyHosts='*' -Djava.net.useSystemProxies=false -Dhttps.proxyHost= -Dhttp.proxyHost= -cp "$OUT" com.celularmod.core.CoreSelfTest
