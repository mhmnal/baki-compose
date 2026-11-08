#!/usr/bin/env bash
# Fast desktop loop: rebuild the distributable, relaunch, capture stdout/stderr + JVM crash file.
#   ./scripts/dev-desktop.sh            launch current build
#   ./scripts/dev-desktop.sh --build    rebuild first
#   ./scripts/dev-desktop.sh --follow   keep tailing the log in the foreground
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="$ROOT/../.tools"
export JAVA_HOME="$TOOLS/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="$TOOLS/gradle"
export ANDROID_HOME="$TOOLS/sdk"
APP="${BAKI_APP:-$ROOT/composeApp/build/compose/binaries/main/app/Baki/bin/Baki}"
LOG_DIR="$ROOT/build/logs"
BUILD=0
FOLLOW=0
for a in "$@"; do
  case "$a" in
    --build) BUILD=1 ;;
    --follow) FOLLOW=1 ;;
    *) echo "usage: $0 [--build] [--follow]" >&2; exit 2 ;;
  esac
done
mkdir -p "$LOG_DIR"
if [ "$BUILD" = 1 ] || [ ! -x "$APP" ]; then
  ( cd "$ROOT" && ./gradlew :composeApp:createDistributable --no-daemon -q )
fi
pkill -9 -x Baki 2>/dev/null || true
sleep 0.5
LOG="$LOG_DIR/desktop.log"
: > "$LOG"
# JVM fatal crashes go next to the log instead of stderr only.
JAVA_TOOL_OPTIONS="-XX:ErrorFile=$LOG_DIR/hs_err_pid%p.log -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$LOG_DIR ${JAVA_TOOL_OPTIONS:-}" \
  nohup "$APP" >>"$LOG" 2>&1 &
PID=$!
echo "$PID" > "$LOG_DIR/desktop.pid"
for _ in $(seq 1 20); do pgrep -x Baki >/dev/null && break; sleep 0.25; done
echo "pid: $PID"
echo "log: $LOG"
echo "crash: $LOG_DIR/hs_err_pid*.log"
if [ "$FOLLOW" = 1 ]; then tail -n +1 -f "$LOG"; fi
