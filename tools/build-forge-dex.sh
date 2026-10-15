#!/bin/bash
# Compile ForgeSource against Mihon stubs and pack only the extension classes into a dex.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-/home/ubuntu/android-sdk}"
BT="$SDK/build-tools/35.0.0"
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
OUT="$ROOT/parser-core/src/main/resources/mihon"
LIB="$ROOT/build/forge-libs"
WORK="$(mktemp -d)"
SLOT=32768
MARKER='@@EXTFORGE_CONFIG_V1@@'

mkdir -p "$LIB" "$OUT" "$WORK/stubs" "$WORK/html" "$WORK/src" "$WORK/dex"

fetch() {
  local url="$1" dest="$2"
  if [[ ! -f "$dest" ]]; then
    curl -fsSL -o "$dest" "$url"
  fi
}

fetch "https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.jar" "$LIB/okhttp.jar"
fetch "https://repo1.maven.org/maven2/com/squareup/okio/okio-jvm/3.6.0/okio-jvm-3.6.0.jar" "$LIB/okio.jar"
fetch "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.0.21/kotlin-stdlib-2.0.21.jar" "$LIB/kotlin-stdlib.jar"
fetch "https://repo1.maven.org/maven2/org/jsoup/jsoup/1.18.3/jsoup-1.18.3.jar" "$LIB/jsoup.jar"

python3 - "$WORK/src/ForgeConfig.java" "$MARKER" "$SLOT" << 'PY'
import sys
path, marker, slot = sys.argv[1], sys.argv[2], int(sys.argv[3])
literal = marker + (" " * (slot - len(marker)))
if len(literal) != slot:
    raise SystemExit("slot length mismatch")
with open(path, "w", encoding="utf-8") as handle:
    handle.write("package ex.extforge.runtime;\n")
    handle.write("public final class ForgeConfig {\n")
    handle.write("    private ForgeConfig() {}\n")
    handle.write("    public static final String JSON = \"")
    handle.write(literal)
    handle.write("\";\n")
    handle.write("    public static final int SLOT = %d;\n" % slot)
    handle.write("}\n")
PY

find "$ROOT/parser-core/src/ext/stubs" -name '*.java' > "$WORK/stubs.list"
javac --release 8 -encoding UTF-8 -cp "$ANDROID_JAR:$LIB/okhttp.jar:$LIB/okio.jar:$LIB/kotlin-stdlib.jar" \
  -d "$WORK/stubs" @"$WORK/stubs.list"
jar cf "$WORK/stubs.jar" -C "$WORK/stubs" .

javac --release 8 -encoding UTF-8 -cp "$LIB/jsoup.jar" \
  -d "$WORK/html" "$ROOT/parser-core/src/main/java/com/extforge/core/parse/ForgeHtml.java"

CP="$WORK/html:$WORK/stubs.jar:$ANDROID_JAR:$LIB/okhttp.jar:$LIB/okio.jar:$LIB/kotlin-stdlib.jar:$LIB/jsoup.jar"
javac --release 8 -encoding UTF-8 -g:none -cp "$CP" \
  -d "$WORK/src" \
  "$WORK/src/ForgeConfig.java" \
  "$ROOT/parser-core/src/ext/java/ex/extforge/runtime/ForgeSource.java"

mapfile -t CLASS_FILES < <(find "$WORK/html" "$WORK/src" -name '*.class')
"$BT/d8" --min-api 26 --lib "$ANDROID_JAR" --lib "$WORK/stubs.jar" \
  --lib "$LIB/okhttp.jar" --lib "$LIB/okio.jar" --lib "$LIB/kotlin-stdlib.jar" --lib "$LIB/jsoup.jar" \
  --output "$WORK/dex" \
  "${CLASS_FILES[@]}"

python3 - "$WORK/dex/classes.dex" "$MARKER" "$SLOT" << 'PY'
import sys
data = open(sys.argv[1], "rb").read()
marker = sys.argv[2].encode()
slot = int(sys.argv[3])
start = data.find(marker)
if start < 0 or data.find(marker, start + 1) >= 0:
    raise SystemExit("marker missing or duplicated")
if start + slot >= len(data) or data[start + slot] != 0:
    raise SystemExit("slot is not a dex string")
print("forge dex", len(data), "marker", start)
PY

cp "$WORK/dex/classes.dex" "$OUT/forge-source.dex"
echo "wrote $OUT/forge-source.dex"
