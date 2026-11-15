#!/usr/bin/env bash
# Build, sign and publish a release APK to the local server's /apk endpoint.
#   ./scripts/release.sh 0.1.8
set -euo pipefail

VERSION="${1:?usage: ./scripts/release.sh <version, e.g. 0.1.8>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="$ROOT/../.tools"
BT="$TOOLS/sdk/build-tools/36.0.0"
KS="$TOOLS/keystore/baki-release.jks"

export JAVA_HOME="${JAVA_HOME:-$TOOLS/jdk21}"
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="$TOOLS/gradle"
export ANDROID_HOME="$TOOLS/sdk"
export ANDROID_USER_HOME="$TOOLS/fakehome/.android"
export ANDROID_SDK_HOME="$TOOLS/fakehome"
export JAVA_TOOL_OPTIONS="-Duser.home=$TOOLS/fakehome"

cd "$ROOT"

# version + code
CODE=$(( $(grep -oE 'versionCode = [0-9]+' composeApp/build.gradle.kts | grep -oE '[0-9]+') + 1 ))
python3 - "$VERSION" "$CODE" <<'PY'
import re, sys
v, c = sys.argv[1], sys.argv[2]
p = 'composeApp/build.gradle.kts'
s = open(p, encoding='utf-8').read()
s = re.sub(r'versionCode = \d+', f'versionCode = {c}', s)
s = re.sub(r'versionName = "[^"]+"', f'versionName = "{v}"', s)
open(p, 'w', encoding='utf-8').write(s)
PY
echo "==> building $VERSION (code $CODE)"
./scripts/gradle-capped.sh :composeApp:assembleRelease -q

mkdir -p dist
"$BT/zipalign" -p -f 4 composeApp/build/outputs/apk/release/composeApp-release-unsigned.apk dist/aligned.apk
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:baki-local-2026 --key-pass pass:baki-local-2026 \
  --ks-key-alias baki --min-sdk-version 24 \
  --out "dist/baki-compose-$VERSION.apk" dist/aligned.apk
rm -f dist/aligned.apk

cp "dist/baki-compose-$VERSION.apk" ../grab-tracker/dist-android/
"$BT/apksigner" verify --print-certs "dist/baki-compose-$VERSION.apk" | grep -E "SHA-256" || true

echo
echo "published: http://192.168.1.18:8787/apk    (always the newest)"
echo "page:      http://192.168.1.18:8787/dl"
