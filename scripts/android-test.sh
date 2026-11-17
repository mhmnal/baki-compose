#!/usr/bin/env bash
# Local Android verification lane. Starts the API 36 AVD, installs a .verify
# build, captures a smoke launch, then runs the Android instrumentation tests.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${TOOLS:-/home/min/Documents/deepseek-harness/default-workspace/.tools}"
export JAVA_HOME="${JAVA_HOME:-$TOOLS/jdk21}"
export PATH="$JAVA_HOME/bin:$TOOLS/sdk/platform-tools:$TOOLS/sdk/emulator:$PATH"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$TOOLS/gradle}"
export ANDROID_HOME="${ANDROID_HOME:-$TOOLS/sdk}"
AVD="${AVD:-baki_api36}"
OUT="${OUT:-/tmp/baki-android-lane}"; mkdir -p "$OUT"
ADB="$ANDROID_HOME/platform-tools/adb"
EMU="$ANDROID_HOME/emulator/emulator"
APP_ID="app.baki.compose.verify"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"

adb_cmd() { "$ADB" -s "$SERIAL" "$@"; }

start_emulator() {
  if "$ADB" -s "$SERIAL" get-state 2>/dev/null | grep -q device; then return; fi
  if [ "$SERIAL" != "emulator-5554" ]; then
    echo "Device $SERIAL is not connected" >&2; exit 1
  fi
  "$EMU" -avd "$AVD" -port 5554 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot >"$OUT/emulator.log" 2>&1 &
  echo $! >"$OUT/emulator.pid"
  "$ADB" -s "$SERIAL" wait-for-device
  for i in $(seq 1 180); do
    [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && return
    sleep 2
  done
  echo "emulator boot timeout" >&2; exit 1
}

stop_emulator() {
  if [ -f "$OUT/emulator.pid" ]; then
    kill "$(cat "$OUT/emulator.pid")" 2>/dev/null || true
    rm -f "$OUT/emulator.pid"
  fi
}

cd "$ROOT"
start_emulator
trap 'stop_emulator' EXIT

./scripts/gradle-capped.sh :composeApp:assembleDebug -PbakiVerify=true --offline >"$OUT/build.log" 2>&1
APK="$ROOT/composeApp/build/outputs/apk/debug/composeApp-debug.apk"
adb_cmd install -r "$APK" >"$OUT/install.log"
adb_cmd shell pm grant "$APP_ID" android.permission.ACCESS_FINE_LOCATION || true
adb_cmd shell pm grant "$APP_ID" android.permission.ACCESS_COARSE_LOCATION || true
adb_cmd shell am force-stop "$APP_ID"
adb_cmd shell am start -n "$APP_ID/app.baki.MainActivity" >/dev/null
sleep 9
adb_cmd exec-out screencap -p >"$OUT/smoke-launch.png"
adb_cmd logcat -d -v time >"$OUT/logcat.txt"

ANDROID_SERIAL="$SERIAL" ./scripts/gradle-capped.sh :composeApp:connectedDebugAndroidTest -PbakiVerify=true >"$OUT/instrumentation.log" 2>&1
echo "Android lane passed. Artifacts: $OUT"
echo "  smoke: $OUT/smoke-launch.png"
echo "  tests: $OUT/instrumentation.log"
