#!/usr/bin/env bash
# Open the verification AVD in a normal window and optionally install the signed app.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${TOOLS:-/home/min/Documents/deepseek-harness/default-workspace/.tools}"
export JAVA_HOME="${JAVA_HOME:-$TOOLS/jdk21}"
export PATH="$JAVA_HOME/bin:$TOOLS/sdk/platform-tools:$TOOLS/sdk/emulator:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$TOOLS/sdk}"
AVD="${AVD:-baki_api36}"
APK="${1:-$ROOT/dist/baki-compose-0.44.6.apk}"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"

if ! "$ANDROID_HOME/platform-tools/adb" -s "$SERIAL" get-state 2>/dev/null | grep -q device; then
  "$ANDROID_HOME/emulator/emulator" -avd "$AVD" -port 5554 \
    -gpu swiftshader_indirect -no-snapshot -no-audio -no-boot-anim \
    >/tmp/baki-emulator-gui.log 2>&1 &
  echo $! >/tmp/baki-emulator-gui.pid
  "$ANDROID_HOME/platform-tools/adb" -s "$SERIAL" wait-for-device
  for i in $(seq 1 180); do
    [ "$("$ANDROID_HOME/platform-tools/adb" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
    sleep 2
  done
fi

if [ -f "$APK" ]; then
  "$ANDROID_HOME/platform-tools/adb" -s "$SERIAL" install -r "$APK" >/tmp/baki-emulator-install.log 2>&1 || true
  "$ANDROID_HOME/platform-tools/adb" -s "$SERIAL" shell monkey -p app.baki.compose 1 >/dev/null 2>&1 || true
fi

echo "Emulator $SERIAL is open. Close the window or run: adb -s $SERIAL emu kill"
