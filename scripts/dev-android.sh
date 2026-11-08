#!/usr/bin/env bash
# Fast phone loop: build the side-by-side verify debug APK, install, launch, stream its logcat.
#   ./scripts/dev-android.sh debug     build+install+launch+logcat (package app.baki.compose.verify)
#   ./scripts/dev-android.sh logs      attach to the already-running app (default: app.baki.compose)
#   BAKI_SERIAL=192.168.1.15:42693 ./scripts/dev-android.sh debug
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="$ROOT/../.tools"
export JAVA_HOME="$TOOLS/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="$TOOLS/gradle"
export ANDROID_HOME="$TOOLS/sdk"
ADB="$TOOLS/sdk/platform-tools/adb"
SERIAL="${BAKI_SERIAL:-192.168.1.15:42693}"
LOG_DIR="$ROOT/build/logs"
mkdir -p "$LOG_DIR"
if ! "$ADB" devices | grep -q "^$SERIAL[[:space:]]"; then
  "$ADB" connect "$SERIAL" >/dev/null
fi
MODE="${1:-debug}"
if [ "$MODE" = "debug" ]; then
  ( cd "$ROOT" && ./gradlew :composeApp:assembleDebug -PbakiVerify=true --no-daemon -q )
  APK="$ROOT/composeApp/build/outputs/apk/debug/composeApp-debug.apk"
  PKG=app.baki.compose.verify
  "$ADB" -s "$SERIAL" install -r "$APK" >/dev/null
  "$ADB" -s "$SERIAL" shell am force-stop "$PKG"
  "$ADB" -s "$SERIAL" shell am start -W -n "$PKG/app.baki.MainActivity" >/dev/null
elif [ "$MODE" = "logs" ]; then
  PKG="${2:-app.baki.compose}"
else
  echo "usage: $0 [debug|logs [package]]" >&2
  exit 2
fi
sleep 1
PID="$("$ADB" -s "$SERIAL" shell pidof "$PKG" 2>/dev/null | tr -d '\r')"
LOG="$LOG_DIR/android-$(echo "$PKG" | tr '.' '-').log"
: > "$LOG"
if [ -z "$PID" ]; then
  echo "no pid for $PKG; capture unfiltered crash buffer for 10s" >&2
  timeout 10 "$ADB" -s "$SERIAL" logcat -v time > "$LOG" || true
else
  echo "pid: $PID"
  timeout "${BAKI_LOG_SECONDS:-30}" "$ADB" -s "$SERIAL" logcat -v time --pid="$PID" > "$LOG" || true
fi
echo "log: $LOG"
tail -40 "$LOG"
