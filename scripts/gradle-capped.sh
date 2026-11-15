#!/usr/bin/env bash
# Run Gradle inside a 6G cgroup, serialized, with the build JVM caps from gradle.properties.
#   ./scripts/gradle-capped.sh :composeApp:desktopTest
#   BAKI_KEEP_DAEMONS=1 ./scripts/gradle-capped.sh :composeApp:assembleRelease
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Local toolchain, unless the caller already provided one. systemd-run inherits these.
TOOLS="${BAKI_TOOLS:-$ROOT/../.tools}"
export JAVA_HOME="${JAVA_HOME:-$TOOLS/jdk21}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$TOOLS/gradle}"
export ANDROID_HOME="${ANDROID_HOME:-$TOOLS/sdk}"
export PATH="$JAVA_HOME/bin:$PATH"

if [[ $# -eq 0 ]]; then
  echo "usage: $0 <gradle task...>" >&2
  exit 2
fi

MEM_MAX="${BAKI_MEMORY_MAX:-6G}"
MEM_HIGH="${BAKI_MEMORY_HIGH:-5G}"
SWAP_MAX="${BAKI_SWAP_MAX:-2G}"
LOCK="${BAKI_GRADLE_LOCK:-/tmp/baki-compose-gradle.lock}"
KEEP_DAEMONS="${BAKI_KEEP_DAEMONS:-0}"

avail_mb="$(awk '/MemAvailable:/ {printf "%d", $2/1024}' /proc/meminfo)"
if (( avail_mb < 3000 )); then
  echo "warning: only ${avail_mb} MiB RAM available; close browser/heavy apps before this build." >&2
fi
if (( avail_mb < 1500 )); then
  echo "refusing to start: less than 1.5 GiB available; the cage cannot save the desktop." >&2
  exit 75
fi

# Only one Gradle build may run at a time. This also prevents two 6G cages stacking.
exec 9>"$LOCK"
if ! flock -w "${BAKI_LOCK_WAIT:-1800}" 9; then
  echo "refusing to start: another capped Gradle build holds $LOCK" >&2
  exit 75
fi

cleanup() {
  if [[ "$KEEP_DAEMONS" != "1" ]]; then
    ./gradlew --stop >/dev/null 2>&1 || true
    pkill -f '[K]otlinCompileDaemon' >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

if ! command -v systemd-run >/dev/null 2>&1; then
  echo "warning: systemd-run unavailable; running uncaged. Caps still apply." >&2
  ./gradlew --no-daemon "$@"
  exit $?
fi

UNIT="baki-gradle-$$"
PEAK_LOG="$(mktemp /tmp/baki-gradle-peak.XXXXXX)"
(
  while sleep 1; do
    cg="$(systemctl --user show "$UNIT.scope" -p ControlGroup --value 2>/dev/null || true)"
    [[ -z "$cg" ]] && continue
    peak="$(cat "/sys/fs/cgroup${cg}/memory.peak" 2>/dev/null || true)"
    [[ -n "$peak" ]] && printf '%s\n' "$peak" >> "$PEAK_LOG"
  done
) &
MONITOR=$!

echo "==> capped build: max=${MEM_MAX}, high=${MEM_HIGH}, swap=${SWAP_MAX}, unit=${UNIT}"
set +e
systemd-run --user --scope --quiet --unit="$UNIT" \
  -p MemoryMax="$MEM_MAX" \
  -p MemoryHigh="$MEM_HIGH" \
  -p MemorySwapMax="$SWAP_MAX" \
  -- ./gradlew --no-daemon "$@"
status=$?
set -e
kill "$MONITOR" >/dev/null 2>&1 || true
wait "$MONITOR" >/dev/null 2>&1 || true
peak="$(sort -n "$PEAK_LOG" 2>/dev/null | tail -1 || true)"
rm -f "$PEAK_LOG"
if [[ -n "$peak" ]]; then
  printf '==> cgroup peak: %s MiB\n' "$((peak / 1024 / 1024))"
fi
if (( status == 137 )); then
  echo "==> build was killed by the memory cage (137), not by the machine OOM killer." >&2
fi
exit "$status"
