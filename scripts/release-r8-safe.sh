#!/usr/bin/env bash
# R8 release build inside a transient systemd service with a hard memory cap.
#
# Three guards:
#   1. preflight: refuse to start on low host memory or a large stray JVM
#   2. service: R8 runs in its own 6 GB cgroup, never the session cgroup
#   3. monitor: stop the service if host availability drops below 1.5 GB
#
# This does not make the host infinite. Close desktop Baki and other Java apps.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${BAKI_TOOLS:-$ROOT/../.tools}"
UNIT="${BAKI_R8_UNIT:-baki-r8-build-$(date +%s)}"
LOG="${BAKI_R8_LOG:-/tmp/baki-r8-build.log}"
UNIT_LOG="${BAKI_R8_UNIT_LOG:-/tmp/baki-r8-unit.log}"
MEM_MAX="${BAKI_R8_MEMORY_MAX:-6G}"
MEM_HIGH="${BAKI_R8_MEMORY_HIGH:-5G}"
SWAP_MAX="${BAKI_R8_SWAP_MAX:-2G}"
PREFLIGHT_MIN_MB="${BAKI_R8_PREFLIGHT_MIN_MB:-6144}"
HOST_MIN_MB="${BAKI_R8_HOST_MIN_MB:-1536}"

available_mb() {
    awk '/^MemAvailable:/ { print int($2 / 1024) }' /proc/meminfo
}

big_javas() {
    ps -C java -o pid=,rss=,args= | awk -v limit=$((1024 * 1024)) '
        $2 >= limit { printf "  pid %s  %.1f GB  %s\n", $1, $2 / 1048576, substr($0, index($0,$3), 100) }
    '
}

cd "$ROOT"

CHECK_ONLY=0
if [[ "${1:-}" == "--check" ]]; then CHECK_ONLY=1; fi

echo "==> stopping Gradle and Kotlin daemons"
./gradlew --stop >/dev/null 2>&1 || true
for pattern in '[o]rg.gradle.launcher.daemon' '[K]otlinCompileDaemon' '[o]rg.jetbrains.kotlin.daemon'; do
    for pid in $(pgrep -f "$pattern" 2>/dev/null || true); do
        kill "$pid" 2>/dev/null || true
    done
done
sleep 2

available="$(available_mb)"
if (( available < PREFLIGHT_MIN_MB )); then
    echo "==> refusing to build: only ${available} MB available (need ${PREFLIGHT_MIN_MB} MB)"
    echo "    close Zen/Firefox, desktop Baki, or other heavy apps and retry"
    exit 2
fi

stray="$(big_javas || true)"
if [[ -n "$stray" ]]; then
    echo "==> refusing to build: a Java process over 1 GB is still running"
    echo "$stray"
    echo "    close it or stop it before building; it is outside the R8 cage"
    exit 2
fi

if (( CHECK_ONLY != 0 )); then
    echo "==> preflight ok: ${available} MB available, no stray Java over 1 GB"
    exit 0
fi

if systemctl --user is-active --quiet "$UNIT"; then
    echo "==> refusing to start: $UNIT is already active"
    exit 2
fi
systemctl --user reset-failed "$UNIT" >/dev/null 2>&1 || true

rm -f "$LOG" "$UNIT_LOG"

RUNNER="$(mktemp /tmp/baki-r8-run.XXXXXX.sh)"
trap 'rm -f "$RUNNER"' EXIT
cat > "$RUNNER" <<RUNNER_EOF
#!/usr/bin/env bash
set -euo pipefail
cd "$ROOT"
export JAVA_HOME="$TOOLS/jdk21"
export PATH="\$JAVA_HOME/bin:\$PATH"
export GRADLE_USER_HOME="$TOOLS/gradle"
export ANDROID_HOME="$TOOLS/sdk"
exec ./gradlew :composeApp:assembleRelease \
  -PbakiMinify=true \
  -Pandroid.enableR8.fullMode=false \
  -Pandroid.r8.maxWorkers=1 \
  --offline --no-daemon --max-workers=1 \
  -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease \
  > "$LOG" 2>&1
RUNNER_EOF
chmod +x "$RUNNER"

echo "==> host available: ${available} MB"
echo "==> unit: ${UNIT}"
echo "==> R8 service: max=${MEM_MAX}, high=${MEM_HIGH}, swap=${SWAP_MAX}"
echo "==> log: ${LOG}"

set +e
systemd-run --user \
    --unit="$UNIT" \
    --service-type=exec \
    --property=WorkingDirectory="$ROOT" \
    --property=MemoryMax="$MEM_MAX" \
    --property=MemoryHigh="$MEM_HIGH" \
    --property=MemorySwapMax="$SWAP_MAX" \
    --property=KillMode=control-group \
    --property=TimeoutStopSec=20 \
    -- "$RUNNER" >"$UNIT_LOG" 2>&1
start_status=$?
set -e
if (( start_status != 0 )); then
    echo "==> could not start $UNIT"
    cat "$UNIT_LOG"
    exit "$start_status"
fi

killed_for_memory=0
while systemctl --user is-active --quiet "$UNIT"; do
    available="$(available_mb)"
    if (( available < HOST_MIN_MB )); then
        echo "==> host below ${HOST_MIN_MB} MB available; stopping R8 before the kernel OOMs"
        systemctl --user stop "$UNIT" >/dev/null 2>&1 || true
        killed_for_memory=1
        break
    fi
    sleep 2
done

result="$(systemctl --user show "$UNIT" -p Result --value 2>/dev/null || true)"
exec_status="$(systemctl --user show "$UNIT" -p ExecMainStatus --value 2>/dev/null || true)"
peak="$(systemctl --user show "$UNIT" -p MemoryPeak --value 2>/dev/null || true)"
echo "==> service result: ${result:-unknown}, exec status: ${exec_status:-unknown}, peak: ${peak:-unknown} B"
tail -20 "$LOG" 2>/dev/null || true

if (( killed_for_memory != 0 )); then
    exit 124
fi
if [[ "$exec_status" =~ ^[0-9]+$ ]] && (( exec_status != 0 )); then
    exit "$exec_status"
fi
exit 0
