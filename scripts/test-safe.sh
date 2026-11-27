#!/usr/bin/env bash
# Run Gradle tests in a persistent transient service cage.
# The scope wrapper is not enough: a test worker must stay in a unit that systemd owns.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${BAKI_TOOLS:-$ROOT/../.tools}"
UNIT="baki-test-$(date +%s)"
LOG="/tmp/baki-test-$UNIT.log"
STATUS="/tmp/baki-test-$UNIT.status"
MEM_MAX="${BAKI_TEST_MEMORY_MAX:-6G}"
MEM_HIGH="${BAKI_TEST_MEMORY_HIGH:-5G}"
SWAP_MAX="${BAKI_TEST_SWAP_MAX:-2G}"
HOST_MIN_MB="${BAKI_TEST_HOST_MIN_MB:-1536}"

available_mb() { awk '/^MemAvailable:/ {print int($2/1024)}' /proc/meminfo; }
java_rows() { ps -C java -o pid=,rss=,args= 2>/dev/null || true; }

cd "$ROOT"

echo "==> stopping Gradle/Kotlin daemons"
./gradlew --stop >/dev/null 2>&1 || true
for pid in $(pgrep -f '[o]rg.gradle.launcher.daemon|[K]otlinCompileDaemon|[o]rg.jetbrains.kotlin.daemon' 2>/dev/null || true); do
  kill "$pid" 2>/dev/null || true
done
sleep 2

# Refuse if a large JVM survived outside the unit; the service cage cannot protect it.
stray="$(java_rows | awk -v limit=$((1024*1024)) '$2 >= limit { printf "  pid %s  %.1f GB  %s\n", $1, $2/1048576, substr($0,index($0,$3),120) }')"
if [[ -n "$stray" ]]; then
  echo "==> refusing to start: a Java process over 1 GB is still running" >&2
  echo "$stray" >&2
  exit 2
fi

avail="$(available_mb)"
if (( avail < 4096 )); then
  echo "==> refusing to start: only ${avail} MB available; need 4096 MB" >&2
  exit 2
fi

RUNNER="$(mktemp /tmp/baki-test-run.XXXXXX.sh)"
trap 'rm -f "$RUNNER"' EXIT
rm -f "$STATUS"
cat > "$RUNNER" <<RUNNER_EOF
#!/usr/bin/env bash
set -euo pipefail
cd "$ROOT"
export JAVA_HOME="$TOOLS/jdk21"
export PATH="\$JAVA_HOME/bin:\$PATH"
export GRADLE_USER_HOME="$TOOLS/gradle"
export ANDROID_HOME="$TOOLS/sdk"
./gradlew --no-daemon "\$@" > "$LOG" 2>&1
status=\$?
printf '%s' "\$status" > "$STATUS"
exit "\$status"
RUNNER_EOF
chmod +x "$RUNNER"

echo "==> test service: $UNIT max=$MEM_MAX high=$MEM_HIGH swap=$SWAP_MAX"
echo "==> host available: ${avail} MB"
systemd-run --user --unit="$UNIT" --service-type=exec --collect \
  --property=WorkingDirectory="$ROOT" \
  --property=MemoryMax="$MEM_MAX" \
  --property=MemoryHigh="$MEM_HIGH" \
  --property=MemorySwapMax="$SWAP_MAX" \
  --property=KillMode=control-group \
  --property=TimeoutStopSec=30 \
  -- "$RUNNER" "$@" >/tmp/baki-test-unit.log 2>&1

killed_for_memory=0
while systemctl --user is-active --quiet "$UNIT"; do
  avail="$(available_mb)"
  if (( avail < HOST_MIN_MB )); then
    echo "==> host below ${HOST_MIN_MB} MB; stopping the test unit" >&2
    systemctl --user stop "$UNIT" >/dev/null 2>&1 || true
    killed_for_memory=1
    break
  fi
  sleep 2
done

exec_status="$(cat "$STATUS" 2>/dev/null || true)"
peak="$(systemctl --user show "$UNIT" -p MemoryPeak --value 2>/dev/null || true)"
echo "==> test service result: ${exec_status:-unknown}, peak: ${peak:-unknown} bytes"
tail -30 "$LOG" 2>/dev/null || true

if (( killed_for_memory != 0 )); then exit 124; fi
if [[ "$exec_status" =~ ^[0-9]+$ ]] && (( exec_status != 0 )); then exit "$exec_status"; fi
exit 0
