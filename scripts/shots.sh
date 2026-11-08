#!/usr/bin/env bash
# Deterministic desktop screenshots without synthetic input.
#
# This session runs Wayland: java.awt.Robot and XTEST can capture the screen but their clicks are
# discarded by the compositor. The desktop already accepts BAKI_VIEW to open a specific surface, so
# each shot launches the app in that state instead of clicking through the UI.
#
#   ./scripts/shots.sh [outdir]
#
# Writes overview.png, entries.png, shifts.png, routes.png, compare.png, year.png, statement.png,
# settings.png and restores a normal launch when done. Screenshots contain personal data: keep the
# output directory out of git (build/shots is ignored).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$ROOT/build/shots}"
APP="${BAKI_APP:-$HOME/.local/opt/baki/bin/Baki}"
JAVA="${JAVA_BIN:-$ROOT/../.tools/jdk21/bin/java}"
[ -x "$APP" ] || { echo "app not installed at $APP (build first or set BAKI_APP)"; exit 1; }
[ -x "$JAVA" ] || JAVA="java"
mkdir -p "$OUT"

kill_app() { pkill -9 -x Baki 2>/dev/null || true; }
restore() { kill_app; sleep 1; (DISPLAY="${DISPLAY:-:0}" nohup "$APP" >/dev/null 2>&1 &) || true; }
trap restore EXIT

for view in overview entries shifts routes compare year statement settings; do
  kill_app; sleep 1
  env_view=""
  [ "$view" = "overview" ] || env_view="$view"
  ( export DISPLAY="${DISPLAY:-:0}" BAKI_VIEW="$env_view"; nohup "$APP" >/dev/null 2>&1 & )
  sleep 4
  DISPLAY="${DISPLAY:-:0}" "$JAVA" "$ROOT/scripts/DeskShot.java" "$OUT/$view.png" >/dev/null
  echo "  $OUT/$view.png"
done
echo "done — $OUT"
